import json
import uuid

# Load collection
collection_file = 'bpa-collection.json'
with open(collection_file, 'r', encoding='utf-8') as f:
    collection = json.load(f)

# Mock response bodies
mock_bodies = {
    "6. OAuth Token": {
        "access_token": "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
        "token_type": "bearer",
        "refresh_token": "a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
        "expires_in": 35999,
        "scope": "read write",
        "UserRequest": {
            "id": 82,
            "userName": "admin2",
            "name": "Admin",
            "type": "EMPLOYEE",
            "tenantId": "pb.amritsar",
            "uuid": "20a8cc29-b4d5-45f7-ae10-d973b01de2b7",
            "roles": [{"code": "SUPERUSER"}]
        }
    },
    "5. EDCR Scrutinize": {
      "edcrDetail": [
        {
          "tenantId": "pb.amritsar",
          "edcrNumber": "PL-2026-55555",
          "status": "Accepted",
          "planDetail": {
            "planInformation": {
              "occupancy": "Residential",
              "plotArea": 500
            }
          }
        }
      ]
    },
    "1. Create BPA": {
      "ResponseInfo": {
        "apiId": "Rainmaker",
        "ver": ".01",
        "ts": "",
        "resMsgId": "uief87324",
        "msgId": "20170310130900|en_IN",
        "status": "successful"
      },
      "BPA": [
        {
          "tenantId": "pb.amritsar",
          "applicationNo": "CG-OC-2026-06-15-000053",
          "edcrNumber": "PL-2026-55555",
          "status": "INITIATED",
          "businessService": "BPA_OC",
          "applicationType": "permit",
          "workflow": {
            "action": "INITIATE",
            "assignes": []
          }
        }
      ]
    },
    "4. Workflow Search": {
      "ResponseInfo": {
        "apiId": "Rainmaker",
        "ver": ".01",
        "status": "successful"
      },
      "ProcessInstances": [
        {
          "tenantId": "pb.amritsar",
          "businessService": "BPA_OC",
          "businessId": "CG-OC-2026-06-15-000053",
          "action": "INITIATE",
          "moduleName": "bpa-services",
          "state": {
            "state": "INITIATED",
            "applicationStatus": "INITIATED"
          },
          "nextActions": [
            {
              "action": "APPROVE",
              "nextState": "APPROVED"
            }
          ]
        }
      ]
    },
    "3. Workflow APPROVE": {
      "ResponseInfo": {
        "apiId": "Rainmaker",
        "ver": ".01",
        "status": "successful"
      },
      "ProcessInstances": [
        {
          "tenantId": "pb.amritsar",
          "businessService": "BPA_OC",
          "businessId": "CG-OC-2026-06-15-000053",
          "action": "APPROVE",
          "moduleName": "bpa-services",
          "state": {
            "state": "APPROVED",
            "applicationStatus": "APPROVED"
          }
        }
      ]
    }
}

def inject_responses(items):
    for i in items:
        if 'item' in i:
            inject_responses(i['item'])
        else:
            name = i.get('name')
            if name in mock_bodies:
                i['response'] = [
                    {
                        "name": "Successful Response",
                        "originalRequest": {
                            "method": i['request']['method'],
                            "header": i['request'].get('header', []),
                            "url": i['request'].get('url', "")
                        },
                        "status": "OK",
                        "code": 200,
                        "_postman_previewlanguage": "json",
                        "header": [
                            {
                                "key": "Content-Type",
                                "value": "application/json"
                            }
                        ],
                        "cookie": [],
                        "body": json.dumps(mock_bodies[name], indent=2)
                    }
                ]

inject_responses(collection.get('item', []))

with open(collection_file, 'w', encoding='utf-8') as f:
    json.dump(collection, f, indent=4)

print("Successfully injected mock responses into collection.")
