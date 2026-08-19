"""Entry. Sibling scripts in pages/ are auto-loaded by Streamlit."""
import os

import streamlit as st

import client

st.set_page_config(page_title="Restaurant SaaS", layout="wide")

st.sidebar.header("API")
default_base = os.environ.get("API_BASE_URL", "http://localhost:8080")
base = st.sidebar.text_input("API base URL", value=default_base)
client.BASE_URL = (base or default_base).rstrip("/")

st.sidebar.caption("JWT: Authorization Bearer. Guest writes need GUEST role. Idempotency-Key on mutating public/staff posts.")

health = st.sidebar.empty()
try:
	r = client.request("GET", "/actuator/health", token="")
	health.write(f"health {r.status_code} {r.text[:240]}")
except Exception as e:
	health.write(f"health down: {e}")

staff = client.staff_token()
guest = client.guest_token()
st.sidebar.write("staff:", "in" if staff else "out")
st.sidebar.write("guest:", "in" if guest else "out")

st.title("Restaurant SaaS (thin UI)")
st.write("All math on backend (`:8080`). This app only calls existing `/api/v1/**`.")
st.write("Multipage: put scripts in `streamlit_app/pages/` — Streamlit picks them up. Do not duplicate logic here.")

PAGES = [
	"1_Onboard_Auth",
	"2_Floor_QR",
	"3_Menu",
	"4_Orders",
	"5_Kitchen",
	"6_Billing",
	"7_Inventory",
	"8_Reports",
]
st.subheader("Pages")
for name in PAGES:
	path = f"pages/{name}.py"
	try:
		st.page_link(path, label=name)
	except Exception:
		st.write(f"- `{name}` (file missing: `{path}`)")
