"""Orders: Java counter + QR guest paths. No bill math here."""
import json
import sys
import uuid
from pathlib import Path

import streamlit as st

_root = Path(__file__).resolve().parent.parent
if str(_root) not in sys.path:
	sys.path.insert(0, str(_root))
import client  # noqa: E402

st.set_page_config(page_title="Orders", layout="wide")
st.title("Orders")
st.caption(
	"No GET list on Java — refresh tracked IDs via GET /orders/{id}. "
	"Staff POST auto-confirms DRAFT→CONFIRMED→KOT_SENT. Guest first round may stay DRAFT if outlet qrAutoConfirm is off. "
	"Bill endpoints exist; this page does not call them."
)

STATUSES = [
	"DRAFT",
	"CONFIRMED",
	"KOT_SENT",
	"PREPARING",
	"READY",
	"BILL_REQUESTED",
	"BILLED",
	"PAID",
	"COMPLETED",
	"CANCELLED",
	"VOIDED",
]


def _parse(resp):
	try:
		return resp.json()
	except Exception:
		return {"_status": resp.status_code, "_text": resp.text}


def _version_from(resp, body):
	if isinstance(body, dict):
		for k in ("version", "optimisticVersion", "versionNo"):
			if k in body and body[k] is not None:
				return f"{k}={body[k]}"
	etag = resp.headers.get("ETag") or resp.headers.get("etag")
	if etag:
		return f"ETag={etag}"
	im = resp.headers.get("If-Match")
	if im:
		return f"If-Match={im}"
	return None


def _remember_order(body):
	if not isinstance(body, dict):
		return
	oid = body.get("id")
	if not oid:
		return
	ids = st.session_state.setdefault("order_ids", [])
	s = str(oid)
	if s not in ids:
		ids.append(s)
	st.session_state["active_order_id"] = s
	if body.get("tableId"):
		st.session_state["table_id"] = str(body["tableId"])
	if body.get("outletId"):
		st.session_state["outlet_id"] = str(body["outletId"])


def _call(method, path, *, json_body=None, params=None, idem=None, token=None, remember=True):
	r = client.request(
		method,
		path,
		json=json_body,
		params=params,
		idempotency_key=idem or None,
		token=token,
	)
	body = _parse(r)
	rec = {
		"method": method,
		"path": path,
		"status": r.status_code,
		"body": body,
		"version": _version_from(r, body),
		"idem": idem,
	}
	st.session_state["last_order_call"] = rec
	if remember and r.status_code < 400:
		_remember_order(body)
		st.session_state["order_snapshot"] = body
	return rec


def _items_payload(variant_id, qty, modifier_csv):
	item = {"variantId": variant_id.strip(), "qty": str(qty).strip() or "1"}
	mods = [m.strip() for m in (modifier_csv or "").split(",") if m.strip()]
	if mods:
		item["modifierIds"] = mods
	return {"items": [item]}


def _show_last():
	rec = st.session_state.get("last_order_call")
	if not rec:
		return
	st.write(f"`{rec['method']} {rec['path']}` → {rec['status']}")
	if rec.get("idem"):
		st.caption(f"Idempotency-Key: `{rec['idem']}`")
	ver = rec.get("version")
	if ver:
		st.info(f"optimistic version from API: {ver}")
	else:
		st.caption("API did not return version / ETag (OrderEntity has none).")
	st.json(rec.get("body"))


if "order_ids" not in st.session_state:
	st.session_state["order_ids"] = []

st.sidebar.header("Context")
outlet_id = st.sidebar.text_input("outletId", value=str(st.session_state.get("outlet_id") or ""))
table_id = st.sidebar.text_input("tableId (assignment)", value=str(st.session_state.get("table_id") or ""))
qr_token = st.sidebar.text_input("QR token", value=str(st.session_state.get("qr_token") or ""))
order_id = st.sidebar.text_input(
	"orderId",
	value=str(st.session_state.get("active_order_id") or ""),
)
channel = st.sidebar.selectbox("channel (counter)", ["COUNTER_DINE_IN", "TAKEAWAY", "QR_DINE_IN"])
idem_in = st.sidebar.text_input("Idempotency-Key", value=str(st.session_state.get("order_idem") or ""))
if st.sidebar.button("new idempotency key"):
	idem_in = str(uuid.uuid4())
	st.session_state["order_idem"] = idem_in
	st.rerun()
st.session_state["order_idem"] = idem_in
st.session_state["outlet_id"] = outlet_id.strip()
st.session_state["table_id"] = table_id.strip()
st.session_state["qr_token"] = qr_token.strip()
if order_id.strip():
	st.session_state["active_order_id"] = order_id.strip()

st.sidebar.write("staff:", "in" if client.staff_token() else "out")
st.sidebar.write("guest:", "in" if client.guest_token() else "out")

tab_list, tab_counter, tab_guest, tab_status, tab_snap = st.tabs(
	["List", "Counter draft/confirm + round", "QR guest", "Cancel / illegal status", "Snapshots JSON"]
)

with tab_list:
	st.write("Java has no collection GET. Track IDs from creates, then GET each.")
	extra = st.text_input("add orderId to track")
	c1, c2, c3 = st.columns(3)
	with c1:
		if st.button("track id"):
			e = extra.strip()
			if e and e not in st.session_state["order_ids"]:
				st.session_state["order_ids"].append(e)
	with c2:
		refresh = st.button("GET tracked orders", type="primary")
	with c3:
		if st.button("clear tracked"):
			st.session_state["order_ids"] = []

	rows = []
	if refresh:
		for oid in list(st.session_state["order_ids"]):
			rec = _call("GET", f"/api/v1/orders/{oid}", remember=True)
			body = rec["body"] if isinstance(rec["body"], dict) else {}
			rows.append(
				{
					"http": rec["status"],
					"id": body.get("id", oid),
					"status": body.get("status"),
					"channel": body.get("channel"),
					"tableId": body.get("tableId"),
					"outletId": body.get("outletId"),
					"rounds": body.get("rounds"),
					"guestFrozen": body.get("guestFrozen"),
					"subtotalPaise": body.get("subtotalPaise"),
					"totalPaise": body.get("totalPaise"),
					"version": rec.get("version"),
				}
			)
		st.session_state["order_list_rows"] = rows

	data = st.session_state.get("order_list_rows") or []
	if data:
		st.dataframe(data, use_container_width=True)
	else:
		st.info(f"tracked: {st.session_state['order_ids'] or 'none'}")

	if st.button("GET /orders/{orderId} (sidebar id)"):
		oid = (st.session_state.get("active_order_id") or "").strip()
		if not oid:
			st.error("orderId required")
		else:
			_call("GET", f"/api/v1/orders/{oid}")
			_show_last()

with tab_counter:
	st.write("POST `/api/v1/outlets/{outletId}/orders` — same route creates DRAFT and adds a round (staff auto-confirm).")
	if st.button("GET staff menu"):
		oid = outlet_id.strip()
		if not oid:
			st.error("outletId required")
		else:
			_call("GET", f"/api/v1/outlets/{oid}/menu", params={"qr": "false"}, remember=False)
	menu_rec = st.session_state.get("last_order_call")
	if menu_rec and menu_rec.get("path", "").endswith("/menu"):
		st.json(menu_rec.get("body"))

	v1, v2, v3 = st.columns(3)
	with v1:
		variant_id = st.text_input("variantId", value=str(st.session_state.get("variant_id") or ""))
	with v2:
		qty = st.text_input("qty", value="1")
	with v3:
		mods = st.text_input("modifierIds (csv)", value="")
	st.session_state["variant_id"] = variant_id.strip()

	assign = st.checkbox("include tableId on body (table assignment)", value=bool(table_id.strip()))
	b1, b2 = st.columns(2)
	with b1:
		create_c = st.button("POST counter order / add round", type="primary")
	with b2:
		confirm_s = st.button("POST status CONFIRMED (draft stuck)")

	if create_c:
		oid = outlet_id.strip()
		vid = variant_id.strip()
		if not oid or not vid:
			st.error("outletId + variantId required")
		else:
			body = _items_payload(vid, qty, mods)
			body["channel"] = channel
			if assign and table_id.strip():
				body["tableId"] = table_id.strip()
			key = idem_in.strip() or str(uuid.uuid4())
			_call(
				"POST",
				f"/api/v1/outlets/{oid}/orders",
				json_body=body,
				idem=key,
				token=client.staff_token(),
			)
			_show_last()
	if confirm_s:
		oid = (st.session_state.get("active_order_id") or "").strip()
		if not oid:
			st.error("orderId required")
		else:
			_call(
				"POST",
				f"/api/v1/orders/{oid}/status",
				json_body={"status": "CONFIRMED"},
				token=client.staff_token(),
			)
			_show_last()

with tab_guest:
	st.write("QR path: public token → session (guest JWT) → menu → rounds → table order.")
	g1, g2, g3 = st.columns(3)
	with g1:
		info = st.button("GET public QR info")
	with g2:
		sess = st.button("POST guest session")
	with g3:
		gmenu = st.button("GET QR menu")

	tok = qr_token.strip()
	if info:
		if not tok:
			st.error("QR token required")
		else:
			_call("GET", f"/api/v1/public/qr/{tok}", token="", remember=False)
			_show_last()
	if sess:
		if not tok:
			st.error("QR token required")
		else:
			rec = _call(
				"POST",
				f"/api/v1/public/qr/{tok}/sessions",
				json_body={},
				idem=idem_in.strip() or str(uuid.uuid4()),
				token="",
				remember=False,
			)
			body = rec.get("body") if isinstance(rec.get("body"), dict) else {}
			at = body.get("accessToken")
			if at:
				client.set_guest_token(at)
			if body.get("tableId"):
				st.session_state["table_id"] = str(body["tableId"])
			if body.get("outletId"):
				st.session_state["outlet_id"] = str(body["outletId"])
			_show_last()
	if gmenu:
		if not tok:
			st.error("QR token required")
		else:
			_call("GET", f"/api/v1/public/qr/{tok}/menu", token=client.guest_token(), remember=False)
			_show_last()

	gv1, gv2, gv3 = st.columns(3)
	with gv1:
		g_variant = st.text_input("guest variantId", value=str(st.session_state.get("variant_id") or ""), key="g_var")
	with gv2:
		g_qty = st.text_input("guest qty", value="1", key="g_qty")
	with gv3:
		g_mods = st.text_input("guest modifierIds csv", value="", key="g_mods")

	r1, r2 = st.columns(2)
	with r1:
		grow = st.button("POST guest round (create/add)", type="primary")
	with r2:
		gget = st.button("GET guest table order")

	if grow:
		if not tok or not g_variant.strip():
			st.error("QR token + variantId required")
		else:
			key = idem_in.strip() or str(uuid.uuid4())
			_call(
				"POST",
				f"/api/v1/public/qr/{tok}/rounds",
				json_body=_items_payload(g_variant, g_qty, g_mods),
				idem=key,
				token=client.guest_token(),
			)
			_show_last()
	if gget:
		if not tok:
			st.error("QR token required")
		else:
			_call("GET", f"/api/v1/public/qr/{tok}/order", token=client.guest_token())
			_show_last()

with tab_status:
	st.write("Cancel uses Idempotency-Key. Status command is the illegal-transition tester (`ORDER_ILLEGAL_STATUS` → 409).")
	oid = (st.session_state.get("active_order_id") or "").strip()
	target = st.selectbox("target status", STATUSES, index=1)
	c1, c2, c3 = st.columns(3)
	with c1:
		do_cancel = st.button("POST cancel")
	with c2:
		do_status = st.button("POST /status (tester)", type="primary")
	with c3:
		do_unlock = st.button("POST unlock-add")

	if do_cancel:
		if not oid:
			st.error("orderId required")
		else:
			key = idem_in.strip() or str(uuid.uuid4())
			_call(
				"POST",
				f"/api/v1/orders/{oid}/cancel",
				json_body={},
				idem=key,
				token=client.staff_token(),
			)
			_show_last()
	if do_status:
		if not oid:
			st.error("orderId required")
		else:
			_call(
				"POST",
				f"/api/v1/orders/{oid}/status",
				json_body={"status": target},
				token=client.staff_token(),
			)
			_show_last()
	if do_unlock:
		if not oid:
			st.error("orderId required")
		else:
			_call("POST", f"/api/v1/orders/{oid}/unlock-add", json_body={}, token=client.staff_token())
			_show_last()

	if st.session_state.get("last_order_call") and not (do_cancel or do_status or do_unlock):
		_show_last()

with tab_snap:
	st.write("Line `nameSnapshot` is stored on the server; GET order view does not include lines. JSON below is the last order payload as returned.")
	snap = st.session_state.get("order_snapshot")
	if snap is None:
		st.info("No snapshot yet — create/GET an order.")
	else:
		st.code(json.dumps(snap, default=str, indent=2))
	st.subheader("last HTTP")
	_show_last()
