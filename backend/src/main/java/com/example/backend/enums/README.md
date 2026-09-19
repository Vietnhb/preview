# Enums Package

Thư mục này chứa tất cả các **Java Enums** được sử dụng trong hệ thống PhysLive.

## 📋 Danh Sách Enums (17)

### 🔐 Status Enums (8)
| Enum | Mô Tả | Values |
|------|-------|--------|
| **AmbiguityStatus** | Trạng thái ambiguity case | OPEN, RESOLVED, ESCALATED |
| **AssignmentStatus** | Trạng thái assignment | ACTIVE, CLOSED |
| **ExtractionRunStatus** | Trạng thái extraction run | RUNNING, SUCCEEDED, FAILED |
| **GradingStatus** | Trạng thái chấm bài | PENDING, AI_GRADED, TEACHER_CONFIRMED |
| **LibraryModerationStatus** | Trạng thái review library | APPROVED, PENDING, REJECTED, FEATURED, REMOVED |
| **LifecycleStatus** | Trạng thái schema/solver version | DRAFT, APPROVED, PUBLISHED, DEPRECATED |
| **SimulationStatus** | Trạng thái simulation | VALIDATING, READY, ARCHIVED, FAILED |
| **SupportStatus** | Trạng thái support ticket | OPEN, READ, IN_PROGRESS, RESOLVED, CLOSED |

### 📊 Type & Classification Enums (6)
| Enum | Mô Tả | Values |
|------|-------|--------|
| **AssetType** | Loại asset | TEXT, IMAGE |
| **ConfirmationState** | Trạng thái confirmation | NO_AMBIGUITY, UNRESOLVED, CONFIRMED, REJECTED |
| **ExtractionOutcome** | Kết quả extraction | API_SUCCESS, RULE_BASED_FALLBACK, FAILED |
| **ExtractionPath** | Phương pháp extraction | OPENROUTER, RULE_BASED |
| **OcrStatus** | Trạng thái OCR | NOT_REQUESTED, SUCCEEDED, FAILED |
| **SourceMode** | Nguồn input | TEXT, PASTE, IMAGE |

### 🎯 Business Logic Enums (3)
| Enum | Mô Tả | Values |
|------|-------|--------|
| **SubmissionStatus** | Trạng thái problem submission | DRAFT, OCR_PREVIEW_READY, EXTRACTION_READY, EXTRACTED, VALIDATED, PUBLISHED |
| **SupportKind** | Loại support | FEEDBACK, MESSAGE |
| **Visibility** | Phạm vi chia sẻ | PERSONAL, SHARED |

---

## ✅ Best Practices

### 1. **Đặt Tên Enum**
```java
// ✅ GOOD: Noun + Status/Type/Mode
public enum SimulationStatus { ... }
public enum AssetType { ... }
public enum SourceMode { ... }

// ❌ BAD: Động từ hoặc không rõ nghĩa
public enum Processing { ... }
public enum Data { ... }
```

### 2. **Convention**
- **Package**: `com.example.backend.enums`
- **File name**: PascalCase (VD: `SimulationStatus.java`)
- **Values**: UPPER_SNAKE_CASE (VD: `IN_PROGRESS`, `NOT_SUBMITTED`)

### 3. **Import**
```java
// ✅ GOOD: Import từ enums package
import com.example.backend.enums.SimulationStatus;

// ❌ BAD: Import từ entity package (đã refactor)
import com.example.backend.entity.SimulationStatus;
```

### 4. **Sử Dụng trong Entity**
```java
@Entity
public class Simulation {
    @Enumerated(EnumType.STRING)
    private SimulationStatus status;  // Store as VARCHAR, not INT
}
```

### 5. **Enum với Methods**
Nếu cần logic phức tạp, có thể thêm methods:
```java
public enum LifecycleStatus {
    DRAFT, APPROVED, PUBLISHED, DEPRECATED;
    
    public boolean isEditable() {
        return this == DRAFT;
    }
    
    public boolean isPublic() {
        return this == PUBLISHED || this == DEPRECATED;
    }
}
```

---

## 🔄 Migration History

**Date**: 2026-09-19  
**Action**: Di chuyển tất cả enums từ `entity/` sang `enums/`  
**Reason**: Tách biệt enums khỏi entities để:
- Dễ quản lý và tìm kiếm
- Tránh circular dependencies
- Follow Java best practices
- Cải thiện tổ chức code

**Files affected**: 17 enums + 17 imports updated

---

## 📚 Related Documentation
- [Entity Package](../entity/README.md)
- [DTO Package](../dto/README.md)
- [Service Layer](../service/README.md)
