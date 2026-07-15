import json
import os

changed_total = 0
for root, dirs, files in os.walk('mdms-data/data/pb'):
    for file in files:
        if file in ('actions.json', 'actions-test.json'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            key = 'actions-test' if 'actions-test' in data else 'actions'
            if key not in data:
                continue
                
            changed = 0
            for a in data.get(key, []):
                url = a.get('navigationURL', '')
                if url.startswith('/'):
                    a['navigationURL'] = url.lstrip('/')
                    changed += 1
                    
            if changed > 0:
                with open(path, 'w', encoding='utf-8') as f:
                    json.dump(data, f, indent=2)
                print(f'Fixed {changed} URLs in {path}')
                changed_total += changed
                
print(f'Total URLs fixed: {changed_total}')
