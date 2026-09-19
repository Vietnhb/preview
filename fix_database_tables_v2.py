#!/usr/bin/env python3
"""
Script v2 để sửa database_data_vi.js: Xóa phantom tables và fix tên bảng
Sử dụng JSON parser để tránh regex phức tạp
"""

import re
import json

print("Reading database_data_vi.js...")
with open('database_data_vi.js', 'r', encoding='utf-8') as f:
    content = f.read()

# FIX 1: Đổi tên adjudications → benchmark_adjudications
print("\nFIX 1: Đổi tên adjudications → benchmark_adjudications...")
content = content.replace(
    'name: "adjudications",\n        dbName: "adjudications"',
    'name: "benchmark_adjudications",\n        dbName: "benchmark_adjudications"'
)

# FIX 2: Xóa phantom tables bằng cách đếm dấu { } matching
phantom_tables = [
    'class_sessions',
    'class_teacher_assignments', 
    'parameter_snapshots',
    'topic_module_releases',
    'validation_runs'
]

for table_name in phantom_tables:
    print(f"\nĐang xóa phantom table: {table_name}...")
    
    # Tìm vị trí bắt đầu của table object
    pattern_start = r'\{\s*\n\s*name:\s*"' + re.escape(table_name) + r'"'
    match = re.search(pattern_start, content)
    
    if not match:
        print(f"  ⚠️  Không tìm thấy: {table_name}")
        continue
    
    start_pos = match.start()
    
    # Đếm dấu { } để tìm vị trí kết thúc
    bracket_count = 0
    in_string = False
    in_template_literal = False
    escape_next = False
    i = start_pos
    
    while i < len(content):
        char = content[i]
        
        # Handle escape sequences
        if escape_next:
            escape_next = False
            i += 1
            continue
        
        if char == '\\':
            escape_next = True
            i += 1
            continue
        
        # Handle template literals (backticks)
        if char == '`' and not in_string:
            in_template_literal = not in_template_literal
            i += 1
            continue
        
        # Handle regular strings
        if char == '"' and not in_template_literal:
            in_string = not in_string
            i += 1
            continue
        
        # Count brackets only outside strings
        if not in_string and not in_template_literal:
            if char == '{':
                bracket_count += 1
            elif char == '}':
                bracket_count -= 1
                if bracket_count == 0:
                    # Found closing bracket
                    end_pos = i + 1
                    
                    # Check if there's a comma after }
                    j = i + 1
                    while j < len(content) and content[j] in [' ', '\n', '\t']:
                        j += 1
                    
                    if j < len(content) and content[j] == ',':
                        end_pos = j + 1
                    
                    # Delete the entire object
                    before = content[:start_pos]
                    after = content[end_pos:]
                    
                    # Remove leading comma if exists
                    before = before.rstrip()
                    if before.endswith(','):
                        before = before[:-1]
                    
                    content = before + '\n    ' + after.lstrip()
                    print(f"  ✅ Đã xóa: {table_name}")
                    break
        
        i += 1

# FIX 3: Clean up extra commas
print("\nCleaning up extra commas...")
# Remove double commas
content = re.sub(r',\s*,', ',', content)
# Remove comma before ]
content = re.sub(r',(\s*\])', r'\1', content)

# FIX 4: Update comment
print("\nUpdating header comment...")
content = content.replace(
    '// Data cho 38 bảng database',
    '// Data cho 33 bảng database (match với Backend Entities)'
)

# Write back
print("\nWriting back to file...")
with open('database_data_vi.js', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ Done! Đã sửa database_data_vi.js")
print("\nVerifying with Node.js syntax check...")
import subprocess
result = subprocess.run(['node', '-c', 'database_data_vi.js'], capture_output=True, text=True)
if result.returncode == 0:
    print("✅ Syntax check passed!")
else:
    print("❌ Syntax error:")
    print(result.stderr)
