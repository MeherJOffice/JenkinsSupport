import os
import sys
import re
from datetime import datetime, timedelta

if len(sys.argv) < 4:
    print("❌ Usage: python3 PreprocessFE2In.py <path_to_FE2In.cs> <unity_override> <is_testing>")
    sys.exit(1)

script_path = sys.argv[1]
unity_override = sys.argv[2]
is_testing = sys.argv[3].lower() == 'true'

if not os.path.isfile(script_path):
    print(f"❌ File not found: {script_path}")
    sys.exit(1)

# Determine the new date
date_obj = datetime.today() - timedelta(days=30) if is_testing else datetime.today()
new_date = date_obj.strftime("%Y-%m-%d")

with open(script_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(
    r'(private\s+string\s+bdate\s*=\s*")[^"]*(")',
    lambda m: f'{m.group(1)}{new_date}{m.group(2)}',
    content
)

# Replace the cfUrls list
content = re.sub(
    r'(private\s+List<string>\s+cfUrls\s*=\s*new\s+List<string>\s*\(\)\s*{)[^}]+(};)',
    f"\\1\n        {unity_override}    \\2",
    content,
    flags=re.DOTALL
)

with open(script_path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"✅ FE2In.cs updated successfully with bdate: {new_date} and cfUrls: {unity_override}")
