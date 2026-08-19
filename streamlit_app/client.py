"""Thin httpx client. Totals/tax/stock stay on Spring."""
import os

import httpx

BASE_URL = os.environ.get("API_BASE_URL", "http://localhost:8080")

# module fallback if Streamlit session is missing
_tokens = {"staff_access": None, "staff_refresh": None, "guest": None}


def _bag():
	try:
		import streamlit as st
		if "_api_tokens" not in st.session_state:
			st.session_state["_api_tokens"] = {
				"staff_access": None,
				"staff_refresh": None,
				"guest": None,
			}
		return st.session_state["_api_tokens"]
	except Exception:
		return _tokens


def set_staff_tokens(access, refresh):
	b = _bag()
	b["staff_access"] = access
	b["staff_refresh"] = refresh


def set_guest_token(access):
	_bag()["guest"] = access


def staff_token():
	return _bag().get("staff_access")


def staff_refresh_token():
	return _bag().get("staff_refresh")


def guest_token():
	return _bag().get("guest")


def auth_header(token=None):
	t = token or staff_token() or guest_token()
	if not t:
		return {}
	return {"Authorization": f"Bearer {t}"}


def request(method, path, json=None, params=None, headers=None, idempotency_key=None, token=None):
	url = BASE_URL.rstrip("/") + path
	h = dict(headers or {})
	effective = token if token is not None else (staff_token() or guest_token())
	if effective:
		h["Authorization"] = f"Bearer {effective}"
	if idempotency_key:
		h["Idempotency-Key"] = idempotency_key
	return httpx.request(method, url, json=json, params=params, headers=h, timeout=15.0)
