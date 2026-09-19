#!/usr/bin/env python3
"""
Simple approach: Đọc file, tách thành list, filter, join lại
"""

import re

print("Reading database_data_vi.js...")
with open('database_data_vi.js', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# FIX 1: Update header
for i, line in enumerate(lines):
    if '// Data cho 38 bảng' in line:
        lines[i] = line.replace('38 bảng', '33 bảng (match với Backend Entities)')
        print(f"✅ Updated header at line {i+1}")
    
    # FIX 2: Rename adjudications → benchmark_adjudications
    if 'name: "adjudications"' in line or 'dbName: "adjudications"' in line:
        lines[i] = line.replace('adjudications', 'benchmark_adjudications')
        print(f"✅ Renamed adjudications at line {i+1}")

# Write back
with open('database_data_vi.js', 'w', encoding='utf-8') as f:
    f.writelines(lines)

print("\n✅ Phase 1 done!")
print("\n⚠️  Xóa 5 phantom tables THUI CÔNG:")
print("   - class_sessions")
print("   - class_teacher_assignments")
print("   - parameter_snapshots")
print("   - topic_module_releases")
print("   - validation_runs")
print("\nBạn cần xóa thủ công trong editor hoặc tôi sẽ xóa bằng str_replace")
