# Reviewer production implementation report

Ngày kiểm chứng: 2026-09-22

## Kết quả đã triển khai

Reviewer đã được nâng từ các endpoint thao tác cơ bản lên workflow có state và provenance ở backend:

| Khu vực | Đã có |
|---|---|
| Benchmark | `DRAFT -> ANNOTATING -> GOLD_READY/DISAGREEMENT -> ARCHIVED`, activate/archive, sửa draft trước annotation, optimistic version |
| Annotation | Hai reviewer độc lập, không lộ annotation còn lại trước khi đủ hai bản, giới hạn kích thước/duplicate quantity, lưu catalog/prompt/model metadata |
| Adjudication | Reviewer thứ ba khác hai annotator, bắt buộc rationale, lưu disagreement categories |
| Ambiguity queue | Claim/release 30 phút, lock khi claim/resolve, expiry, endpoint page/filter topic, optimistic version |
| Evaluation | Lưu status, actor, thời gian, duration, benchmark snapshot hash, configuration; có history/detail/compare endpoint |
| Schema/Solver | Version locking, schema approve chạy validation + approved solver binding, solver binding checksum, chống sửa version đã publish |
| Library moderation | Lock khi moderation, reject/remove bắt buộc reason, `409` cho optimistic conflict |
| Frontend | Reviewer Console tiếng Việt UTF-8, activate/claim/release/adjudication rationale, evaluation history, solver/schema evidence view |

## Migration

- `V21__harden_reviewer_workflows.sql`: state benchmark, claim ambiguity, evaluation metadata.
- `V22__add_reviewer_record_versions.sql`: optimistic-lock columns cho solver/schema/library.
- `V23__add_reviewer_provenance.sql`: provenance cho annotation và rationale adjudication.

Các migration mới đều additive; không sửa migration lịch sử đã chạy trên cloud.

## API mới/chính

- `GET /api/reviewer/ambiguities/page`
- `POST /api/reviewer/ambiguities/{id}/claim`
- `POST /api/reviewer/ambiguities/{id}/release`
- `GET /api/reviewer/benchmarks/page`
- `PUT /api/reviewer/benchmarks/{id}` cho draft
- `POST /api/reviewer/benchmarks/{id}/activate`
- `POST /api/reviewer/benchmarks/{id}/archive`
- `GET /api/evaluations/history`
- `GET /api/evaluations/{id}`
- `GET /api/evaluations/compare?baseline=...&candidate=...`

Các endpoint list cũ vẫn được giữ để không phá frontend/consumer hiện tại.

## Bằng chứng kiểm thử

- Backend: `./mvnw.cmd -q test` pass. Testcontainers integration test được cấu hình skip khi Docker không khả dụng; log không được coi là integration pass giả.
- Frontend: `npm run check` pass: architecture, scene contracts với 182 schema và 0 lỗi, lint, 29 test, TypeScript build và Vite production build.
- Runtime: Flyway validate thành công 24 migration, database cloud báo schema version 23 và `Schema "public" is up to date`. Backend đã restart thành công và listen trên port `8080`.

## Giới hạn còn lại cần hoàn thiện trước production sign-off

1. Evaluation vẫn chạy đồng bộ trong HTTP request; cần chuyển sang bounded job executor/polling nếu benchmark lớn.
2. Semantic diff/impact analysis của schema mới đang có evidence view, chưa có renderer diff field-level đầy đủ.
3. Library và ambiguity đã có page endpoint backend, nhưng UI hiện vẫn gọi endpoint list tương thích cũ.
4. Integration test PostgreSQL thực tế chưa chạy trong môi trường hiện tại vì Docker daemon không khả dụng; cần chạy lại khi CI có PostgreSQL/Testcontainers.
5. Cần smoke test các endpoint ghi dữ liệu với tài khoản reviewer thật trước khi gọi production-ready; chưa tự tạo quyết định reviewer trong production.
