import json, glob

files = glob.glob('mdms-data/data/pb/**/actions-test.json', recursive=True)
files += glob.glob('mdms-data/data/pb/**/actions.json', recursive=True)

# PHASE 1: Fix all bad navigationURLs
for path in files:
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    key = 'actions-test' if 'actions-test' in data else 'actions'
    changed = 0
    for action in data.get(key, []):
        url = action.get('navigationURL', '')
        if url and not url.startswith('/') and not url.startswith('http'):
            action['navigationURL'] = '/' + url
            changed += 1

    if changed:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2)
        print('FIXED %d URLs in: %s' % (changed, path))
    else:
        print('No changes needed: %s' % path)

print()

# PHASE 2: Verify - print any remaining bad URLs
bad = []
for path in files:
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    key = 'actions-test' if 'actions-test' in data else 'actions'
    for action in data.get(key, []):
        url = action.get('navigationURL', '')
        if url and not url.startswith('/') and not url.startswith('http'):
            bad.append((url, path))

if bad:
    print('STILL BAD (%d entries):' % len(bad))
    for url, path in bad:
        print('  %s  in  %s' % (url, path))
else:
    print('VERIFICATION PASSED: All navigationURLs have leading slash or are absolute.')

# PHASE 3: Specifically verify the BPA action
print()
for path in files:
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    key = 'actions-test' if 'actions-test' in data else 'actions'
    for action in data.get(key, []):
        nav = action.get('navigationURL', '')
        if 'egov-bpa' in nav:
            print('BPA action: name=%s  navigationURL=%s  file=%s' % (action.get('name'), nav, path))
