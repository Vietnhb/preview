#!/usr/bin/env python3
"""
Close all /* comments with */
"""

import re

with open('database_data_vi.js', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern: Find all phantom table blocks that start with /* but don't end with */
phantom_names = [
    'class_sessions_REMOVED',
    'class_teacher_assignments_REMOVED',
    'parameter_snapshots_REMOVED',
    'topic_module_releases_REMOVED',
    'validation_runs_REMOVED'
]

for name in phantom_names:
    # Find the comment block
    pattern = r'(/\* \{\s*name: "' + re.escape(name) + r'"[\s\S]*?businessRules: \[[^\]]*\]\s*\n\s*\}),(\s*\n)'
    
    def replacer(match):
        return match.group(1) + ' */' + match.group(2)
    
    content, count = re.subn(pattern, replacer, content)
    if count > 0:
        print(f"✅ Closed comment for: {name}")
    else:
        print(f"⚠️  Not found: {name}")

# Write back
with open('database_data_vi.js', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ Done!")

# Verify
import subprocess
result = subprocess.run(['node', '-c', 'database_data_vi.js'], capture_output=True, text=True)
if result.returncode == 0:
    print("✅ Syntax check passed!")
else:
    print("❌ Syntax error:")
    print(result.stderr[:500])
