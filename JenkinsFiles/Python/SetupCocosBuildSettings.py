import os
import json
import sys
import uuid
import re

def find_scene_with_s_suffix(loadscene_path: str) -> str:
    for filename in os.listdir(loadscene_path):
        if filename.endswith('s.fire'):
            return filename
    return None

def extract_uuid_from_meta(meta_path: str) -> str:
    with open(meta_path, 'r', encoding='utf-8') as f:
        meta = json.load(f)
    return meta.get('uuid', '')

def sanitize_product_name(name: str) -> str:
    return re.sub(r'[^A-Za-z0-9]', '', name)

def update_builder_jsons(cocos_path: str, uuid_val: str, bundle_id: str, sanitized_name: str, xxtea_key: str):
    # Settings builder.json
    settings_file = os.path.join(cocos_path, "settings", "builder.json")
    with open(settings_file, 'r+', encoding='utf-8') as f:
        data = json.load(f)
        data["startScene"] = uuid_val
        data["packageName"] = bundle_id
        data["title"] = sanitized_name
        data["xxteaKey"] = xxtea_key
        data["inlineSpriteFrames"] = True
        data["inlineSpriteFrames_native"] = True
        data["md5Cache"] = False
        data["encryptJs"] = True
        data["zipCompressJs"] = True
        data["orientation"] = {
            "landscapeLeft": True,
            "landscapeRight": True,
            "portrait": True,
            "upsideDown": True
        }
        f.seek(0)
        json.dump(data, f, indent=2)
        f.truncate()

    # Local builder.json
    local_file = os.path.join(cocos_path, "local", "builder.json")
    with open(local_file, 'r+', encoding='utf-8') as f:
        data = json.load(f)
        data["actualPlatform"] = "ios"
        data["platform"] = "ios"
        data["buildPath"] = "./build"
        data["debug"] = False
        data["sourceMaps"] = False
        data["template"] = "default"
        f.seek(0)
        json.dump(data, f, indent=2)
        f.truncate()

# --------- ENTRY POINT ---------
if len(sys.argv) < 4:
    print("❌ Usage: python3 SetupCocosBuildSettings.py <project_path> <bundle_id> <product_name>")
    sys.exit(1)

project_path = sys.argv[1]
bundle_id = sys.argv[2]
product_name = sys.argv[3]
xxtea_key = uuid.uuid4().hex[:16]
sanitized_product_name = sanitize_product_name(product_name)

loadscene_path = os.path.join(project_path, "assets", "LoadScene")
scene_file = find_scene_with_s_suffix(loadscene_path)
if not scene_file:
    print("❌ No scene file ending with 's.fire' found.")
    sys.exit(1)

meta_file = os.path.join(loadscene_path, scene_file + ".meta")
if not os.path.isfile(meta_file):
    print("❌ Corresponding .meta file not found for:", scene_file)
    sys.exit(1)

scene_uuid = extract_uuid_from_meta(meta_file)

print(f"📄 Found scene: {scene_file} with UUID: {scene_uuid}")
print(f"🔐 Bundle ID: {bundle_id} | Product Name: {product_name} | Sanitized: {sanitized_product_name} | JS Key: {xxtea_key}")

update_builder_jsons(project_path, scene_uuid, bundle_id, sanitized_product_name, xxtea_key)
print("✅ Builder settings updated successfully.")
