#!/usr/bin/env python3
"""
Signs into Telegram MTProto using the code sent to +8801906045479
and saves the authenticated session for the worker.
"""

import sys
import json
import asyncio
from telethon import TelegramClient
from telethon.errors import SessionPasswordNeededError, PhoneCodeInvalidError, PhoneCodeExpiredError

async def verify(code_str: str, password: str = None):
    with open("login_state.json", "r") as f:
        state = json.load(f)

    api_id = state["api_id"]
    api_hash = state["api_hash"]
    phone = state["phone"]
    phone_code_hash = state["phone_code_hash"]

    client = TelegramClient("session_auth", api_id, api_hash)
    await client.connect()

    try:
        print(f"[*] Submitting code '{code_str}' for {phone}...")
        try:
            await client.sign_in(phone=phone, code=code_str, phone_code_hash=phone_code_hash)
        except SessionPasswordNeededError:
            if password:
                print("[*] 2FA Password detected. Submitting password...")
                await client.sign_in(password=password)
            else:
                print("[!] 2FA PASSWORD REQUIRED: This account has Two-Step Verification enabled. Please provide the 2FA password.")
                return

        me = await client.get_me()
        print(f"[✓] LOGIN SUCCESSFUL!")
        print(f"[*] Account Name: {me.first_name} {me.last_name or ''}")
        print(f"[*] Username: @{me.username or 'No Username'}")
        print(f"[*] ID: {me.id}")
        print("[✓] Session saved as 'session_auth.session'. Ready for automated bot worker!")

    except PhoneCodeInvalidError:
        print("[!] The code you entered is invalid. Please double check the code.")
    except PhoneCodeExpiredError:
        print("[!] The code has expired. A new code request is required.")
    except Exception as e:
        print(f"[!] Error signing in: {e}")
    finally:
        await client.disconnect()

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 verify_telegram_code.py <CODE> [2FA_PASSWORD]")
        sys.exit(1)

    code = sys.argv[1].strip()
    pwd = sys.argv[2].strip() if len(sys.argv) > 2 else None
    asyncio.run(verify(code, pwd))
