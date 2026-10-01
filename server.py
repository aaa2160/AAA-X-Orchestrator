import os
import re
import time
from typing import List, Optional
from fastapi import FastAPI, Request, Form
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel
import uvicorn

app = FastAPI(title="AAA-X Cloud Orchestrator", version="1.7.0")

# In-memory storage for cloud state
active_phone_number = "+48459074091"
latest_otp = None
latest_otp_timestamp = None
accounts_vault = []

class AccountPayload(BaseModel):
    username: str
    password: str
    phoneNumber: Optional[str] = None
    twoFaSecret: Optional[str] = None
    cookies: Optional[str] = None

class OtpPayload(BaseModel):
    code: str
    source: Optional[str] = "webhook"

class PhonePayload(BaseModel):
    phoneNumber: str

@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "AAA-X Cloud Orchestrator",
        "region": "Frankfurt, Germany (DE)",
        "egress": "Native German IP (Face Verification Bypass Active)",
        "accounts_count": len(accounts_vault),
        "active_phone": active_phone_number,
        "latest_otp": latest_otp
    }

@app.get("/api/accounts")
def get_accounts():
    return {"count": len(accounts_vault), "accounts": accounts_vault}

@app.post("/api/accounts")
def add_account(account: AccountPayload):
    record = account.dict()
    record["created_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    accounts_vault.insert(0, record)
    return {"status": "saved", "account": record}

@app.get("/api/otp")
def get_otp():
    return {
        "latest_otp": latest_otp,
        "timestamp": latest_otp_timestamp
    }

@app.post("/api/otp")
def post_otp(payload: OtpPayload):
    global latest_otp, latest_otp_timestamp
    latest_otp = payload.code
    latest_otp_timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    return {"status": "ok", "latest_otp": latest_otp}

@app.post("/api/phone")
def set_phone(payload: PhonePayload):
    global active_phone_number
    active_phone_number = payload.phoneNumber
    return {"status": "ok", "active_phone": active_phone_number}

@app.post("/api/webhook/telegram")
async def telegram_webhook(request: Request):
    global latest_otp, latest_otp_timestamp, active_phone_number
    try:
        body = await request.json()
        text = ""
        if "message" in body and "text" in body["message"]:
            text = body["message"]["text"]
        elif "channel_post" in body and "text" in body["channel_post"]:
            text = body["channel_post"]["text"]

        if text:
            # Extract 6-digit OTP
            otp_match = re.search(r"\b\d{6}\b", text)
            if otp_match:
                latest_otp = otp_match.group(0)
                latest_otp_timestamp = time.strftime("%Y-%m-%d %H:%M:%S")

            # Extract Phone Number (+ followed by 9-14 digits)
            phone_match = re.search(r"(\+48[0-9]{9}|\+[0-9]{10,14})", text)
            if phone_match:
                active_phone_number = phone_match.group(0)

        return {"status": "processed", "detected_otp": latest_otp, "detected_phone": active_phone_number}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.get("/", response_class=HTMLResponse)
def dashboard():
    accounts_rows = ""
    for acc in accounts_vault[:20]:
        accounts_rows += f"""
        <tr style="border-bottom: 1px solid #334155;">
            <td style="padding: 10px; color: #38BDF8; font-weight: bold;">{acc.get('username')}</td>
            <td style="padding: 10px; color: #94A3B8;">{acc.get('password')}</td>
            <td style="padding: 10px; color: #E2E8F0;">{acc.get('phoneNumber', '-')}</td>
            <td style="padding: 10px; color: #34D399; font-family: monospace;">{acc.get('twoFaSecret', 'None')}</td>
            <td style="padding: 10px; color: #64748B;">{acc.get('created_at', '-')}</td>
        </tr>
        """

    if not accounts_rows:
        accounts_rows = """
        <tr>
            <td colspan="5" style="padding: 20px; text-align: center; color: #64748B;">
                No accounts created yet. Start registration via the AAA-X App or Cloud Dispatch.
            </td>
        </tr>
        """

    html = f"""
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>AAA-X Cloud Orchestrator</title>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
        <style>
            body {{
                font-family: 'Inter', sans-serif;
                background-color: #0F172A;
                color: #F8FAFC;
                margin: 0;
                padding: 20px;
            }}
            .container {{
                max-width: 1000px;
                margin: 0 auto;
            }}
            .card {{
                background: #1E293B;
                border-radius: 12px;
                padding: 20px;
                margin-bottom: 20px;
                border: 1px solid #334155;
            }}
            .badge {{
                display: inline-block;
                padding: 4px 10px;
                border-radius: 6px;
                font-size: 12px;
                font-weight: 700;
            }}
            .badge-de {{ background: #065F46; color: #34D399; }}
            .badge-live {{ background: #1E40AF; color: #60A5FA; }}
            .badge-otp {{ background: #B45309; color: #FBBF24; font-size: 16px; padding: 6px 14px; }}
            table {{ width: 100%; border-collapse: collapse; }}
            th {{ text-align: left; padding: 10px; color: #94A3B8; font-size: 12px; border-bottom: 2px solid #334155; }}
            .btn {{
                background: #2563EB;
                color: white;
                border: none;
                padding: 8px 16px;
                border-radius: 6px;
                cursor: pointer;
                font-weight: 600;
            }}
            .btn:hover {{ background: #1D4ED8; }}
        </style>
    </head>
    <body>
        <div class="container">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;">
                <div>
                    <h1 style="margin: 0; font-size: 24px; color: #38BDF8;">⚡ AAA-X Cloud Orchestrator</h1>
                    <p style="margin: 4px 0 0; color: #94A3B8; font-size: 13px;">Automated Cloud Worker deployed on Render</p>
                </div>
                <div>
                    <span class="badge badge-de">🇩🇪 Region: Frankfurt, Germany</span>
                    <span class="badge badge-live">● Cloud Active</span>
                </div>
            </div>

            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px;">
                <div class="card">
                    <h3 style="margin-top: 0; font-size: 14px; color: #94A3B8;">ACTIVE PHONE NUMBER</h3>
                    <div style="font-size: 22px; font-weight: 700; color: #F8FAFC;">{active_phone_number}</div>
                    <p style="margin: 6px 0 0; font-size: 11px; color: #64748B;">Ready for Twitter signup (up to 6 OTPs per number)</p>
                </div>

                <div class="card">
                    <h3 style="margin-top: 0; font-size: 14px; color: #94A3B8;">LATEST INBOUND OTP</h3>
                    <div style="margin-top: 4px;">
                        <span class="badge badge-otp">{latest_otp if latest_otp else 'Waiting for OTP...'}</span>
                    </div>
                    <p style="margin: 6px 0 0; font-size: 11px; color: #64748B;">Timestamp: {latest_otp_timestamp if latest_otp_timestamp else 'No OTP received yet'}</p>
                </div>

                <div class="card">
                    <h3 style="margin-top: 0; font-size: 14px; color: #94A3B8;">GERMAN BYPASS STATUS</h3>
                    <div style="font-size: 16px; font-weight: 700; color: #34D399;">✓ ACTIVE (Frankfurt Egress)</div>
                    <p style="margin: 6px 0 0; font-size: 11px; color: #64748B;">Bypasses Twitter Face Verification checks</p>
                </div>
            </div>

            <div class="card">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                    <h2 style="margin: 0; font-size: 16px;">Saved Twitter Accounts ({len(accounts_vault)})</h2>
                    <button class="btn" onclick="location.reload()">Refresh Live Data</button>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>USERNAME</th>
                            <th>PASSWORD</th>
                            <th>PHONE NUMBER</th>
                            <th>2FA SECRET</th>
                            <th>CREATED AT</th>
                        </tr>
                    </thead>
                    <tbody>
                        {accounts_rows}
                    </tbody>
                </table>
            </div>
        </div>
    </body>
    </html>
    """
    return HTMLResponse(content=html)

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 10000))
    uvicorn.run(app, host="0.0.0.0", port=port)
