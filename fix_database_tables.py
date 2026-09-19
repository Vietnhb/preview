#!/usr/bin/env python3
"""
Script để sửa database_data_vi.js: Xóa phantom tables và fix tên bảng
"""

import re

# Đọc file
with open('database_data_vi.js', 'r', encoding='utf-8') as f:
    content = f.read()

# FIX 1: Đổi tên adjudications → benchmark_adjudications
print("FIX 1: Đổi tên adjudications → benchmark_adjudications...")
content = re.sub(
    r'name: "adjudications",\s*\n\s*dbName: "adjudications"',
    'name: "benchmark_adjudications",\n        dbName: "benchmark_adjudications"',
    content
)

# FIX 2: Xóa 5 phantom tables (tìm và xóa toàn bộ object {...})
phantom_tables = [
    'class_sessions',
    'class_teacher_assignments', 
    'parameter_snapshots',
    'topic_module_releases',
    'validation_runs'
]

for table_name in phantom_tables:
    print(f"Đang xóa phantom table: {table_name}...")
    
    # Pattern: Tìm object bắt đầu với name: "table_name"
    # Và kết thúc ở dấu },\n    { hoặc },\n];
    pattern = r',?\s*\{\s*name:\s*"' + table_name + r'",[\s\S]*?(\},\s*\{|\}\s*\];)'
    
    def replace_func(match):
        # Nếu match kết thúc bằng }, { thì giữ lại }, {
        # Nếu kết thúc bằng }]; thì giữ lại ];
        ending = match.group(1)
        if ending.strip() == '];':
            return '];'
        else:
            return ','  # Giữ dấu phẩy giữa các objects
    
    content = re.sub(pattern, replace_func, content)

# FIX 3: Update stats comment
print("Updating stats...")
content = content.replace(
    '// Data cho 38 bảng',
    '// Data cho 33 bảng'
)

# Ghi lại file
with open('database_data_vi.js', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ Done! Đã sửa database_data_vi.js")
print("- Đổi tên: adjudications → benchmark_adjudications")
print("- Xóa 5 phantom tables:")
for table in phantom_tables:
    print(f"  ❌ {table}")
