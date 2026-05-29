import json
import os

with open(r'mdms-data\data\pb\amritsar\egov-location\TenantBoundary.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

token = "5889a33b-6d81-4a7b-a964-1d2b8628428f"
req_info = {
    "apiId":"test",
    "ver":"1.0",
    "action":"_create",
    "authToken": token
}

for tb in data.get("TenantBoundary", []):
    payload = {
        "RequestInfo": req_info,
        "TenantBoundary": tb
    }
    filename = f'tb_payload_{tb["hierarchyType"]["code"]}.json'
    with open(filename, 'w', encoding='utf-8') as pf:
        json.dump(payload, pf)
    print(f"Created {filename}")
