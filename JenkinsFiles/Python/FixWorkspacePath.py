import sys
import xml.etree.ElementTree as ET

xcworkspace_file = sys.argv[1]

tree = ET.parse(xcworkspace_file)
root = tree.getroot()

for fileref in root.findall(".//FileRef"):
    loc = fileref.get("location")
    if loc and loc.startswith("absolute:"):
        if "/UnityBuild/" in loc:
            new_loc = "container:../" + loc.split("/UnityBuild/", 1)[1]
            fileref.set("location", new_loc)
        elif "/CocosBuild/" in loc:
            new_loc = "container:../" + loc.split("/CocosBuild/", 1)[1]
            fileref.set("location", new_loc)

tree.write(xcworkspace_file, encoding="UTF-8", xml_declaration=True)
