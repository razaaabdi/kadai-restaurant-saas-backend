"""Catalog create/read. No local tax/price math except paise/100 display."""
import sys
from pathlib import Path

import streamlit as st

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
	sys.path.insert(0, str(_ROOT))

from client import guest_token, request, staff_token  # noqa: E402

st.set_page_config(page_title="Menu", layout="wide")
st.title("Menu / catalog")
st.caption(
	"API has POST tax/category/item/modifier + GET staff/public menu. "
	"No PUT/PATCH/DELETE, no variant-only create, no modifier groups."
)


def _json(resp):
	try:
		return resp.json()
	except Exception:
		return resp.text


def show(resp):
	st.write(f"HTTP {resp.status_code}")
	data = _json(resp)
	st.json(data)
	rows = data if isinstance(data, list) else None
	if isinstance(data, dict) and isinstance(data.get("list"), list):
		rows = data["list"]
	if not rows:
		return
	view = []
	for row in rows:
		if not isinstance(row, dict):
			view.append({"value": row})
			continue
		out = dict(row)
		for k in ("pricePaise", "extraPaise"):
			if k in out and out[k] is not None:
				try:
					out[f"{k.replace('Paise', 'Rupees')}Display"] = int(out[k]) / 100
				except (TypeError, ValueError):
					pass
		view.append(out)
	st.dataframe(view, use_container_width=True)


def paise_hint(n):
	try:
		p = int(n)
	except (TypeError, ValueError):
		return ""
	return f"{p} paise  |  ₹{p / 100:.2f} (display only)"


if "outlet_id" not in st.session_state:
	st.session_state.outlet_id = ""
if "last_category_id" not in st.session_state:
	st.session_state.last_category_id = ""
if "last_tax_id" not in st.session_state:
	st.session_state.last_tax_id = ""
if "qr_token" not in st.session_state:
	st.session_state.qr_token = ""

st.sidebar.write("staff JWT:", "yes" if staff_token() else "no")
st.sidebar.write("guest JWT:", "yes" if guest_token() else "no")
st.session_state.outlet_id = st.sidebar.text_input("outletId", value=st.session_state.outlet_id)
outlet = st.session_state.outlet_id.strip()

tabs = st.tabs(
	[
		"Staff menu GET",
		"Public QR menu GET",
		"Tax",
		"Category",
		"Item (+ default variant)",
		"Modifier",
	]
)

with tabs[0]:
	st.subheader("GET /api/v1/outlets/{outletId}/menu")
	qr_only = st.checkbox("qr=true (QR channel, skips items with availableOnQr=false)", value=False)
	if st.button("Load staff menu", disabled=not outlet):
		show(request("GET", f"/api/v1/outlets/{outlet}/menu", params={"qr": str(qr_only).lower()}))
	if not outlet:
		st.warning("Set outletId in the sidebar.")

with tabs[1]:
	st.subheader("GET /api/v1/public/qr/{token}/menu")
	st.caption("GET is permitAll. Path token is the table QR token, not the guest JWT.")
	st.session_state.qr_token = st.text_input("QR token", value=st.session_state.qr_token)
	use_guest = st.checkbox("send guest JWT if stored", value=False)
	tok = st.session_state.qr_token.strip()
	if st.button("Load public QR menu", disabled=not tok):
		kw = {"token": guest_token()} if use_guest else {"token": ""}
		show(request("GET", f"/api/v1/public/qr/{tok}/menu", **kw))

with tabs[2]:
	st.subheader("POST /api/v1/tax-codes")
	with st.form("tax_form"):
		code = st.text_input("code", value="GST5")
		rate_bps = st.number_input("rateBps (basis points, not paise)", min_value=0, value=500, step=1)
		if st.form_submit_button("Create tax code"):
			resp = request("POST", "/api/v1/tax-codes", json={"code": code, "rateBps": int(rate_bps)})
			show(resp)
			body = _json(resp)
			if isinstance(body, dict) and body.get("id"):
				st.session_state.last_tax_id = str(body["id"])

with tabs[3]:
	st.subheader("POST /api/v1/outlets/{outletId}/categories")
	with st.form("cat_form"):
		cat_name = st.text_input("name", value="Mains")
		if st.form_submit_button("Create category", disabled=not outlet):
			resp = request("POST", f"/api/v1/outlets/{outlet}/categories", json={"name": cat_name})
			show(resp)
			body = _json(resp)
			if isinstance(body, dict) and body.get("id"):
				st.session_state.last_category_id = str(body["id"])

with tabs[4]:
	st.subheader("POST /api/v1/outlets/{outletId}/items")
	st.caption("Backend creates a Default variant with pricePaise. No separate variant or availability update APIs.")
	with st.form("item_form"):
		category_id = st.text_input("categoryId", value=st.session_state.last_category_id)
		item_name = st.text_input("name", value="Masala Dosa")
		price_paise = st.number_input("pricePaise", min_value=0, value=12000, step=1)
		st.write(paise_hint(price_paise))
		tax_code_id = st.text_input("taxCodeId (optional)", value=st.session_state.last_tax_id)
		available_on_qr = st.checkbox("availableOnQr", value=True)
		if st.form_submit_button("Create item", disabled=not outlet):
			body = {
				"categoryId": category_id.strip(),
				"name": item_name,
				"pricePaise": int(price_paise),
				"availableOnQr": bool(available_on_qr),
			}
			if tax_code_id.strip():
				body["taxCodeId"] = tax_code_id.strip()
			resp = request("POST", f"/api/v1/outlets/{outlet}/items", json=body)
			show(resp)
			created = _json(resp)
			if isinstance(created, dict) and created.get("pricePaise") is not None:
				st.write("created " + paise_hint(created["pricePaise"]))

with tabs[5]:
	st.subheader("POST /api/v1/outlets/{outletId}/modifiers")
	st.caption("Flat modifiers only — no modifier-group endpoints.")
	with st.form("mod_form"):
		mod_name = st.text_input("name", value="Extra ghee")
		extra_paise = st.number_input("extraPaise", min_value=0, value=2000, step=1)
		st.write(paise_hint(extra_paise))
		if st.form_submit_button("Create modifier", disabled=not outlet):
			resp = request(
				"POST",
				f"/api/v1/outlets/{outlet}/modifiers",
				json={"name": mod_name, "extraPaise": int(extra_paise)},
			)
			show(resp)
			created = _json(resp)
			if isinstance(created, dict) and created.get("extraPaise") is not None:
				st.write("created " + paise_hint(created["extraPaise"]))
