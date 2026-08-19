"""Inventory: commands only. Qty lives on Spring stock/balance."""
import sys
import uuid
from pathlib import Path

import streamlit as st

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import client  # noqa: E402

st.set_page_config(page_title="Inventory", layout="wide")
st.title("Inventory")
st.caption("No local qty. Purchase is the only stock POST. Ledger/adjustment have no HTTP routes.")

if "inv_created_items" not in st.session_state:
	st.session_state["inv_created_items"] = []
if "inv_created_recipes" not in st.session_state:
	st.session_state["inv_created_recipes"] = []

staff = client.staff_token()
st.sidebar.write("staff JWT:", "in" if staff else "out — login on 1_Onboard_Auth")
outlet_id = st.sidebar.text_input("outletId", key="inv_outlet_id")
item_id = st.sidebar.text_input("inventoryItemId", key="inv_item_id")


def show(r):
	st.write("status", r.status_code)
	try:
		st.json(r.json())
	except Exception:
		st.code(r.text or "(empty)")


def remember_item(body):
	if not isinstance(body, dict) or not body.get("id"):
		return
	rows = st.session_state["inv_created_items"]
	if any(r.get("id") == body["id"] for r in rows):
		return
	rows.append({"id": body["id"], "name": body.get("name")})


st.subheader("POST /api/v1/outlets/{outletId}/inventory-items")
with st.form("inv_create_item"):
	name = st.text_input("name")
	unit = st.text_input("unit", value="g")
	if st.form_submit_button("create item") and outlet_id:
		r = client.request("POST", f"/api/v1/outlets/{outlet_id}/inventory-items", json={"name": name, "unit": unit or "g"})
		show(r)
		if r.is_success:
			try:
				remember_item(r.json())
			except Exception:
				pass

st.write("IDs from create responses (API has no item list GET)")
st.dataframe(st.session_state["inv_created_items"], use_container_width=True)

st.subheader("GET /api/v1/outlets/{outletId}/menu — variantId for recipes")
if st.button("load menu variants") and outlet_id:
	r = client.request("GET", f"/api/v1/outlets/{outlet_id}/menu", params={"qr": "false"})
	show(r)

st.subheader("POST /api/v1/recipes")
st.caption("Each POST inserts a new recipe_versions row + one recipe_lines row. No list/GET.")
with st.form("inv_create_recipe"):
	variant_id = st.text_input("variantId")
	recipe_item = st.text_input("inventoryItemId", value=item_id)
	recipe_qty = st.text_input("qty", value="100.0000")
	if st.form_submit_button("create recipe version") and variant_id and recipe_item:
		r = client.request(
			"POST",
			"/api/v1/recipes",
			json={"variantId": variant_id, "inventoryItemId": recipe_item, "qty": recipe_qty},
		)
		show(r)
		if r.is_success:
			try:
				body = r.json()
				st.session_state["inv_created_recipes"].append(
					{"recipeVersionId": body.get("recipeVersionId"), "variantId": variant_id, "inventoryItemId": recipe_item}
				)
			except Exception:
				pass
st.dataframe(st.session_state["inv_created_recipes"], use_container_width=True)

st.subheader("POST /api/v1/stock/purchase")
st.caption("Idempotency-Key required. Server writes PURCHASE tx + balance. Do not add qty in session.")
with st.form("inv_purchase"):
	p_outlet = st.text_input("outletId", value=outlet_id)
	p_item = st.text_input("inventoryItemId", value=item_id)
	p_qty = st.text_input("qty", value="1000.0000")
	idem = st.text_input("Idempotency-Key", value=str(uuid.uuid4()))
	if st.form_submit_button("purchase") and p_outlet and p_item:
		r = client.request(
			"POST",
			"/api/v1/stock/purchase",
			json={"outletId": p_outlet, "inventoryItemId": p_item, "qty": p_qty},
			idempotency_key=idem,
		)
		show(r)

st.subheader("GET /api/v1/stock/balance")
with st.form("inv_balance"):
	b_outlet = st.text_input("outletId", value=outlet_id, key="bal_outlet")
	b_item = st.text_input("inventoryItemId", value=item_id, key="bal_item")
	if st.form_submit_button("fetch balance") and b_outlet and b_item:
		r = client.request(
			"GET",
			"/api/v1/stock/balance",
			params={"outletId": b_outlet, "inventoryItemId": b_item},
		)
		show(r)

st.subheader("Ledger")
st.warning("InventoryFacade.ledger exists in Java. No GetMapping. Cannot list stock_transactions.")

st.subheader("Adjustment")
st.warning("No /stock/adjustment (or similar) mapping. SALE/VOID_REVERSAL are order-side only.")

st.subheader("GET /api/v1/me")
if st.button("whoami"):
	show(client.request("GET", "/api/v1/me"))
