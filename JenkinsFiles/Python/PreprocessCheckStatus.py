import sys
import re
import datetime
import os

if len(sys.argv) < 4:
    print("❌ Usage: python3 PreprocessCheckStatus.py /path/to/CheckStatus.ts override_link testing_flag")
    sys.exit(1)

ts_path = sys.argv[1]
override_link = sys.argv[2]
testing_flag = sys.argv[3].lower() == 'true'

if not os.path.isfile(ts_path):
    print(f"❌ CheckStatus.ts not found at {ts_path}")
    sys.exit(1)

# Determine the new date
if testing_flag:
    new_date = (datetime.datetime.now() - datetime.timedelta(days=30)).strftime('%Y-%m-%d')
else:
    new_date = datetime.datetime.now().strftime('%Y-%m-%d')

# Read original file
with open(ts_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix LINK_CONFIG
content = re.sub(
    r'(public static LINK_CONFIG\s*=\s*")[^"]+(";)',
    lambda m: f'{m.group(1)}{override_link}{m.group(2)}',
    content
)

# Fix bdate
content = re.sub(
    r"(private\s+bdate\s*=\s*')[^']+(';)",
    lambda m: f'{m.group(1)}{new_date}{m.group(2)}',
    content
)


# Write back to file
with open(ts_path, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"✅ CheckStatus.ts updated with LINK_CONFIG='{override_link}' and bdate='{new_date}'")
