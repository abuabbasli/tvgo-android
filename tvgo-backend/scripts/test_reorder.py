import requests
import os

API_URL = "http://localhost:8000/api"

def test_reorder():
    # 1. Login
    login_data = {
        "username": "admin",
        "password": "password" # Trying default
    }
    # Try to find password from env or other scripts? 
    # Verify_status_unique.py used hardcoded credentials? No, it used create_subscriber?
    
    # I'll try generic admin login.
    # If not, I'll use the one from known setups.
    
    # Actually, verify_status_unique.py uses "test_user_...".
    # I need ADMIN login.
    
    # Try logging in as admin.
    resp = requests.post(f"{API_URL}/auth/login", json={"username": "admin", "password": "admin"})
        
    if resp.status_code != 200:
        print("Could not login as admin")
        return

    token = resp.json()["accessToken"]
    headers = {"Authorization": f"Bearer {token}"}

    # 2. Reorder Payload
    payload = [
        {"id": "test-channel-1", "order": 1},
        {"id": "test-channel-2", "order": 2}
    ]

    r = requests.put(f"{API_URL}/admin/channels/reorder", json={"items": payload}, headers=headers)
    print(f"Status: {r.status_code}")
    print(f"Response: {r.text}")

if __name__ == "__main__":
    test_reorder()
