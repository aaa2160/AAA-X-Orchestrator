#!/usr/bin/env python3
"""
AAA-X Standalone Python Telegram Worker
Automates fetching rented phone numbers and intercepting SMS OTPs
from @EHR_QUICKINCOME_BOT via Telethon MTProto client.

Features:
- Sends /start or clicks "+ GET NUMBER" to @EHR_QUICKINCOME_BOT
- Extracts phone numbers from the bot's inline keyboard (e.g., "+2348091267977")
- Listens for incoming OTP verification codes (6-digit) from Twitter / X
- Automatically syncs detected phone & OTP to the AAA-X Cloud Orchestrator on Render
"""

import os
import re
import sys
import time
import asyncio
import requests
from telethon import TelegramClient, events
from telethon.tl.custom import Button

# Configuration
API_ID = int(os.environ.get("TELEGRAM_API_ID", "2040"))  # Default or custom Telegram API ID
API_HASH = os.environ.get("TELEGRAM_API_HASH", "b18441a1ff607e10a989891a5462e627")
SESSION_NAME = os.environ.get("TELEGRAM_SESSION", "aaa_telegram_session")
TARGET_BOT = os.environ.get("TARGET_BOT", "EHR_QUICKINCOME_BOT")
CLOUD_ENDPOINT = os.environ.get("CLOUD_ENDPOINT", "https://aaa-x-cloud-worker.onrender.com")

client = TelegramClient(SESSION_NAME, API_ID, API_HASH)

def sync_to_cloud(endpoint_path: str, data: dict):
    """Syncs phone numbers and OTPs to the active Render cloud worker."""
    url = f"{CLOUD_ENDPOINT.rstrip('/')}/{endpoint_path.lstrip('/')}"
    try:
        resp = requests.post(url, json=data, timeout=8)
        print(f"[*] Cloud Sync ({endpoint_path}): {resp.status_code} - {resp.json()}")
    except Exception as e:
        print(f"[!] Cloud Sync Warning ({endpoint_path}): {e}")

@client.on(events.NewMessage(chats=TARGET_BOT))
async def handle_bot_message(event):
    message_text = event.message.message or ""
    print(f"\n[+] Incoming from @{TARGET_BOT}:\n{message_text}")

    # 1. Search for phone number in inline buttons or text
    phone_found = None
    if event.message.buttons:
        for row in event.message.buttons:
            for btn in row:
                btn_text = btn.text
                match = re.search(r"\+([0-9]{9,15})", btn_text)
                if match:
                    phone_found = match.group(0)
                    print(f"[🎯] Phone Number Detected in Button: {phone_found}")
                    sync_to_cloud("/api/phone", {"phoneNumber": phone_found})

    if not phone_found:
        match = re.search(r"\+([0-9]{9,15})", message_text)
        if match:
            phone_found = match.group(0)
            print(f"[🎯] Phone Number Detected in Text: {phone_found}")
            sync_to_cloud("/api/phone", {"phoneNumber": phone_found})

    # 2. Search for 6-digit OTP verification code
    otp_match = re.search(r"\b\d{6}\b", message_text)
    if otp_match:
        otp_code = otp_match.group(0)
        print(f"[🔥] OTP Code Detected: {otp_code}")
        sync_to_cloud("/api/otp", {"code": otp_code, "source": "telegram_bot"})

async def request_new_number():
    """Commands the bot to generate a new number."""
    print(f"[*] Sending request to @{TARGET_BOT}...")
    try:
        bot = await client.get_entity(TARGET_BOT)
        # Send /start first to wake bot or get menu
        await client.send_message(bot, "/start")
        await asyncio.sleep(2)

        # Check last message for "+ GET NUMBER" button
        messages = await client.get_messages(bot, limit=3)
        for msg in messages:
            if msg.buttons:
                for row in msg.buttons:
                    for btn in row:
                        if "GET NUMBER" in btn.text.upper():
                            print(f"[*] Clicking button: {btn.text}")
                            await btn.click()
                            return
        # If no button found, send text "+ GET NUMBER"
        await client.send_message(bot, "+ GET NUMBER")
    except Exception as e:
        print(f"[!] Error requesting number: {e}")

async def main():
    print("=" * 60)
    print("⚡ AAA-X Standalone Python Telegram Worker")
    print(f"[*] Target Bot: @{TARGET_BOT}")
    print(f"[*] Cloud Target: {CLOUD_ENDPOINT}")
    print("=" * 60)

    await client.start()
    me = await client.get_me()
    print(f"[✓] Logged in as: {me.first_name} (@{me.username or 'No Username'}, Phone: +{me.phone})")

    # Trigger initial number request
    await request_new_number()

    print("[*] Worker is running and actively listening for numbers & OTPs...")
    await client.run_until_disconnected()

if __name__ == "__main__":
    asyncio.run(main())
