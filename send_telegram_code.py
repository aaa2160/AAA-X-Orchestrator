#!/usr/bin/env python3
import asyncio
import json
from telethon import TelegramClient

api_id = 6
api_hash = 'eb06628fc6d3a7a34275082c66f9394f'
phone = '+8801906045479'

async def main():
    client = TelegramClient('session_auth', api_id, api_hash)
    await client.connect()
    print(f"[*] Connected to Telegram MTProto. Requesting code for {phone}...")
    try:
        sent_code = await client.send_code_request(phone)
        state = {
            "phone": phone,
            "phone_code_hash": sent_code.phone_code_hash,
            "is_password_needed": False
        }
        with open("login_state.json", "w") as f:
            json.dump(state, f)
        print(f"[✓] SUCCESS! Code has been sent to {phone}!")
        print(f"[*] phone_code_hash: {sent_code.phone_code_hash}")
        print(f"[*] Type of code delivery: {sent_code.type}")
    except Exception as e:
        print(f"[!] Error sending code request: {e}")
    finally:
        await client.disconnect()

if __name__ == "__main__":
    asyncio.run(main())
