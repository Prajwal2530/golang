const http = require('http');

const scrutinyResponse = {
  edcrDetail: [{
    edcrNumber: "PL-2024-1234",
    applicationType: "BUILDING_PLAN_SCRUTINY",
    appliactionType: "permit",
    applicationSubType: "NEW_CONSTRUCTION",
    planDetail: {
      planInformation: {
        occupancy: "Residential",
        khataNo: "123",
        plotNo: "SUN178",
        floorCount: 2
      },
      virtualBuilding: {
        totalBuitUpArea: 250.0
      },
      blocks: [{
        building: {
          buildingHeight: 16,
          floors: [],
          totalFloorArea: 200.0
        }
      }],
      plot: {
        plotBndryArea: 800.0
      }
    },
    status: "Accepted"
  }]
};

http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  console.log(`${req.method} ${req.url}`);
  
  if (req.url.includes('/edcr/rest/dcr/scrutinize') || req.url.includes('/edcr/rest/dcr/scrutinydetails')) {
    res.writeHead(200);
    res.end(JSON.stringify(scrutinyResponse));
  } else if (req.url.includes('/billing-service/demand/_search')) {
    res.writeHead(200);
    res.end(JSON.stringify({ Demands: [] }));
  } else if (req.url.includes('/billing-service/demand/_create') || req.url.includes('/billing-service/demand/_update')) {
    let body = '';
    req.on('data', chunk => body += chunk.toString());
    req.on('end', () => {
      try {
        let payload = JSON.parse(body);
        if (payload.Demands) {
          payload.Demands.forEach(d => { if (!d.id) d.id = 'mock-demand-' + Date.now(); });
        }
        res.writeHead(200);
        res.end(JSON.stringify({ Demands: payload.Demands || [] }));
      } catch(e) {
        res.writeHead(400);
        res.end(JSON.stringify({ error: "Invalid JSON" }));
      }
    });
  } else if (req.url.includes('token')) {
    res.writeHead(200);
    res.end(JSON.stringify({
      access_token: "mock-edcr-token-12345",
      token_type: "bearer",
      expires_in: 604800
    }));
  } else {
    res.writeHead(404);
    res.end(JSON.stringify({error: "Not found"}));
  }
}).listen(8080, () => {
  console.log("Mock EDCR server running on port 8080");
});
