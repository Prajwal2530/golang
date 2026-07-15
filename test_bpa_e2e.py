import json
import urllib.request
import urllib.parse
import time
import sys

BASE_URL = "http://localhost:8080"

# Step 1: Skip OAuth token fetch and use the one from collection
print("1. Loading auth info from collection...")
# Load BPA Collection
with open('bpa-collection.json', 'r') as f:
    collection = json.loads(f.read())

def get_payload(name_prefix, items=None):
    if items is None:
        items = collection['item']
    for item in items:
        if 'item' in item:
            res = get_payload(name_prefix, item['item'])
            if res:
                return res
        if item.get('name', '').startswith(name_prefix):
            if 'request' in item and 'body' in item['request']:
                return json.loads(item['request']['body']['raw'])
    return None


auth_token = get_payload('1. Create BPA')['RequestInfo']['authToken']
user_info = get_payload('1. Create BPA')['RequestInfo']['userInfo']
# Add BPA_VERIFIER to roles just in case
user_info['roles'].append({"code": "BPA_VERIFIER", "tenantId": "pb.amritsar"})
print(f"✅ Using auth token: {auth_token[:15]}...")

# Step 2: BPA Create
print("\n2. Creating BPA...")
create_payload = get_payload('1. Create BPA')
create_payload['RequestInfo']['authToken'] = auth_token

import random
random_edcr = f"PL-2026-{random.randint(10000, 99999)}"
create_payload['BPA']['edcrNumber'] = random_edcr
print(f"Using new EDCR Number: {random_edcr}")


req = urllib.request.Request(f"{BASE_URL}/bpa-services/v1/bpa/_create", 
                             data=json.dumps(create_payload).encode(), 
                             headers={'Authorization': f'Bearer {auth_token}', 'Content-Type': 'application/json'})

try:
    with urllib.request.urlopen(req) as response:
        bpa_res = json.loads(response.read())
        business_id = bpa_res['BPA'][0]['applicationNo']
        print(f"✅ BPA Created! Application No: {business_id}")
except urllib.error.HTTPError as e:
    print(f"BPA Create failed: {e}")
    print(e.read().decode('utf-8', errors='replace'))
    sys.exit(1)
except Exception as e:
    print(f"BPA Create failed: {e}")
    sys.exit(1)

# Step 3: Workflow INITIATE is automatically done by bpa-services. We just skip it here.

# Step 3.5: Workflow Search to see current state (with polling)
print("\n3.5. Workflow Search to get current state (polling up to 15s)...")
search_res = None
for i in range(15):
    try:
        req = urllib.request.Request(f"{BASE_URL}/egov-workflow-v2/egov-wf/process/_search?tenantId=pb.amritsar&history=true&businessIds={business_id}", 
                                     data=json.dumps({"RequestInfo": {"authToken": auth_token, "userInfo": user_info}}).encode(),
                                     headers={'Authorization': f'Bearer {auth_token}', 'Content-Type': 'application/json'})
        with urllib.request.urlopen(req) as response:
            res = json.loads(response.read())
            if res.get('ProcessInstances') and len(res['ProcessInstances']) > 0:
                search_res = res
                print("✅ Found workflow instance!")
                print("Current Workflow State:", json.dumps(search_res['ProcessInstances'][0]['state'], indent=2))
                print("Next Actions:", [a['action'] for a in search_res['ProcessInstances'][0].get('nextActions', [])])
                break
    except Exception as e:
        if hasattr(e, 'read'):
            print("Search failed:", e.read().decode('utf-8', errors='replace'))
        else:
            print("Search failed:", e)
    time.sleep(1)

if not search_res:
    print("❌ Failed to find workflow instance after polling.")
    sys.exit(1)

# Step 4: Workflow APPROVE
print("\n4. Workflow APPROVE...")
approve_payload = get_payload('3. Workflow APPROVE')
approve_payload['RequestInfo']['authToken'] = auth_token
approve_payload['ProcessInstances'][0]['businessId'] = business_id

req = urllib.request.Request(f"{BASE_URL}/egov-workflow-v2/egov-wf/process/_transition", 
                             data=json.dumps(approve_payload).encode(), 
                             headers={'Authorization': f'Bearer {auth_token}', 'Content-Type': 'application/json'})

try:
    with urllib.request.urlopen(req) as response:
        print("✅ Workflow APPROVE success!")
except urllib.error.HTTPError as e:
    print(f"Workflow APPROVE failed: {e}")
    print(e.read().decode('utf-8', errors='replace'))
    sys.exit(1)
except Exception as e:
    print(f"Workflow APPROVE failed: {e}")
    sys.exit(1)

# Step 5: Workflow Search
print("\n5. Workflow Search (waiting 2 seconds)...")
time.sleep(2)
req = urllib.request.Request(f"{BASE_URL}/egov-workflow-v2/egov-wf/process/_search?tenantId=pb.amritsar&history=true&businessIds={business_id}", 
                             data=json.dumps({"RequestInfo": {"authToken": auth_token, "userInfo": user_info}}).encode(),
                             headers={'Authorization': f'Bearer {auth_token}', 'Content-Type': 'application/json'})

try:
    with urllib.request.urlopen(req) as response:
        search_res = json.loads(response.read())
        status = search_res['ProcessInstances'][0]['state']['state']
        print(f"✅ Workflow Search success! Final Status: {status}")
        if status != 'APPROVED':
            print(f"⚠️ Warning: Expected APPROVED, got {status}")
except urllib.error.HTTPError as e:
    print(f"Workflow Search failed: {e}")
    print(e.read().decode('utf-8', errors='replace'))
    sys.exit(1)
except Exception as e:
    print(f"Workflow Search failed: {e}")
    sys.exit(1)

# Update Collection
print("\nUpdating bpa-collection.json with new authToken and businessId...")
for var in collection['variable']:
    if var['key'] == 'authToken':
        var['value'] = auth_token

for item in collection['item']:
    if 'item' in item:
        # Skip folders for this top-level update
        continue
    if 'request' in item and 'body' in item['request'] and item['request']['body'].get('mode') == 'raw':
        try:
            body = json.loads(item['request']['body']['raw'])
            if 'RequestInfo' in body:
                body['RequestInfo']['authToken'] = auth_token
            if 'ProcessInstances' in body:
                body['ProcessInstances'][0]['businessId'] = business_id
            item['request']['body']['raw'] = json.dumps(body, indent=2)
        except Exception:
            pass
    if item.get('name') == '4. Workflow Search':
        if 'request' in item and 'url' in item['request']:
            item['request']['url']['raw'] = f"{{{{baseUrl}}}}/egov-workflow-v2/egov-wf/process/_search?tenantId=pb.amritsar&businessIds={business_id}"
            for query in item['request']['url']['query']:
                if query['key'] == 'businessIds':
                    query['value'] = business_id

with open('bpa-collection.json', 'w') as f:
    json.dump(collection, f, indent=2)
print("✅ bpa-collection.json updated successfully!")
