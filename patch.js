const fs = require('fs');
let content = fs.readFileSync('docker-compose.bpa.yml', 'utf8');
content = content.replace('EGOV_EDCR_HOST: "http://egov-edcr:8080"', 'EGOV_EDCR_HOST: "http://mock-server:8080"');
content += '\n  mock-server:\n    build: ./mock-server\n    container_name: mock-server\n    networks:\n      - bpa-network\n';
fs.writeFileSync('docker-compose.bpa.yml', content);
