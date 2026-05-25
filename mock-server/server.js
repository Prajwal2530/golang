const http = require('http');
const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    edcrDetail: [{
      status: 'Accepted',
      appliactionType: 'BUILDING_PLAN_SCRUTINY',
      applicationSubType: 'NEW_CONSTRUCTION',
      permitNumber: '',
      planDetail: {
        virtualBuilding: {
          occupancyTypes: [{ type: { code: 'RESIDENTIAL' } }]
        },
        plot: { area: [1000] },
        blocks: [{ building: { buildingHeight: [10] } }]
      }
    }]
  }));
});
server.listen(8080);
console.log('Mock server listening on 8080');
