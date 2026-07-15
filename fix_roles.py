import json

with open('bpa-collection.json', 'r') as f:
    collection = json.load(f)

for item in collection.get('item', []):
    if 'request' in item and 'body' in item['request'] and 'raw' in item['request']['body']:
        raw = item['request']['body']['raw']
        raw = raw.replace('"code": "SUPERUSER",\n          "tenantId": "pb.amritsar"\n        }', '"code": "SUPERUSER",\n          "tenantId": "pb.amritsar"\n        },\n        {\n          "code": "BPA_BUILDER",\n          "tenantId": "pb.amritsar"\n        }')
        item['request']['body']['raw'] = raw

with open('bpa-collection.json', 'w') as f:
    json.dump(collection, f, indent=2)
