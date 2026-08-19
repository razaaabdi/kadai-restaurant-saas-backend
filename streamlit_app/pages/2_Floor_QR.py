"""Floor / QR / table session — calls FloorController only. No totals."""
import json
import sys
from pathlib import Path

import streamlit as st

_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
	sys.path.insert(0, str(_ROOT))
import client  # noqa: E402

st.set_page_config(page_title="Floor / QR", layout="wide")
st.title("Floor / QR")
st.caption("No GET list on FloorController. Ledger below is create/rotate responses only.")

if "floor_areas" not in st.session_state:
	st.session_state.floor_areas = []
if "floor_tables" not in st.session_state:
	st.session_state.floor_tables = []


def _show(r):
	st.write("status", r.status_code)
	ct = (r.headers.get("content-type") or "").lower()
	if r.status_code == 204 or not r.content:
		st.write("(empty body)")
		return None
	if "json" in ct or (r.text[:1] in "{["):
		try:
			body = r.json()
			st.json(body)
			return body
		except Exception:
			pass
	st.text(r.text)
	return None


def _remember_area(outlet_id, body):
	if not isinstance(body, dict):
		return
	row = {"outletId": outlet_id, **body}
	st.session_state.floor_areas = [row] + [
		a for a in st.session_state.floor_areas if a.get("id") != body.get("id")
	]


def _remember_table(area_id, body, extra=None):
	if not isinstance(body, dict):
		return
	row = {"areaId": area_id, **body, **(extra or {})}
	tid = body.get("tableId") or body.get("id")
	st.session_state.floor_tables = [row] + [
		t for t in st.session_state.floor_tables if (t.get("tableId") or t.get("id")) != tid
	]


st.sidebar.header("Staff JWT")
st.sidebar.write("staff token in client:", "yes" if client.staff_token() else "no")
paste = st.sidebar.text_input("paste staff access token", type="password")
if st.sidebar.button("use pasted staff token"):
	client.set_staff_tokens(paste.strip() or None, client.staff_refresh_token())

# --- areas / tables ---
c1, c2 = st.columns(2)
with c1:
	st.subheader("Create area")
	with st.form("create_area"):
		outlet_id = st.text_input("outletId")
		area_name = st.text_input("name")
		if st.form_submit_button("POST area"):
			r = client.request(
				"POST",
				f"/api/v1/outlets/{outlet_id.strip()}/areas",
				json={"name": area_name},
			)
			body = _show(r)
			_remember_area(outlet_id.strip(), body)

with c2:
	st.subheader("Create table (issues QR)")
	with st.form("create_table"):
		area_id = st.text_input("areaId")
		code = st.text_input("code")
		seats = st.number_input("seats", min_value=1, value=4, step=1)
		if st.form_submit_button("POST table"):
			r = client.request(
				"POST",
				f"/api/v1/areas/{area_id.strip()}/tables",
				json={"code": code, "seats": int(seats)},
			)
			body = _show(r)
			_remember_table(area_id.strip(), body, extra={"code": code, "seats": int(seats), "qr_locked": False})
			if isinstance(body, dict) and body.get("accessToken") is None:
				st.session_state["last_qr_payload"] = body.get("qrPayload")
				st.session_state["last_qr_token"] = body.get("token")

st.subheader("Session ledger (create responses — API has no list GET)")
st.write("areas")
st.json(st.session_state.floor_areas)
st.write("tables (code/seats/status/qr_locked as last known from responses + commands)")
st.json(st.session_state.floor_tables)

# --- QR staff ---
st.subheader("Issue / show QR, rotate, lock")
st.write("last qrPayload", st.session_state.get("last_qr_payload"))
st.write("last token", st.session_state.get("last_qr_token"))

with st.form("rotate_qr"):
	tid = st.text_input("tableId (rotate)")
	if st.form_submit_button("POST rotate-qr"):
		r = client.request("POST", f"/api/v1/tables/{tid.strip()}/rotate-qr", json={})
		body = _show(r)
		_remember_table(None, body)
		if isinstance(body, dict):
			st.session_state["last_qr_payload"] = body.get("qrPayload")
			st.session_state["last_qr_token"] = body.get("token")

lc, uc = st.columns(2)
with lc:
	with st.form("qr_lock"):
		tid = st.text_input("tableId (lock)")
		if st.form_submit_button("POST qr-lock locked=true"):
			r = client.request(
				"POST",
				f"/api/v1/tables/{tid.strip()}/qr-lock",
				json={"locked": True},
			)
			_show(r)
			for t in st.session_state.floor_tables:
				if t.get("tableId") == tid.strip():
					t["qr_locked"] = True
with uc:
	with st.form("qr_unlock"):
		tid = st.text_input("tableId (unlock)")
		if st.form_submit_button("POST qr-lock locked=false"):
			r = client.request(
				"POST",
				f"/api/v1/tables/{tid.strip()}/qr-lock",
				json={"locked": False},
			)
			_show(r)
			for t in st.session_state.floor_tables:
				if t.get("tableId") == tid.strip():
					t["qr_locked"] = False

# --- occupancy / clear ---
st.subheader("clear_table / occupancy")
st.caption("Occupancy is server-side (FREE / OCCUPIED / BILL_REQUESTED / PAID_DIRTY). occupy() is not a Floor HTTP command — it runs on order. Status is read from GET public QR info.")
with st.form("clear_table"):
	tid = st.text_input("tableId (clear)")
	if st.form_submit_button("POST clear-table"):
		r = client.request("POST", f"/api/v1/tables/{tid.strip()}/clear-table", json={})
		_show(r)

st.subheader("Staff /me")
if st.button("GET /me (staff token)"):
	_show(client.request("GET", "/api/v1/me"))

# --- guest ---
st.subheader("Guest: resolve token + session")
with st.form("guest_one"):
	tok = st.text_input("token")
	do_resolve = st.form_submit_button("GET public QR info")
	do_sess = st.form_submit_button("POST session (store guest JWT)")
	if do_resolve and tok.strip():
		_show(client.request("GET", f"/api/v1/public/qr/{tok.strip()}", token=""))
	if do_sess and tok.strip():
		r = client.request("POST", f"/api/v1/public/qr/{tok.strip()}/sessions", json={}, token="")
		body = _show(r)
		if isinstance(body, dict) and body.get("accessToken"):
			jwt = body["accessToken"]
			client.set_guest_token(jwt)
			st.session_state["guest_jwt"] = jwt
			st.session_state["guest_session"] = body
			st.write("stored session_state.guest_jwt")

st.write("session_state.guest_jwt set:", bool(st.session_state.get("guest_jwt")))
if st.button("GET /me (guest JWT)"):
	_show(client.request("GET", "/api/v1/me", token=st.session_state.get("guest_jwt") or client.guest_token()))

# --- two-token join ---
st.subheader("Two-token join test")
st.caption("Same QR token in A and B = two guests on one table (join). Different tokens = rotate / two tables.")
ja, jb = st.columns(2)
with ja:
	st.text_input("token A", key="join_token_a")
	if st.button("resolve A"):
		t = (st.session_state.get("join_token_a") or "").strip()
		_show(client.request("GET", f"/api/v1/public/qr/{t}", token=""))
	if st.button("session A"):
		t = (st.session_state.get("join_token_a") or "").strip()
		r = client.request("POST", f"/api/v1/public/qr/{t}/sessions", json={}, token="")
		body = _show(r)
		if isinstance(body, dict) and body.get("accessToken"):
			st.session_state["guest_jwt_a"] = body["accessToken"]
			st.session_state["guest_session_a"] = body
			client.set_guest_token(body["accessToken"])
	if st.button("GET /me as A"):
		_show(client.request("GET", "/api/v1/me", token=st.session_state.get("guest_jwt_a")))
	st.write("jwt A", "set" if st.session_state.get("guest_jwt_a") else "empty")
	if st.session_state.get("guest_session_a"):
		st.json(st.session_state["guest_session_a"])
with jb:
	st.text_input("token B", key="join_token_b")
	if st.button("resolve B"):
		t = (st.session_state.get("join_token_b") or "").strip()
		_show(client.request("GET", f"/api/v1/public/qr/{t}", token=""))
	if st.button("session B"):
		t = (st.session_state.get("join_token_b") or "").strip()
		r = client.request("POST", f"/api/v1/public/qr/{t}/sessions", json={}, token="")
		body = _show(r)
		if isinstance(body, dict) and body.get("accessToken"):
			st.session_state["guest_jwt_b"] = body["accessToken"]
			st.session_state["guest_session_b"] = body
	if st.button("GET /me as B"):
		_show(client.request("GET", "/api/v1/me", token=st.session_state.get("guest_jwt_b")))
	st.write("jwt B", "set" if st.session_state.get("guest_jwt_b") else "empty")
	if st.session_state.get("guest_session_b"):
		st.json(st.session_state["guest_session_b"])

st.write("sessionIds A vs B (raw, no join math)")
st.code(
	json.dumps(
		{
			"A": (st.session_state.get("guest_session_a") or {}).get("sessionId"),
			"B": (st.session_state.get("guest_session_b") or {}).get("sessionId"),
			"tableIdA": (st.session_state.get("guest_session_a") or {}).get("tableId"),
			"tableIdB": (st.session_state.get("guest_session_b") or {}).get("tableId"),
		},
		indent=2,
		default=str,
	)
)
