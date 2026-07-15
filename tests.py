import json
from http.server import BaseHTTPRequestHandler, HTTPServer

api_responses = {
    "/user/oauth/token": {
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
    "/edcr/rest/dcr/scrutinize": {
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
    "/bpa-services/v1/bpa/_create": {
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
    "/egov-workflow-v2/egov-wf/process/_search": {
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
    "/egov-workflow-v2/egov-wf/process/_transition": {
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

class APIServerRequestHandler(BaseHTTPRequestHandler):
    def send_api_response(self):
        # Match URL path to our API responses
        path = self.path.split('?')[0] # Remove query params for matching
        if path in api_responses:
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(json.dumps(api_responses[path]).encode('utf-8'))
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'{"error": "Endpoint not found"}')

    def do_GET(self):
        self.send_api_response()

    def do_POST(self):
        self.send_api_response()

if __name__ == '__main__':
    server_address = ('', 9090)
    httpd = HTTPServer(server_address, APIServerRequestHandler)
    print("=======================================")
    print(" BPA API SERVER RUNNING ON PORT 9090 ")
    print("=======================================")
    print("Update your Postman baseUrl to:")
    print("http://localhost:9090")
    print("=======================================")
    httpd.serve_forever()
