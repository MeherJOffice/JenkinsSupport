import sys
import re
from datetime import date

def update_date(file_path):
    today = date.today().isoformat()
    pattern = r'"2025-\d{2}-\d{2}"'
    replacement = f'"{today}"'

    with open(file_path, 'r') as f:
        content = f.read()

    new_content = re.sub(pattern, replacement, content)

    with open(file_path, 'w') as f:
        f.write(new_content)

    print(f"✔ Updated date in {file_path} to {today}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("❌ Usage: python UpdateScriptDate.py <file_path>")
        sys.exit(1)

    update_date(sys.argv[1])
