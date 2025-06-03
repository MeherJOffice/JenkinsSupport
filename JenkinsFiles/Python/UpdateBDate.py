import sys
import re
import os
from datetime import datetime, timedelta

if len(sys.argv) < 3:
    print("❌ Usage: python3 UpdateBDate.py /path/to/CheckStatus.ts isTesting")
    sys.exit(1)

ts_path = sys.argv[1]
is_testing_flag = sys.argv[2].lower() == 'true'

# Calculate the date
if is_testing_flag:
    new_date = (datetime.today() - timedelta(days=7)).strftime('%Y-%m-%d')
else:
    new_date = datetime.today().strftime('%Y-%m-%d')

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
