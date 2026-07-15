import glob, os

for root, dirs, files in os.walk('.'):
    # skip node_modules, .git, .venv, etc.
    if any(p in root for p in ['node_modules', '.git', '.venv', 'backend_snapshot', 'bpa-extracted', 'bpa-egov', 'bpa-calculator-go']):
        continue
    for file in files:
        if file.endswith(('.json', '.html', '.js', '.properties', '.yml', '.yaml', '.conf')):
            path = os.path.join(root, file)
            try:
                content = open(path, 'r', encoding='utf-8', errors='ignore').read()
                if 'redirectUrl' in content or '127.0.0.1' in content:
                    print('Found in:', path)
                    for line in content.splitlines():
                        if 'redirectUrl' in line or '127.0.0.1' in line:
                            print('  ', line[:150])
            except Exception:
                pass
