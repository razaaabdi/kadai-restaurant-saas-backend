"""Staff reports: Java reporting GET payload only. No Python math."""
import json
import sys
from datetime import date
from pathlib import Path

import streamlit as st

_root = Path(__file__).resolve().parent.parent
if str(_root) not in sys.path:
	sys.path.insert(0, str(_root))
import client  # noqa: E402

st.set_page_config(page_title="Reports", layout="wide")
st.title("Reports")
st.caption("Reads `outlet_daily_sales` via GET. No `/dashboard` route on Java. Drain outbox if the worker has not run.")


def _parse_body(resp):
	try:
		return resp.json()
	except Exception:
		return {"_status": resp.status_code, "_text": resp.text}


def _as_rows(payload):
	# display JSON as-is; wrap dict as one row / list as rows
	if isinstance(payload, list):
		return payload
	if isinstance(payload, dict):
		return [payload]
	return [{"value": payload}]


def _kv_rows(payload):
	if isinstance(payload, dict):
		return [{"field": k, "value": v} for k, v in payload.items()]
	return [{"field": "_", "value": payload}]


default_outlet = st.session_state.get("outlet_id") or ""

c1, c2, c3 = st.columns(3)
with c1:
	outlet_id = st.text_input("outletId (path)", value=str(default_outlet))
with c2:
	biz_date = st.date_input("date (query)", value=date.today())
with c3:
	st.write("")
	drain = st.button("POST /outbox/drain")

if drain:
	try:
		r = client.request("POST", "/api/v1/outbox/drain", json={})
		st.session_state["_drain"] = {"status": r.status_code, "body": _parse_body(r)}
	except Exception as e:
		st.session_state["_drain"] = {"error": str(e)}

if "_drain" in st.session_state:
	st.subheader("outbox drain")
	st.dataframe(_as_rows(st.session_state["_drain"].get("body", st.session_state["_drain"])), use_container_width=True)

fetch = st.button("GET daily-sales", type="primary")
if fetch:
	if not outlet_id.strip():
		st.error("outletId required by API")
	else:
		path = f"/api/v1/outlets/{outlet_id.strip()}/daily-sales"
		params = {"date": biz_date.isoformat()}
		try:
			r = client.request("GET", path, params=params)
			st.session_state["_sales"] = {
				"path": path,
				"params": params,
				"status": r.status_code,
				"body": _parse_body(r),
			}
		except Exception as e:
			st.session_state["_sales"] = {"error": str(e)}

sales = st.session_state.get("_sales")
if not sales:
	st.info("Set outlet + date, then GET. Empty row from API is `{ordersCount:0,gmvPaise:0}`.")
	st.stop()

if "error" in sales:
	st.error(sales["error"])
	st.stop()

st.write(f"`GET {sales.get('path')}?date={sales.get('params', {}).get('date')}` → {sales.get('status')}")
body = sales.get("body")

st.subheader("outlet_daily_sales (GET payload as row)")
st.dataframe(_as_rows(body), use_container_width=True)

st.subheader("dashboard (same payload, field/value — no extra endpoint)")
st.dataframe(_kv_rows(body), use_container_width=True)

with st.expander("raw JSON"):
	st.code(json.dumps(body, default=str, indent=2))
