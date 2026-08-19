"""Kitchen board: list KOTs, start-prep, mark-ready. No SSE; reprint not in API."""
import sys
from pathlib import Path

import streamlit as st

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
	sys.path.insert(0, str(_ROOT))

import client

STATUSES = ("NEW", "PREPARING", "READY")

st.set_page_config(page_title="Kitchen", layout="wide")
st.title("Kitchen / KOT")
st.caption("Staff JWT. List is per order (no outlet-status list route). Reprint not exposed.")

st.sidebar.write("staff:", "in" if client.staff_token() else "out")
st.sidebar.write("base:", client.BASE_URL)

order_id = st.text_input("Order ID", key="kot_order_id")
status_pick = st.selectbox("Status filter", ["ALL", *STATUSES])
cols = st.columns(2)
refresh = cols[0].button("Refresh")
if refresh:
	st.rerun()


def _as_list(body):
	if isinstance(body, list):
		return body
	if isinstance(body, dict) and isinstance(body.get("list"), list):
		return body["list"]
	return []


def _show_http(r):
	st.write(f"HTTP {r.status_code}")
	text = r.text or ""
	if r.status_code == 204 or not text.strip():
		st.json({"empty": True})
		return
	try:
		st.json(r.json())
	except Exception:
		st.code(text[:4000])


def _load_kots(oid):
	r = client.request("GET", f"/api/v1/orders/{oid}/kots")
	if r.status_code != 200:
		_show_http(r)
		return []
	try:
		rows = _as_list(r.json())
	except Exception:
		st.code(r.text[:4000])
		return []
	if status_pick != "ALL":
		rows = [k for k in rows if str(k.get("status")) == status_pick]
	return rows


if not order_id.strip():
	st.info("Enter an order id, then Refresh.")
	st.stop()

kots = _load_kots(order_id.strip())
st.write(f"{len(kots)} KOT(s)")
st.dataframe(kots if kots else [{"_": "none"}], use_container_width=True)

by_status = {s: [k for k in kots if str(k.get("status")) == s] for s in STATUSES}
tabs = st.tabs(list(STATUSES))
for tab, status in zip(tabs, STATUSES):
	with tab:
		bucket = by_status[status]
		st.write(f"{status}: {len(bucket)}")
		for i, kot in enumerate(bucket):
			kid = str(kot.get("id") or "")
			st.json(kot)
			b1, b2, b3 = st.columns(3)
			if status == "NEW" and b1.button("start-prep", key=f"prep-{kid}-{i}", disabled=not kid):
				resp = client.request("POST", f"/api/v1/kots/{kid}/start-prep")
				_show_http(resp)
				if resp.status_code in (200, 204):
					st.rerun()
			if status == "PREPARING" and b2.button("mark-ready", key=f"ready-{kid}-{i}", disabled=not kid):
				resp = client.request("POST", f"/api/v1/kots/{kid}/mark-ready")
				_show_http(resp)
				if resp.status_code in (200, 204):
					st.rerun()
			b3.button("reprint (no API)", key=f"reprint-{kid}-{i}", disabled=True)

st.divider()
st.subheader("Manual action")
man_id = st.text_input("KOT ID")
m1, m2, m3 = st.columns(3)
if m1.button("POST start-prep") and man_id.strip():
	_show_http(client.request("POST", f"/api/v1/kots/{man_id.strip()}/start-prep"))
if m2.button("POST mark-ready") and man_id.strip():
	_show_http(client.request("POST", f"/api/v1/kots/{man_id.strip()}/mark-ready"))
m3.caption("No POST /kots/{id}/reprint in KitchenController (entity reprintOf unused).")
