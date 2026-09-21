# PhysLive: Hoàn thiện Reviewer Console và quy trình kiểm duyệt chuẩn production

## 1. Vai trò và nhiệm vụ

Bạn là Principal Full-stack Engineer kiêm Domain Architect cho PhysLive. Hãy làm việc trực tiếp trên codebase hiện tại trong thư mục `physLive_preview` và hoàn thiện toàn bộ hệ thống Reviewer để đủ chiều sâu học thuật, vận hành được trong production, có bằng chứng kiểm thử và không phá vỡ các luồng đang chạy.

Đây là nhiệm vụ triển khai thực tế, không chỉ phân tích hoặc viết đề xuất. Hãy đọc code hiện có, sửa backend, frontend, migration, test và tài liệu cần thiết; sau đó chạy kiểm chứng toàn bộ.

Không đưa timeline hoặc ước lượng thời gian. Không tạo dữ liệu giả trong production. Không hardcode quyết định reviewer, schema ID hay kết quả đánh giá.

## 2. Bối cảnh codebase hiện tại

Reviewer hiện đã có các phần sau:

- Backend reviewer controllers trong `backend/src/main/java/com/example/backend/controller/reviewer/`.
- `ReviewerService` xử lý ambiguity queue và reviewer decision.
- `BenchmarkReviewService` hỗ trợ tạo benchmark, hai reviewer annotation và reviewer thứ ba adjudication.
- `EvaluationService` chạy comparative evaluation và lưu `evaluation_runs`.
- Schema, solver và module release đã có lifecycle APIs.
- Library moderation đã có danh sách, cập nhật trạng thái và history.
- Frontend Reviewer Console nằm trong `react-client/src/pages/reviewer/ReviewerConsole.tsx` và `react-client/src/components/roles/reviewer/`.
- Các bảng liên quan gồm `ambiguity_cases`, `reviewer_decisions`, `schema_versions`, `solver_versions`, `benchmark_problems`, `gold_annotations`, `benchmark_adjudications`, `evaluation_runs`, `library_items`, `library_moderation_audits`.

Reviewer hiện không chỉ là khai báo, nhưng còn thiếu nhiều yêu cầu production:

- Benchmark chưa có lifecycle quản trị đầy đủ.
- Chưa có claim/assignment rõ ràng cho reviewer queue.
- Chưa có lịch sử và màn hình so sánh các evaluation run.
- Chưa có optimistic locking/idempotency đầy đủ cho thao tác review.
- Chưa trình bày diff, bằng chứng solver và tác động của thay đổi schema đủ sâu.
- Một số chuỗi tiếng Việt frontend đang bị mojibake UTF-8.
- API còn trả trực tiếp entity ở một số nơi và chưa thống nhất pagination/filter/sort.
- Chưa có bộ integration test bao phủ đồng thời, lifecycle bất hợp lệ và phân quyền reviewer.

Hãy giữ lại những phần đúng, mở rộng có kiểm soát và tránh viết lại toàn bộ nếu không cần thiết.

## 3. Mục tiêu sản phẩm

Reviewer Console phải trở thành trung tâm kiểm soát chất lượng cho toàn bộ chuỗi:

```text
User problem
  -> AI extraction
  -> ambiguity review
  -> specification readiness
  -> schema version
  -> numerical solver + closed-form/reference strategy
  -> module release
  -> simulation
  -> benchmark corpus
  -> independent annotation
  -> adjudication
  -> reproducible evaluation
```

Mỗi quyết định reviewer phải trả lời được:

1. Ai đã thực hiện?
2. Thực hiện lúc nào?
3. Đã xem phiên bản nào?
4. Dựa trên bằng chứng nào?
5. Trạng thái trước và sau là gì?
6. Có ảnh hưởng schema, solver, module hoặc simulation nào?
7. Có thể tái lập kết quả đánh giá hay không?

## 4. Nguyên tắc bất biến

- Không cho sửa âm thầm dữ liệu đã approved.
- Schema/solver/module đã approved phải được version hóa; thay đổi nội dung phải tạo version mới.
- Annotation, adjudication, audit và evaluation run là dữ liệu lịch sử. Không hard-delete qua API thông thường.
- Nếu cần hủy, dùng trạng thái `ARCHIVED`, `VOIDED` hoặc tương đương và ghi lý do/audit.
- Hai gold annotation phải đến từ hai reviewer độc lập.
- Reviewer thứ ba adjudicate phải khác cả hai annotator.
- Trước khi annotation thứ hai hoàn tất, không được tiết lộ nội dung annotation của reviewer còn lại.
- Mọi lifecycle transition phải được kiểm tra ở backend; frontend chỉ phản ánh quyền, không phải lớp bảo vệ duy nhất.
- Mọi thao tác ghi phải có transaction, validation, authorization và xử lý conflict rõ ràng.
- Không để LLM tự approve schema, solver, module hoặc benchmark gold.
- Không cho phép reviewer approve chính nội dung họ là tác giả nếu hệ thống có thông tin tác giả.
- Không lưu token, secret hoặc dữ liệu nhạy cảm trong log/audit payload.
- Giữ Flyway là nguồn quản lý schema DB; production dùng `ddl-auto=validate`.

## 5. Hoàn thiện Review Queue cho ambiguity

Nâng cấp queue hiện tại thành workflow production:

### 5.1 Trạng thái và chuyển trạng thái

Hỗ trợ trạng thái rõ ràng, ví dụ:

- `OPEN`
- `CLAIMED`
- `IN_REVIEW`
- `RESOLVED`
- `REJECTED`
- `EXPIRED`

Nếu enum hiện tại khác, tái sử dụng hoặc migration tương thích; không đổi tên phá dữ liệu cũ khi không cần thiết.

Các transition phải có rule và trả `409 Conflict` nếu trạng thái đã thay đổi bởi reviewer khác.

### 5.2 Claim và concurrency

- Reviewer có thể claim/release một item.
- Một item chỉ có tối đa một reviewer đang giữ tại một thời điểm.
- Có `claimedBy`, `claimedAt`, `claimExpiresAt` hoặc mô hình tương đương.
- Dùng optimistic locking (`@Version`) hoặc lock có phạm vi nhỏ để tránh double resolution.
- Hỗ trợ idempotency cho resolve request để retry mạng không tạo hai decision.

### 5.3 Danh sách và bộ lọc

API và UI cần pagination, sort và filter theo:

- status
- topic
- schema ID
- confidence/risk
- assigned reviewer
- created date/age
- language
- unresolved reason/error code

Hiển thị priority/SLA age và các item sắp quá hạn. Không tải toàn bộ queue vào một request.

### 5.4 Màn hình xử lý

Hiển thị song song:

- đề bài gốc
- extraction output
- schema được route
- quantities/units/objects/relations
- ambiguity question và options
- tác động của từng lựa chọn lên specification
- validation errors
- lịch sử quyết định trước đó

Reviewer phải nhập lý do đối với reject, override hoặc lựa chọn ngoài đề xuất AI.

## 6. Schema Version Review có chiều sâu

Hoàn thiện tab schema để reviewer không chỉ đổi lifecycle status.

### 6.1 Lifecycle

Chuẩn hóa transition:

```text
DRAFT -> PENDING_REVIEW -> APPROVED -> RETIRED
                   \-> REJECTED
```

- Không cho `DRAFT -> APPROVED` nếu bỏ qua bằng chứng bắt buộc.
- `APPROVED` phải immutable.
- `RETIRED` phải có replacement schema/version nếu có; không tự động suy diễn replacement.
- Chặn approve nếu schemaId/version/checksum trùng hoặc definition không hợp lệ.

### 6.2 Diff và impact analysis

UI phải hiển thị semantic diff giữa version đang review và version approved gần nhất:

- required/optional quantities
- aliases và units
- adjustable parameters/min/max/step
- output contract
- visualization/scene graph
- numerical solver binding
- reference solver binding
- validation tolerance/checkpoints
- language/metadata thay đổi

Phân loại thay đổi thành breaking/non-breaking và giải thích lý do.

Hiển thị các simulation/library item/assignment đang pin version cũ; không tự đổi historical records sang version mới.

### 6.3 Validation gate

Trước khi approve schema, backend phải kiểm tra:

- JSON/schema contract hợp lệ.
- Không duplicate quantity keys, aliases hoặc series keys.
- Units, ranges, steps và default values hợp lệ.
- Mọi visualization source tồn tại trong output contract.
- Scene graph chỉ dùng primitive/binding được hỗ trợ.
- Có adjustable parameter nếu schema được thiết kế cho tương tác.
- Solver/reference binding tồn tại và đúng lifecycle.
- Checksum được tạo deterministic.

## 7. Solver và Module Approval

Reviewer phải đánh giá solver bằng bằng chứng, không chỉ tên class hoặc status.

### 7.1 Solver version

Hiển thị:

- solver ID/version/implementation key/checksum
- schema versions tương thích
- numerical strategy
- closed-form/reference strategy
- trường hợp chỉ numerical và lý do học thuật
- tolerance
- deterministic test cases
- boundary/invalid input behavior
- performance summary
- module/class thực thi thực tế

Không gọi một solver là closed-form nếu nó chỉ gọi lại numerical solver hoặc dùng cùng implementation path.

### 7.2 Evidence gate

Approve solver chỉ khi:

- implementation tồn tại trong registry/runtime.
- checksum khớp binding hiện hành.
- numerical test pass.
- reference comparison pass khi có closed-form/reference độc lập.
- schema chỉ-numerical có rationale và review tag rõ ràng.
- không có NaN/Infinity ngoài contract.
- kết quả deterministic với cùng input/version.

### 7.3 Module release

Module release chỉ được approve nếu toàn bộ dependency đã approved và tương thích:

- schema version
- numerical solver version
- reference strategy
- scene contract
- validation evidence

Thực hiện release atomically; không tạo trạng thái module approved nhưng dependency chưa approved.

## 8. Benchmark Corpus production-grade

Giữ `BenchmarkProblem` là aggregate root; `GoldAnnotation` và `Adjudication` có thể tiếp tục persist bằng cascade nếu hợp lý. Không cần tạo repository riêng chỉ để gọi CRUD trực tiếp.

### 8.1 Lifecycle benchmark

Hỗ trợ tối thiểu:

- `DRAFT`
- `ANNOTATING`
- `DISAGREEMENT`
- `GOLD_READY`
- `ARCHIVED`

Thêm API/UI cần thiết để:

- tạo benchmark draft
- xem detail
- sửa draft trước annotation đầu tiên
- activate để annotation
- archive có lý do
- lọc theo topic, grade, source, language, status
- pagination/sort
- import/export corpus có validation

Không cho sửa problem text, topic hoặc grade sau khi annotation bắt đầu; nếu cần thay đổi phải clone thành benchmark mới hoặc reset bằng quy trình audit đặc biệt.

### 8.2 Independent annotation

- Đảm bảo hai reviewer khác nhau.
- Không cho người tạo benchmark annotation nếu policy yêu cầu độc lập; cấu hình policy rõ ràng.
- Không lộ annotation còn lại trước khi đủ hai người.
- Annotation phải validate đầy đủ specification contract, schema identity, quantities, units và object relations.
- Lưu schema catalog checksum/prompt version/model version dùng tại thời điểm annotation nếu có liên quan.
- Sau khi submit, annotation immutable; correction phải tạo revision/audit, không overwrite âm thầm.

### 8.3 Adjudication

- Chỉ cho adjudicate khi có đúng hai annotation hợp lệ và chúng bất đồng.
- Reviewer thứ ba phải độc lập.
- UI có structured diff giữa hai annotation.
- Cho chọn từng field từ annotation A/B hoặc nhập resolved value có lý do.
- Lưu rationale, disagreement categories và resolved specification.
- Gold specification phải có provenance rõ ràng: agreement tự động hoặc adjudication.

### 8.4 Không triển khai CRUD nguy hiểm

Không thêm delete trực tiếp cho gold annotation/adjudication chỉ để đủ chữ CRUD. Production reviewer cần lifecycle, immutability và audit hơn CRUD tùy ý.

## 9. Evaluation Runs có thể tái lập

`evaluation_runs` hiện mới chủ yếu lưu kết quả khi chạy. Hoàn thiện thành lịch sử nghiên cứu có thể tái lập.

Mỗi run cần lưu hoặc tham chiếu:

- status: queued/running/completed/failed/cancelled
- evaluation type
- benchmark corpus snapshot/hash
- benchmark count
- extraction provider/model version
- prompt/template version
- schema catalog checksum/version
- configuration/tolerance
- startedAt/completedAt/duration
- metrics JSON
- human-readable report
- failure code/message an toàn
- actor khởi chạy

API cần có:

- chạy evaluation
- list history có pagination/filter
- xem detail
- so sánh hai run
- download JSON/CSV report
- retry failed run bằng config đã pin

Không chạy evaluation dài trong HTTP transaction. Nếu hiện tại chưa có queue infrastructure, tạo job executor có giới hạn, persisted status và polling; không dùng unbounded thread.

Metrics tối thiểu:

- precision, recall, F1
- Cohen's kappa
- numeric agreement
- incorrect simulation rate
- breakdown theo topic, grade, language, schema và quantity type
- disagreement categories
- model/prompt regression so với baseline được chọn

UI phải cho xem trend và regression, không chỉ KPI của lần chạy gần nhất.

## 10. Library Moderation

Hoàn thiện moderation workflow:

- `PENDING -> APPROVED/REJECTED/FEATURED/ARCHIVED` theo rule hiện có.
- Reviewer thấy simulation snapshot, specification, schema/solver version, validation result và nguồn nội dung.
- Reject/return bắt buộc có reason.
- Mọi thay đổi ghi `library_moderation_audits` với actor, old/new status, reason, timestamp.
- Chống double moderation bằng optimistic locking.
- Historical moderation không được sửa/xóa âm thầm.

## 11. Reviewer Dashboard và UX

Giữ style system hiện có, nhưng nâng cấp Reviewer Console thành giao diện vận hành thực tế.

### 11.1 Dashboard

Hiển thị:

- open/claimed/overdue ambiguity count
- pending schema/solver/module count
- benchmarks waiting second annotation
- disagreements waiting adjudication
- failed/regressed evaluation runs
- pending library moderation
- workload theo reviewer

### 11.2 UX bắt buộc

- URL/deep-link cho từng tab và item; reload không mất vị trí.
- Search/filter/sort/pagination server-side.
- Loading/empty/error/retry states đầy đủ.
- Confirm dialog cho lifecycle transition quan trọng.
- Disable action khi request đang chạy; chống double submit.
- Hiển thị conflict khi record đã được reviewer khác thay đổi.
- Không dùng `window.alert` làm UI chính.
- Keyboard navigation, focus management, accessible labels và contrast phù hợp.
- Responsive desktop/tablet; các diff phức tạp ưu tiên desktop nhưng không vỡ layout.
- Sửa toàn bộ mojibake tiếng Việt trong Reviewer Console và component liên quan; đảm bảo source UTF-8 thật, không thay ký tự bằng chuỗi escape sai.

## 12. API contract

Chuẩn hóa API reviewer:

- Không trả trực tiếp JPA entity có lazy relation.
- Dùng request/response DTO ổn định.
- List endpoint trả page object gồm `items`, `page`, `size`, `totalElements`, `totalPages`.
- Filter/sort phải whitelist field; không ghép SQL từ input.
- Dùng error contract thống nhất với `code`, `message`, `fieldErrors`, `traceId` nếu project đã có.
- Trả `400` cho payload invalid, `403` cho thiếu quyền, `404` khi không tồn tại, `409` cho state/concurrency conflict.
- Gắn idempotency key cho action có thể retry nếu phù hợp.
- Mọi timestamp dùng UTC trên wire.

Giữ backward compatibility cho endpoint frontend hiện tại trong giai đoạn chuyển đổi hoặc cập nhật đồng bộ frontend và backend trong cùng thay đổi.

## 13. Security và governance

- Chỉ `ADMIN` và `CONTENT_REVIEWER` truy cập reviewer APIs theo policy hiện có.
- Phân biệt quyền admin override và reviewer thông thường.
- Backend tự lấy actor từ authenticated principal; không nhận reviewer ID tùy ý từ client.
- Ngăn IDOR: mọi detail/action endpoint phải kiểm tra quyền và scope.
- Audit các action: claim, release, resolve, reject, approve, retire, annotate, adjudicate, moderate, run/retry evaluation.
- Không đưa email, token, prompt secret hoặc raw stack trace vào audit response.
- Giới hạn kích thước JSON annotation/schema/evaluation payload.
- Validate JSON depth và collection size để tránh payload gây cạn tài nguyên.
- Rate-limit hoặc concurrency-limit evaluation run.

## 14. Database và migration

Trước khi thêm bảng mới, audit entity và quan hệ hiện có. Ưu tiên mở rộng hợp lý các aggregate hiện tại.

Nếu cần migration:

- Chỉ thêm bằng Flyway migration mới; không sửa migration đã chạy trên cloud.
- Migration phải idempotent trong phạm vi project convention và an toàn với dữ liệu hiện có.
- Thêm `@Version`/version column cho aggregate bị concurrent update.
- Thêm index dựa trên query thực tế, đặc biệt queue status/claimedAt, benchmark status/topic, evaluation createdAt/status và moderation status.
- PostgreSQL không tự tạo index cho foreign key; kiểm tra query plan trước khi thêm.
- Không tạo GIN index cho toàn bộ JSONB nếu không có query cần dùng.
- Với audit/evaluation lớn, chuẩn bị retention/archive policy; chưa partition nếu dữ liệu chưa đủ lớn và chưa có bằng chứng cần thiết.

Không xóa các bảng benchmark/evaluation hiện tại. Chúng là một phần của workflow reviewer.

## 15. Testing bắt buộc

### 15.1 Backend unit/integration tests

Bao phủ:

- role authorization cho mọi reviewer endpoint
- valid/invalid lifecycle transitions
- hai annotator phải khác nhau
- adjudicator phải khác hai annotator
- annotation không bị lộ sớm
- double claim/double resolve concurrency
- optimistic locking conflict
- immutable approved version
- immutable annotation/adjudication
- evaluation không chạy khi chưa có finalized gold corpus
- reproducibility metadata được pin
- pagination/filter/sort
- audit record được tạo đúng actor và old/new state
- malformed/deep/oversized JSON bị từ chối

Ưu tiên integration test với PostgreSQL/Testcontainers khi môi trường hỗ trợ. Nếu Docker không có, unit test phải vẫn chạy và integration test phải skip/fail rõ ràng, không báo pass giả.

### 15.2 Frontend tests

Bao phủ:

- tab routing/deep link
- queue filters và pagination
- claim/conflict UX
- schema/solver diff rendering
- benchmark annotation visibility rules
- adjudication structured diff
- evaluation history/compare
- loading/empty/error/retry
- action disabled khi submitting
- UTF-8 tiếng Việt
- accessibility cơ bản

### 15.3 Regression

Chạy ít nhất:

```bash
cd backend
./mvnw.cmd test

cd ../react-client
npm run check
```

Đảm bảo backend vẫn khởi động được ở port `8080` với cấu hình local hiện tại. Không xóa hoặc ghi đè `.env.local`; chỉ đọc biến cần thiết và không in secret ra log.

## 16. Definition of Done

Chỉ kết luận hoàn thành khi tất cả điều sau đúng:

- Reviewer queue có claim, lifecycle, concurrency protection và audit.
- Schema review có semantic diff, validation gate và immutable approved versions.
- Solver/module approval hiển thị và kiểm tra evidence thật.
- Benchmark hỗ trợ lifecycle, hai annotation độc lập và third-party adjudication.
- Evaluation có persisted history, detail, compare, reproducibility metadata và failure state.
- Library moderation có đầy đủ history và conflict protection.
- Reviewer UI không còn mojibake, có pagination/filter/error states và responsive layout.
- API dùng DTO, validation, authorization và error contract nhất quán.
- Flyway migration chạy sạch trên database mới và database đã có dữ liệu.
- Backend tests, frontend tests, lint và production build đều pass.
- Không phá simulation, schema routing, assignment, school, library hoặc authentication flow.
- Có báo cáo implementation nêu file thay đổi, migration, API contract, test evidence và các giới hạn còn lại.

## 17. Cách thực hiện

1. Audit code hiện tại và lập ma trận feature -> controller -> service -> repository/entity -> UI -> test.
2. Xác định gap thực tế; không tạo lại phần đã có.
3. Thiết kế state machine và API contract trước khi sửa UI.
4. Thực hiện migration/backward compatibility.
5. Hoàn thiện backend domain rules và concurrency.
6. Kết nối frontend bằng DTO/API mới.
7. Bổ sung test theo từng workflow và các case conflict.
8. Chạy toàn bộ regression suite.
9. Khởi động backend port 8080 và smoke test các reviewer endpoint chính.
10. Viết báo cáo production readiness trung thực: phần nào pass, phần nào chưa được kiểm chứng và lý do.

Không dừng ở việc tạo entity, bảng hoặc giao diện tĩnh. Mỗi feature phải nối hoàn chỉnh từ database đến API, domain rule, UI và test.
