"""Billing/pay wrapper. Tax/totals come from Java invoice JSON only."""
import sys
import uuid
from pathlib import Path

import streamlit as st

_root = Path(__file__).resolve().parent.parent
if str(_root) not in sys.path:
	sys.path.insert(0, str(_root))
import client  # noqa: E402

st.set_page_config(page_title="Billing", layout="wide")
st.title("Billing + payments")
st.caption(
	"Invoice is generated inside POST request-bill (no GET /invoices). "
	"Amounts are API fields. Do not recompute tax. Split pay = multiple POST /payments."
)

ORDER_STATES = "DRAFT CONFIRMED KOT_SENT PREPARING READY BILL_REQUESTED BILLED PAID COMPLETED CANCELLED VOIDED"
PAY_METHODS = ["CASH", "UPI", "CARD"]
# Java PaymentEntity.status defaults SUCCESS; no GET /payments list exists
PAY_STATES_DOC = "SUCCESS (record), invoice OPEN→PAID (markPaid, not returned on GET), order BILLED→PAID→COMPLETED"


def _parse(resp):
	try:
		body = resp.json()
	except Exception:
		body = resp.text
	return {"http": resp.status_code, "body": body}


def _show(label, payload):
	st.subheader(label)
	if payload is None:
		st.info("no response yet")
		return
	st.write("http", payload.get("http"))
	body = payload.get("body")
	if isinstance(body, (dict, list)):
		st.json(body)
		if isinstance(body, dict):
			st.dataframe([{"field": k, "value": v} for k, v in body.items()], use_container_width=True)
	else:
		st.code(str(body))


def _call(method, path, **kw):
	try:
		return _parse(client.request(method, path, **kw))
	except Exception as e:
		return {"http": None, "body": {"error": str(e)}}


ss = st.session_state
ss.setdefault("bill_invoice_json", None)
ss.setdefault("bill_order_json", None)
ss.setdefault("bill_guest_order_json", None)
ss.setdefault("bill_pay_log", [])
ss.setdefault("bill_unlock_json", None)
ss.setdefault("bill_me", None)

st.sidebar.markdown("**tokens**")
st.sidebar.write("staff", "set" if client.staff_token() else "missing")
st.sidebar.write("guest", "set" if client.guest_token() else "missing")
if st.sidebar.button("GET /me"):
	ss["bill_me"] = _call("GET", "/api/v1/me")
if ss.get("bill_me"):
	st.sidebar.json(ss["bill_me"])

st.sidebar.markdown("**order states (backend)**")
st.sidebar.code(ORDER_STATES)
st.sidebar.markdown("**payment states (backend)**")
st.sidebar.caption(PAY_STATES_DOC)

order_id = st.text_input("orderId", value=str(ss.get("order_id") or ""))
qr_token = st.text_input("QR token (guest path)", value=str(ss.get("qr_token") or ""))
invoice_id = st.text_input(
	"invoiceId",
	value=str(ss.get("invoice_id") or ""),
	help="Filled from last request-bill JSON if present",
)

tab_staff, tab_guest, tab_invoice, tab_pay, tab_states = st.tabs(
	["staff request-bill", "guest request-bill", "invoice JSON", "split payments", "states"]
)

with tab_staff:
	st.write("POST /api/v1/orders/{orderId}/request-bill — generate=true on Java; invoice created in BillingFacade.generate")
	c1, c2 = st.columns(2)
	with c1:
		disc = st.number_input("discountPaise (staff OWNER/MANAGER)", min_value=0, value=0, step=1)
	with c2:
		ikey = st.text_input("Idempotency-Key (staff bill)", value=f"bill-{uuid.uuid4()}")
	if st.button("POST staff request-bill", type="primary"):
		if not order_id.strip():
			st.error("orderId required")
		else:
			body = {"discountPaise": int(disc)}
			out = _call(
				"POST",
				f"/api/v1/orders/{order_id.strip()}/request-bill",
				json=body,
				idempotency_key=ikey,
				token=client.staff_token(),
			)
			ss["bill_invoice_json"] = out
			b = out.get("body") or {}
			if isinstance(b, dict) and b.get("invoiceId"):
				ss["invoice_id"] = str(b["invoiceId"])
			if isinstance(b, dict) and b.get("id"):
				ss["order_id"] = str(b["id"])
	_show("staff request-bill → invoice/order JSON", ss.get("bill_invoice_json"))

	st.divider()
	st.write("POST /api/v1/orders/{orderId}/unlock-add — waiter unfreeze after BILL_REQUESTED")
	ukey = st.text_input("unused (unlock has no Idempotency-Key on Java)", value="", disabled=True)
	if st.button("POST unlock-add"):
		if not order_id.strip():
			st.error("orderId required")
		else:
			ss["bill_unlock_json"] = _call(
				"POST",
				f"/api/v1/orders/{order_id.strip()}/unlock-add",
				token=client.staff_token(),
			)
	_show("unlock-add", ss.get("bill_unlock_json"))

with tab_guest:
	st.write("POST /api/v1/public/qr/{token}/request-bill — guest JWT; discount forced 0")
	gkey = st.text_input("Idempotency-Key (guest bill)", value=f"gbill-{uuid.uuid4()}")
	if st.button("POST guest request-bill"):
		if not qr_token.strip():
			st.error("QR token required")
		else:
			out = _call(
				"POST",
				f"/api/v1/public/qr/{qr_token.strip()}/request-bill",
				json={},
				idempotency_key=gkey,
				token=client.guest_token(),
			)
			ss["bill_invoice_json"] = out
			b = out.get("body") or {}
			if isinstance(b, dict) and b.get("invoiceId"):
				ss["invoice_id"] = str(b["invoiceId"])
			if isinstance(b, dict) and b.get("id"):
				ss["order_id"] = str(b["id"])
	if st.button("GET public guest order"):
		if not qr_token.strip():
			st.error("QR token required")
		else:
			ss["bill_guest_order_json"] = _call(
				"GET",
				f"/api/v1/public/qr/{qr_token.strip()}/order",
				token=client.guest_token(),
			)
	_show("guest request-bill (same invoice JSON store)", ss.get("bill_invoice_json"))
	_show("GET /public/qr/{token}/order", ss.get("bill_guest_order_json"))

with tab_invoice:
	st.write("No GET /invoices/{id}. Show last request-bill JSON + GET /orders/{orderId} amounts as returned.")
	if st.button("GET /orders/{orderId}"):
		oid = (order_id or ss.get("order_id") or "").strip()
		if not oid:
			st.error("orderId required")
		else:
			ss["bill_order_json"] = _call("GET", f"/api/v1/orders/{oid}", token=client.staff_token())
	_show("last request-bill JSON (invoiceId, invoiceTotalPaise from generate)", ss.get("bill_invoice_json"))
	_show("GET order (subtotalPaise, totalPaise, status — tax not on this view)", ss.get("bill_order_json"))
	inv_body = (ss.get("bill_invoice_json") or {}).get("body")
	if isinstance(inv_body, dict):
		st.markdown("**API amount fields (as returned)**")
		keys = [
			"subtotalPaise",
			"totalPaise",
			"invoiceTotalPaise",
			"taxPaise",
			"discountPaise",
			"invoiceId",
			"status",
		]
		st.table([{"field": k, "api": inv_body.get(k)} for k in keys if k in inv_body])

with tab_pay:
	st.write("POST /api/v1/payments — Idempotency-Key required. Body: invoiceId, method, amountPaise (tender).")
	st.write("Cashier/OWNER/MANAGER. Guest forbidden. Split = repeat POST with CASH/UPI/CARD until invoicePaid true.")
	pc1, pc2, pc3 = st.columns(3)
	with pc1:
		method = st.selectbox("method", PAY_METHODS)
	with pc2:
		amt = st.number_input("amountPaise (tender)", min_value=0, value=0, step=1)
	with pc3:
		pkey = st.text_input("Idempotency-Key (pay)", value=f"pay-{uuid.uuid4()}")
	iid = (invoice_id or ss.get("invoice_id") or "").strip()
	st.write("invoiceId in use:", iid or "(empty)")
	if st.button("POST record payment"):
		if not iid:
			st.error("invoiceId required")
		else:
			out = _call(
				"POST",
				"/api/v1/payments",
				json={"invoiceId": iid, "method": method, "amountPaise": int(amt)},
				idempotency_key=pkey,
				token=client.staff_token(),
			)
			ss["bill_pay_log"].append(
				{"method": method, "amountPaise_sent": int(amt), "idempotencyKey": pkey, **out}
			)
	if st.button("clear payment log (local display only)"):
		ss["bill_pay_log"] = []
	st.subheader("payment API responses (states from body.invoicePaid + http)")
	log = ss.get("bill_pay_log") or []
	if log:
		rows = []
		for i, p in enumerate(log):
			b = p.get("body") if isinstance(p.get("body"), dict) else {}
			rows.append(
				{
					"n": i + 1,
					"http": p.get("http"),
					"method_posted": p.get("method"),
					"amountPaise_posted": p.get("amountPaise_sent"),
					"paymentId": b.get("paymentId"),
					"invoicePaid": b.get("invoicePaid"),
					"changePaise": b.get("changePaise"),
					"orderId": b.get("orderId"),
					"error": b.get("error") or b.get("code") or b.get("message"),
				}
			)
		st.dataframe(rows, use_container_width=True)
		st.json(log[-1])
	else:
		st.info("no payments recorded this session")
	st.caption("Java sets PaymentEntity.status=SUCCESS; response has no status field. Replay same Idempotency-Key returns cached 200.")

with tab_states:
	st.write("Refresh order after pay — InvoicePaid listener sets order PAID then COMPLETED.")
	if st.button("refresh GET order (staff)"):
		oid = (order_id or ss.get("order_id") or "").strip()
		if not oid:
			st.error("orderId required")
		else:
			ss["bill_order_json"] = _call("GET", f"/api/v1/orders/{oid}", token=client.staff_token())
	_show("order JSON (status field = payment/order state)", ss.get("bill_order_json"))
	last = (ss.get("bill_pay_log") or [None])[-1]
	_show("last payment JSON", last)
	st.caption(
		"Invoice OPEN→PAID is internal; no GET invoice. Use payment invoicePaid + GET order status."
	)
