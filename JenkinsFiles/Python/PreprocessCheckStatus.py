import sys
import re
import os

if len(sys.argv) < 3:
    print("❌ Usage: python3 PreprocessCheckStatus.py /path/to/CheckStatus.ts override_link testing_flag")
    sys.exit(1)

ts_path = sys.argv[1]
override_block = sys.argv[2]  # This should be the full block like: [ "45", "52", "Test" ]

if not os.path.isfile(ts_path):
    print(f"❌ CheckStatus.ts not found at {ts_path}")
    sys.exit(1)

# Read the original file
with open(ts_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Rebuild and inject the replacement block
new_block = f'public static LIST_LINK_CONFIG = {override_block};'

# Replace the entire block
content, count = re.subn(
    r'public static LIST_LINK_CONFIG\s*=\s*\[[\s\S]*?\];',
    new_block,
    content
)

if count == 0:
    print("⚠️ LIST_LINK_CONFIG not found. No changes made.")
else:
    # Write back to file
    with open(ts_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("✅ LIST_LINK_CONFIG replaced successfully.")
