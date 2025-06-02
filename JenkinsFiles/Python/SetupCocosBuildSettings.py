import os
import json
import sys
import uuid

def update_builder_settings(project_path: str):
    xxtea_key = uuid.uuid4().hex[:16]

    # 🔧 Update settings/builder.json
    settings_path = os.path.join(project_path, "settings", "builder.json")
    with open(settings_path, 'r+', encoding='utf-8') as f:
        data = json.load(f)
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
        print("✅ Updated: settings/builder.json")

    # 🔧 Update local/builder.json
    local_path = os.path.join(project_path, "local", "builder.json")
    with open(local_path, 'r+', encoding='utf-8') as f:
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
        print("✅ Updated: local/builder.json")

# --------- ENTRY POINT ---------
if len(sys.argv) < 2:
    print("❌ Usage: python3 ConfigureBuilderSettings.py <cocos_project_path>")
    sys.exit(1)

project_path = sys.argv[1]
update_builder_settings(project_path)
