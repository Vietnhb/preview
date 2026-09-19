# Entity enums

Package: `com.example.backend.entity.enums`

Thư mục này chứa các kiểu enum được lưu trong entity hoặc dùng trực tiếp trong vòng đời dữ liệu. Enum được tách khỏi class entity để thư mục `entity` không bị phẳng, nhưng vẫn nằm trong cùng bounded package.

## Nhóm enum

- Vòng đời xử lý: `AmbiguityStatus`, `AssignmentStatus`, `ExtractionRunStatus`, `GradingStatus`, `LibraryModerationStatus`, `LifecycleStatus`, `SimulationStatus`, `SubmissionStatus`, `SupportStatus`.
- Phân loại dữ liệu: `AssetType`, `ConfirmationState`, `OcrStatus`, `RoleName`, `SourceMode`, `SupportKind`, `Visibility`.
- Audit extraction: `ExtractionOutcome`, `ExtractionPath`.

`RULE_BASED` và `RULE_BASED_FALLBACK` chỉ được giữ để đọc dữ liệu audit cũ. Luồng production hiện tại không còn tạo extraction rule-based.

## Quy ước

- Entity lưu enum bằng `@Enumerated(EnumType.STRING)`, không lưu ordinal.
- Không đổi tên hoặc xóa giá trị đã được lưu trong database nếu chưa có Flyway migration tương ứng.
- Không đặt logic service, truy vấn repository hoặc cấu hình môi trường trong enum.
- Enum không gắn với persistence phải đặt gần domain sử dụng, không tự động đưa vào package này.
