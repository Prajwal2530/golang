import urllib.request
import json

with open('payload2.json', 'r') as f:
    payload = json.load(f)

req = urllib.request.Request(
    'http://localhost/localization/messages/v1/_create',
    data=json.dumps(payload).encode('utf-8'),
    headers={'Content-Type': 'application/json'},
    method='POST'
)

try:
    with urllib.request.urlopen(req) as response:
        print(response.read().decode('utf-8'))
except urllib.error.HTTPError as e:
    print(f"HTTP Error {e.code}: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error: {e}")
