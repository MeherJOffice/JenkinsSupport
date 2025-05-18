import os
import sys
from xml.etree import ElementTree as ET

# --------- CONFIG ---------
if len(sys.argv) < 3:
    print("\u274c Usage: python3 SetupXcodeWorkspace.py /path/to/unity.xcodeproj /path/to/cocos.xcodeproj")
    sys.exit(1)

unity_xcodeproj = sys.argv[1]
cocos_xcodeproj = sys.argv[2]

if not os.path.isdir(unity_xcodeproj):
    print(f"\u274c Unity pbxproj not found: {unity_xcodeproj}")
    sys.exit(1)

if not os.path.isdir(cocos_xcodeproj):
    print(f"\u274c Cocos pbxproj not found: {cocos_xcodeproj}")
    sys.exit(1)

# Extract sanitized workspace name from unity xcodeproj folder
unity_project_folder = os.path.dirname(unity_xcodeproj)
project_root = os.path.dirname(unity_project_folder)

unity_project_Name = os.path.dirname(os.path.dirname(unity_xcodeproj))
product_name = os.path.basename(unity_project_Name)
sanitized_name = ''.join(e for e in product_name if e.isalnum())

workspace_folder = os.path.join(project_root, "XcodeWorkspace")
workspace_name = f"{sanitized_name}WS"
workspace_file = os.path.join(workspace_folder, f"{workspace_name}.xcworkspace", "contents.xcworkspacedata")

print(f"\U0001F6E0\uFE0F Creating workspace at: {workspace_file}")

# --------- CREATE WORKSPACE FOLDER STRUCTURE ---------
os.makedirs(os.path.dirname(workspace_file), exist_ok=True)

# --------- GENERATE WORKSPACE XML ---------
workspace_xml = ET.Element('Workspace', version="1.0")
ET.SubElement(workspace_xml, 'FileRef', location=f"absolute:{os.path.abspath(unity_xcodeproj)}")
ET.SubElement(workspace_xml, 'FileRef', location=f"absolute:{os.path.abspath(cocos_xcodeproj)}")

# --------- WRITE TO FILE ---------
tree = ET.ElementTree(workspace_xml)
ET.indent(tree, space="   ", level=0)
with open(workspace_file, 'wb') as f:
    tree.write(f, encoding='utf-8', xml_declaration=True)

print("\u2705 Workspace created with Unity and Cocos projects included.")
