import subprocess
import re

files_res = subprocess.run(['docker', 'exec', 'digit-ui', 'find', '/var/web/digit-ui/', '-name', '*.js'], capture_output=True)
files = files_res.stdout.decode('utf-8', errors='ignore').splitlines()

for f in files:
    content_res = subprocess.run(['docker', 'exec', 'digit-ui', 'cat', f], capture_output=True)
    content = content_res.stdout.decode('utf-8', errors='ignore')
    matches_obps = re.findall(r'.{0,50}/obps/.{0,50}', content)
    matches_bpa = re.findall(r'.{0,50}/egov-bpa/.{0,50}', content)
    
    if matches_obps:
        print(f"File {f} matches /obps/:")
        for m in matches_obps[:3]:
            print("  ", m.strip())
            
    if matches_bpa:
        print(f"File {f} matches /egov-bpa/:")
        for m in matches_bpa[:3]:
            print("  ", m.strip())
