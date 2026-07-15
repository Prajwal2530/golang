import subprocess

fn = 'ActionController.class'
cmd = f'docker exec egov-accesscontrol unzip -p /opt/egov/egov-accesscontrol-1.1.3-SNAPSHOT.jar BOOT-INF/classes/org/egov/access/web/controller/{fn}'
print('Extracting:', fn)
data = subprocess.check_output(cmd, shell=True)
with open(fn, 'wb') as f:
    f.write(data)
print(f'Wrote {len(data)} bytes to {fn}')
