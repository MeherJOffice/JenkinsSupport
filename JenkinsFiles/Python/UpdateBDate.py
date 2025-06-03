import sys
import re
import os
from datetime import datetime

if len(sys.argv) < 3:
    print("❌ Usage: python3 UpdateBDate.py /path/to/CheckStatus.ts 'YYYY-MM-DD'")
    sys.exit(1)

ts_path = sys.argv[1]
new_date = sys.argv[2]

# Validate date format
try:
    datetime.strptime(new_date, '%Y-%m-%d')
except ValueError:
    print("❌ Invalid date format. Use YYYY-MM-DD")
    sys.exit(1)

if not os.path.isfile(ts_path):
    print(f"❌ File not found at: {ts_path}")
    sys.exit(1)

# Read the file
with open(ts_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace bdate assignment
content, count = re.subn(
    r"(private\s+bdate\s*=\s*')[^']+(';)",
    lambda m: f"{m.group(1)}{new_date}{m.group(2)}",
    content
)

if count == 0:
    print("⚠️ No bdate property found. No changes made.")
else:
    with open(ts_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"✅ bdate updated to {new_date}")
