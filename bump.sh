#!/usr/bin/env bash
set -euo pipefail

STEP="${1:-50}"
GRADLE_FILE="app_mojo/build.gradle"

python3 - "$GRADLE_FILE" "$STEP" <<'PY'
import re, sys

path, step = sys.argv[1], int(sys.argv[2])
with open(path, "r", encoding="utf-8") as f:
    src = f.read()

m = re.search(r'(^\s*versionCode\s+)(\d+)', src, flags=re.MULTILINE)
if not m:
    sys.exit(f"❌ versionCode not found in {path}")

old_code = int(m.group(2))
new_code = old_code + step
new_name = f"{new_code / 100:.2f}"

src = re.sub(r'(^\s*versionCode\s+)\d+', rf'\g<1>{new_code}', src, count=1, flags=re.MULTILINE)
src = re.sub(r'(^\s*versionName\s+")[^"]+(")', rf'\g<1>{new_name}\g<2>', src, count=1, flags=re.MULTILINE)

with open(path, "w", encoding="utf-8") as f:
    f.write(src)

print(f"✅ Bumped {path}: {old_code} -> {new_code} (versionName: \"{new_name}\")")
PY
