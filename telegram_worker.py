#!/usr/bin/env python3
"""
AAA-X Standalone Python Telegram Worker
Automates fetching rented phone numbers and intercepting SMS OTPs
from @EHR_QUICKINCOME_BOT via Telethon MTProto client.

Fully autonomous:
- Sends /start or clicks "GET NUMBER"
- Selects "TWITTER" automatically
- Selects "NIGERIA" automatically
- Captures generated phone number (e.g., +2348170905785)
- Syncs phone to Render cloud worker
- Listens for incoming 6-digit Twitter OTP verification codes
- Syncs OTP to Render cloud worker
"""

import os
import re
import sys
import time
import json
import asyncio
import urllib.request
from telethon import TelegramClient, events

# Configuration
API_ID = int(os.environ.get("TELEGRAM_API_ID", "2040"))
API_HASH = os.environ.get("TELEGRAM_API_HASH", "b18441a1ff607e10a989891a5462e627")
SESSION_NAME = os.environ.get("TELEGRAM_SESSION", "session_auth")
TARGET_BOT = os.environ.get("TARGET_BOT", "EHR_QUICKINCOME_BOT")
CLOUD_ENDPOINT = os.environ.get("CLOUD_ENDPOINT", "https://aaa-x-cloud-worker.onrender.com")

client = TelegramClient(SESSION_NAME, API_ID, API_HASH)

def sync_to_cloud(endpoint_path: str, data: dict):
    """Syncs phone numbers and OTPs to the active Render cloud worker."""
    url = f"{CLOUD_ENDPOINT.rstrip('/')}/{endpoint_path.lstrip('/')}"
    try:
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            print(f"[*] Cloud Sync ({endpoint_path}): {resp.status} - {body}")
    except Exception as e:
        print(f"[!] Cloud Sync Warning ({endpoint_path}): {e}")

last_phone_time = 0.0
is_requesting = False

@client.on(events.NewMessage(chats=TARGET_BOT))
@client.on(events.MessageEdited(chats=TARGET_BOT))
async def handle_bot_message(event):
    global last_phone_time
    message_text = event.message.message or ""
    print(f"\n[+] Incoming from @{TARGET_BOT}:\n{message_text}")

    # Check for phone number in buttons first (e.g. ['+2348170905785'])
    phone_found = None
    if event.message.buttons:
        for row in event.message.buttons:
            for btn in row:
                btn_text = btn.text
                match = re.search(r"\+([0-9]{9,15})", btn_text)
                if match:
                    phone_found = match.group(0)
                    last_phone_time = time.time()
                    print(f"[PHONE] Phone Number Detected in Button: {phone_found}")
                    sync_to_cloud("/api/phone", {"phoneNumber": phone_found})

    # Step 0: Check for Expired Session or Number
    if "EXPIRED" in message_text.upper() or "SESSION CLOSED" in message_text.upper():
        print("[*] Bot notice: Previous session closed. Ready for new number.")
        return

    # Step 0: Check for Main Menu 'GET NUMBER'
    if "SELECT AN OPTION" in message_text.upper() or "WELCOME" in message_text.upper():
        if event.message.buttons:
            for row in event.message.buttons:
                for btn in row:
                    if "GET NUMBER" in btn.text.upper() or "𝗚𝗘𝗧 𝗡𝗨𝗠𝗕𝗘𝗥" in btn.text:
                        print(f"[*] Auto-clicking Main Menu: {btn.text}")
                        await asyncio.sleep(1)
                        await btn.click()
                        return

    # Step 1: Check for Service Selection ('TWITTER')
    if "SELECT SERVICE" in message_text.upper() or "CHOOSE WHAT YOU NEED" in message_text.upper():
        if event.message.buttons:
            for row in event.message.buttons:
                for btn in row:
                    if "TWITTER" in btn.text.upper():
                        print(f"[*] Auto-clicking Service: {btn.text}")
                        await asyncio.sleep(1)
                        await btn.click()
                        return

    # Step 2: Check for Region Selection ('NIGERIA')
    if "SELECT REGION" in message_text.upper() or "AVAILABLE COUNTRIES" in message_text.upper():
        if event.message.buttons:
            for row in event.message.buttons:
                for btn in row:
                    if "NIGERIA" in btn.text.upper():
                        print(f"[*] Auto-clicking Region: {btn.text}")
                        await asyncio.sleep(1)
                        await btn.click()
                        return

    # Step 3: Check for text phone number fallback
    if not phone_found:
        match = re.search(r"\+([0-9]{9,15})", message_text)
        if match:
            phone_found = match.group(0)
            print(f"[PHONE] Phone Number Detected in Text: {phone_found}")
            sync_to_cloud("/api/phone", {"phoneNumber": phone_found})

    # Step 4: Check for 6-digit OTP verification code
    otp_match = re.search(r"\b\d{6}\b", message_text)
    if otp_match:
        otp_code = otp_match.group(0)
        print(f"[OTP] Verification Code Detected: {otp_code}")
        sync_to_cloud("/api/otp", {"code": otp_code, "source": "telegram_bot"})

async def request_new_number():
    """Commands the bot to generate a new number."""
    global is_requesting
    if is_requesting:
        print("[*] Number request already in progress, skipping duplicate.")
        return
    is_requesting = True
    print(f"[*] Triggering number request flow with @{TARGET_BOT}...")
    try:
        bot = await client.get_entity(TARGET_BOT)
        # Check recent messages for "+ GET NUMBER" or "𝗚𝗘𝗧 𝗡𝗨𝗠𝗕𝗘𝗥" button
        messages = await client.get_messages(bot, limit=4)
        for msg in messages:
            if msg.buttons:
                for row in msg.buttons:
                    for btn in row:
                        if "GET NUMBER" in btn.text.upper() or "𝗚𝗘𝗧 𝗡𝗨𝗠𝗕𝗘𝗥" in btn.text:
                            print(f"[*] Clicking menu button: {btn.text}")
                            await btn.click()
                            return
        # If no button found, send /start to trigger menu
        print("[*] Sending /start to reset menu...")
        await client.send_message(bot, "/start")
    except Exception as e:
        print(f"[!] Error requesting number: {e}")
    finally:
        await asyncio.sleep(4)
        is_requesting = False

async def poll_cloud_requests():
    """Polls Render cloud every 3 seconds to trigger bot whenever app requests a fresh number."""
    url = f"{CLOUD_ENDPOINT.rstrip('/')}/api/phone/request"
    while True:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "AAA-X-Worker/1.9"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                if data.get("request_new") is True:
                    print("[*] Cloud signal received: New phone number requested by user/app!")
                    await request_new_number()
        except Exception:
            pass
        await asyncio.sleep(3)

async def run_worker():
    while True:
        try:
            if not client.is_connected():
                await client.connect()
            if not await client.is_user_authorized():
                await client.start()

            me = await client.get_me()
            print(f"[OK] Logged in as: {me.first_name} (@{me.username or 'No Username'}, Phone: +{me.phone})")

            # Start background cloud polling loop
            asyncio.create_task(poll_cloud_requests())

            # Start the automated number request flow
            await request_new_number()

            print("\n[*] Worker is listening in background for numbers & OTPs...")
            await client.run_until_disconnected()
        except Exception as e:
            print(f"[!] Disconnected from Telegram ({e}). Auto-reconnecting in 5s...")
            await asyncio.sleep(5)

async def main():
    print("=" * 65)
    print("AAA-X Autonomous Telegram Worker for @EHR_QUICKINCOME_BOT")
    print(f"[*] Target Bot: @{TARGET_BOT}")
    print(f"[*] Cloud Target: {CLOUD_ENDPOINT}")
    print("=" * 65)

    await run_worker()

if __name__ == "__main__":
    asyncio.run(main())
