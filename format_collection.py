import json

with open('bpa-collection.json', 'r', encoding='utf-8') as f:
    col = json.load(f)

# Folders definition
folders = {
    "1-Auth": [],
    "2-BPA Create": [],
    "3-Workflow Transitions": [],
    "4-Workflow Search": [],
    "5-Verify": []
}

for item in col.get('item', []):
    name = item.get('name', '')
    if 'OAuth' in name or 'Auth' in name:
        item['description'] = "Fetch OAuth token for the user."
        folders["1-Auth"].append(item)
    elif 'Create BPA' in name:
        item['description'] = "Create a new Building Plan Application (BPA)."
        folders["2-BPA Create"].append(item)
    elif 'Workflow' in name and 'Search' not in name:
        item['description'] = "Transition workflow state (e.g., INITIATE, APPROVE)."
        folders["3-Workflow Transitions"].append(item)
    elif 'Workflow Search' in name:
        item['description'] = "Search for a workflow instance and its current status."
        folders["4-Workflow Search"].append(item)
    elif 'Verify' in name or 'DCR' in name or 'Scrutiny' in name:
        item['description'] = "Perform Verification."
        folders["5-Verify"].append(item)
    else:
        # Fallback
        folders["2-BPA Create"].append(item)

# Build new items array
new_items = []
for folder_name, folder_items in folders.items():
    if folder_items:
        new_items.append({
            "name": folder_name,
            "item": folder_items,
            "description": f"Requests related to {folder_name}"
        })

col['item'] = new_items
col['info']['description'] = "Automated BPA API tests for DIGIT OSS."

with open('bpa-collection.json', 'w', encoding='utf-8') as f:
    json.dump(col, f, indent=2)

print("Collection formatted.")
