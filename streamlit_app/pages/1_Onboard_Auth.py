"""Onboard + auth against Spring `/api/v1/onboarding` and `/api/v1/auth/**`. No local math."""
from __future__ import annotations

import base64
import json
import os
import sys
from pathlib import Path

import streamlit as st

_HERE = Path(__file__).resolve()
_APP = _HERE.parents[1]
_REPO = _HERE.parents[2]
for _p in (str(_REPO), str(_APP)):
	if _p not in sys.path:
		sys.path.insert(0, _p)
try:
	from streamlit_app import client
except ImportError:
	import client  # Streamlit runs with streamlit_app on path

try:
	st.set_page_config(page_title="Onboard / Auth", layout="wide")
except Exception:
	pass

st.markdown(
	"<style>*,*::before,*::after{animation-duration:.01ms!important;animation-delay:0s!important;"
	"transition-duration:.01ms!important}</style>",
	unsafe_allow_html=True,
)

# --- Java bodies (no DTO classes; Map fields from controllers/services) ---
# POST /api/v1/onboarding  {name, slug, email, password, ownerName?}
# POST /api/v1/auth/login  {email, password}
# POST /api/v1/auth/refresh  {refreshToken}
# POST /api/v1/auth/logout  {refreshToken} -> 204
# POST /api/v1/auth/password-reset/request  {email} -> status, optional devToken
# POST /api/v1/auth/password-reset/confirm  {token, newPassword} -> 204
# login/refresh JSON: accessToken, refreshToken, tenantId, userId, roles, outletIds
# staff JWT claims: sub, typ, tenant_id, outlet_ids, roles, iat, exp


def _sidebar_cfg():
	st.sidebar.header("API")
	default_base = os.environ.get("API_BASE_URL", client.BASE_URL or "http://localhost:8080")
	base = st.sidebar.text_input("API_BASE_URL", value=default_base, key="onboard_api_base")
	client.BASE_URL = (base or default_base).rstrip("/")
	st.sidebar.caption(f"client.BASE_URL = `{client.BASE_URL}`")
	pasted = st.sidebar.text_area(
		"Paste access token (fallback)",
		value="",
		height=80,
		key="onboard_paste_access",
		help="Overrides staff access token in session; refresh token unchanged.",
	)
	if st.sidebar.button("Use pasted access token"):
		tok = (pasted or "").strip()
		if tok:
			client.set_staff_tokens(tok, client.staff_refresh_token())
			st.sidebar.success("staff access set from paste")
		else:
			st.sidebar.warning("empty paste")
	st.sidebar.write("staff access:", "set" if client.staff_token() else "none")
	st.sidebar.write("staff refresh:", "set" if client.staff_refresh_token() else "none")


def _b64url_json(seg: str):
	pad = "=" * (-len(seg) % 4)
	return json.loads(base64.urlsafe_b64decode(seg + pad).decode("utf-8"))


def decode_jwt(token: str | None) -> dict:
	if not token:
		return {}
	parts = token.split(".")
	out = {"raw_parts": len(parts)}
	try:
		if len(parts) >= 1:
			out["header"] = _b64url_json(parts[0])
		if len(parts) >= 2:
			out["payload"] = _b64url_json(parts[1])
	except Exception as e:
		out["decode_error"] = str(e)
	return out


def as_rows(obj) -> list[dict]:
	if obj is None:
		return [{"key": "(empty)", "value": ""}]
	if isinstance(obj, dict):
		return [{"key": str(k), "value": json.dumps(v, default=str)} for k, v in obj.items()]
	if isinstance(obj, list):
		return [{"key": str(i), "value": json.dumps(v, default=str)} for i, v in enumerate(obj)]
	return [{"key": "value", "value": json.dumps(obj, default=str)}]


def show_raw(title: str, obj, status: int | None = None):
	st.markdown(f"**{title}**" + (f" — HTTP {status}" if status is not None else ""))
	st.table(as_rows(obj))
	st.json(obj if obj is not None else {})


def call(method: str, path: str, body=None, token=None):
	entry = {"method": method, "path": path, "request": body, "status": None, "body": None}
	try:
		r = client.request(method, path, json=body, token=token)
	except Exception as e:
		entry["body"] = {"error": str(e)}
		st.session_state["_last_http"] = entry
		show_raw(f"{method} {path} transport error", entry["body"])
		return None, None
	parsed = None
	if r.content:
		try:
			parsed = r.json()
		except Exception:
			parsed = {"_non_json": r.text}
	entry["status"] = r.status_code
	entry["body"] = parsed
	st.session_state["_last_http"] = entry
	hist = st.session_state.setdefault("_http_hist", [])
	hist.append(entry)
	st.session_state["_http_hist"] = hist[-12:]
	show_raw(f"{method} {path}", parsed, r.status_code)
	return r.status_code, parsed


def persist_auth(parsed: dict | None):
	if not isinstance(parsed, dict):
		return
	access = parsed.get("accessToken")
	refresh = parsed.get("refreshToken")
	if access or refresh:
		client.set_staff_tokens(access or client.staff_token(), refresh or client.staff_refresh_token())


_sidebar_cfg()
st.title("Onboard / Auth")
st.caption("UI wrapper only. Bodies match Java Map fields, not invented DTO names.")

access = client.staff_token()
refresh = client.staff_refresh_token()
decoded = decode_jwt(access)

st.header("Session / JWT")
show_raw(
	"token bag",
	{
		"accessToken": access,
		"refreshToken": refresh,
		"access_len": len(access) if access else 0,
		"refresh_len": len(refresh) if refresh else 0,
	},
)
if decoded.get("payload"):
	pl = decoded["payload"]
	show_raw(
		"JWT claims (decoded, not verified)",
		{
			"sub": pl.get("sub"),
			"typ": pl.get("typ"),
			"tenant_id": pl.get("tenant_id"),
			"roles": pl.get("roles"),
			"outlet_ids": pl.get("outlet_ids"),
			"iat": pl.get("iat"),
			"exp": pl.get("exp"),
		},
	)
	show_raw("JWT header", decoded.get("header") or {})
	show_raw("JWT full payload", pl)
else:
	st.write("No access token to decode.")

if st.session_state.get("_http_hist"):
	st.header("Last HTTP (raw)")
	for i, h in enumerate(reversed(st.session_state["_http_hist"])):
		show_raw(
			f"[{i}] {h.get('method')} {h.get('path')} request",
			h.get("request"),
		)
		show_raw(
			f"[{i}] {h.get('method')} {h.get('path')} response",
			h.get("body"),
			h.get("status"),
		)

c1, c2 = st.columns(2)

with c1:
	st.header("POST /api/v1/onboarding")
	st.caption("OnboardingController: name, slug, email, password, ownerName (default Owner).")
	with st.form("onboard"):
		name = st.text_input("name", value="")
		slug = st.text_input("slug", value="")
		email = st.text_input("email", value="", key="onb_email")
		password = st.text_input("password", type="password", key="onb_password")
		owner_name = st.text_input("ownerName", value="Owner")
		if st.form_submit_button("Onboard"):
			body = {
				"name": name,
				"slug": slug,
				"email": email,
				"password": password,
				"ownerName": owner_name,
			}
			st.session_state["_last_onboard_req"] = body
			code, parsed = call("POST", "/api/v1/onboarding", body, token="")
			st.session_state["_last_onboard"] = {"status": code, "body": parsed}

	if "_last_onboard_req" in st.session_state:
		show_raw("last onboard request", st.session_state["_last_onboard_req"])

	st.header("POST /api/v1/auth/login")
	with st.form("login"):
		le = st.text_input("email", key="login_email")
		lp = st.text_input("password", type="password", key="login_password")
		if st.form_submit_button("Login"):
			code, parsed = call("POST", "/api/v1/auth/login", {"email": le, "password": lp}, token="")
			persist_auth(parsed)
			st.session_state["_last_login"] = parsed
			if isinstance(parsed, dict):
				show_raw(
					"login tenant / roles (response)",
					{
						"tenantId": parsed.get("tenantId"),
						"userId": parsed.get("userId"),
						"roles": parsed.get("roles"),
						"outletIds": parsed.get("outletIds"),
					},
					code,
				)
			if isinstance(parsed, dict) and parsed.get("accessToken"):
				st.rerun()

	st.header("POST /api/v1/auth/refresh")
	with st.form("refresh"):
		rt = st.text_input("refreshToken", value=refresh or "", key="refresh_in")
		if st.form_submit_button("Refresh"):
			code, parsed = call("POST", "/api/v1/auth/refresh", {"refreshToken": rt}, token="")
			persist_auth(parsed)
			if isinstance(parsed, dict):
				show_raw(
					"refresh tenant / roles (response)",
					{
						"tenantId": parsed.get("tenantId"),
						"userId": parsed.get("userId"),
						"roles": parsed.get("roles"),
						"outletIds": parsed.get("outletIds"),
					},
					code,
				)
			if isinstance(parsed, dict) and parsed.get("accessToken"):
				st.rerun()

	st.header("POST /api/v1/auth/logout")
	with st.form("logout"):
		lt = st.text_input("refreshToken", value=refresh or "", key="logout_in")
		if st.form_submit_button("Logout"):
			call("POST", "/api/v1/auth/logout", {"refreshToken": lt}, token="")
			client.set_staff_tokens(None, None)
			st.rerun()

with c2:
	st.header("Password reset")
	st.caption("AuthController: /password-reset/request and /confirm exist.")
	with st.form("reset_req"):
		re = st.text_input("email", key="reset_email")
		if st.form_submit_button("POST /api/v1/auth/password-reset/request"):
			code, parsed = call("POST", "/api/v1/auth/password-reset/request", {"email": re}, token="")
			if isinstance(parsed, dict) and parsed.get("devToken"):
				st.session_state["_dev_reset_token"] = parsed.get("devToken")
				st.warning("devToken returned (non-prod). Paste into confirm.")
	with st.form("reset_confirm"):
		tok = st.text_input("token", value=st.session_state.get("_dev_reset_token") or "", key="reset_token")
		npw = st.text_input("newPassword", type="password", key="reset_new")
		if st.form_submit_button("POST /api/v1/auth/password-reset/confirm"):
			call("POST", "/api/v1/auth/password-reset/confirm", {"token": tok, "newPassword": npw}, token="")

	st.header("Endpoints used")
	st.table(
		[
			{"method": "POST", "path": "/api/v1/onboarding", "auth": "permitAll"},
			{"method": "POST", "path": "/api/v1/auth/login", "auth": "permitAll"},
			{"method": "POST", "path": "/api/v1/auth/refresh", "auth": "permitAll"},
			{"method": "POST", "path": "/api/v1/auth/logout", "auth": "permitAll"},
			{"method": "POST", "path": "/api/v1/auth/password-reset/request", "auth": "permitAll"},
			{"method": "POST", "path": "/api/v1/auth/password-reset/confirm", "auth": "permitAll"},
		]
	)
