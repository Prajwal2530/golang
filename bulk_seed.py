import urllib.request
import json

modules = [
    "rainmaker-common",
    "digit-ui",
    "digit-tenants",
    "rainmaker-pb",
    "digit-privacy-policy",
    "rainmaker-bpa"
]

tenantId = "pb"
locale = "en_IN"

# Need the superuser userInfo to bypass auth
userInfo = {
    "id": "1",
    "uuid": "12345-67890",
    "type": "EMPLOYEE",
    "roles": [{"name": "Superuser", "code": "SUPERUSER", "tenantId": "pb"}],
    "tenantId": "pb"
}

for mod in modules:
    print(f"Fetching from live environment for module: {mod}")
    
    # Fetch from live
    fetch_payload = {
        "RequestInfo": {},
        "tenantId": tenantId,
        "module": mod,
        "locale": locale
    }
    
    fetch_url = f'https://mseva.lgpunjab.gov.in/localization/messages/v1/_search?module={mod}&locale={locale}&tenantId={tenantId}'
    fetch_req = urllib.request.Request(
        fetch_url,
        data=json.dumps(fetch_payload).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    
    try:
        with urllib.request.urlopen(fetch_req) as response:
            data = json.loads(response.read().decode('utf-8'))
            messages = data.get("messages", [])
            
            if not messages:
                print(f"No messages found for {mod}.")
                continue
                
            print(f"Fetched {len(messages)} messages for {mod}. Upserting to localhost in batches...")
            
            batch_size = 1000
            for i in range(0, len(messages), batch_size):
                batch = messages[i:i + batch_size]
                
                upsert_payload = {
                    "RequestInfo": {
                        "userInfo": userInfo
                    },
                    "tenantId": tenantId,
                    "module": mod,
                    "locale": locale,
                    "messages": batch
                }
                
                upsert_req = urllib.request.Request(
                    'http://localhost/localization/messages/v1/_create',
                    data=json.dumps(upsert_payload).encode('utf-8'),
                    headers={'Content-Type': 'application/json'},
                    method='POST'
                )
                
                with urllib.request.urlopen(upsert_req) as upsert_resp:
                    upsert_result = json.loads(upsert_resp.read().decode('utf-8'))
                    print(f"  Successfully seeded batch {i//batch_size + 1}: {len(upsert_result.get('messages', []))} messages.")
                
    except Exception as e:
        print(f"Error processing module {mod}: {e}")
        if hasattr(e, 'read'):
            print(e.read().decode('utf-8'))
