# Prompt triển khai PhysLive: mô phỏng Vật lí THPT Việt Nam

> Tài liệu giao việc cho coding agent và nhóm phát triển. Đây là yêu cầu triển khai và cổng nghiệm thu, không phải tuyên bố sản phẩm hiện tại đã bao phủ 100%.
> Mọi nhận định về cấu trúc repo phải được kiểm tra lại trước khi sửa.

## 1. Vai trò và mục tiêu

Bạn là kỹ sư trưởng triển khai PhysLive trên codebase hiện có. Hãy mở rộng hệ thống để trực quan hóa đầy đủ nội dung Vật lí THPT Việt Nam lớp 10, 11, 12, gồm nội dung cốt lõi, thực hành và chuyên đề học tập trong phạm vi đã kiểm kê. Sản phẩm phục vụ giáo viên tạo hoạt động, giao theo lớp, học sinh thí nghiệm và giáo viên đánh giá.

Mục tiêu triển khai là bao phủ 100% yêu cầu cần đạt và các hiện tượng/thí nghiệm/bài mô phỏng trong các bộ SGK thuộc phạm vi. Thực thi từ một prompt giao việc xuyên suốt, không đặt lịch theo ngày/tuần/tháng, không tự cắt phạm vi thành một bản demo. Tiến hành liên tục theo các cổng nghiệm thu và xử lý lỗi phát hiện được. Nếu thiếu nguồn, quyền truy cập hoặc đánh giá chuyên môn thực tế, báo chính xác phần chưa xác minh; không công bố 100% khi chưa có bằng chứng.

Ưu tiên: đúng học thuật → tương thích dữ liệu và phân quyền → hoàn chỉnh luồng học tập → rõ ràng trực quan → hiệu năng → trang trí. Chất lượng giao diện là yêu cầu bắt buộc, được nghiệm thu bằng sử dụng thực tế và ảnh/video, không chỉ bằng build thành công.

Trong lần thực thi prompt này, hoàn thành kiểm kê, triển khai một lát cắt dọc về sóng để kiểm chứng kiến trúc, rồi tiếp tục các phần còn lại của ma trận. Pilot là cổng kiểm chứng đầu tiên, không phải điểm dừng. Không viết lại toàn bộ ứng dụng cùng lúc.

Định hướng đồ họa đã chốt: **2D làm nền tảng; 2.5D cho các cảnh cần chiều sâu trực quan**. Hai chế độ dùng chung dữ liệu vật lý và hoạt động học tập. Ưu tiên Canvas 2D và asset vector/sprite hiện có; không mở rộng phạm vi sang hệ thống 3D tự do.

## 2. Quy tắc xác định “100%”

1. Thu thập chương trình môn Vật lí chính thức và các sửa đổi có hiệu lực tại thời điểm triển khai. Chương trình tổng thể, tài liệu thi và tài liệu GDTX không thay thế chương trình môn học THPT.
2. Đối chiếu riêng từng bộ SGK được phê duyệt đang nằm trong phạm vi sử dụng: khởi đầu khảo sát Kết nối tri thức với cuộc sống, Chân trời sáng tạo, Cánh Diều; xác minh danh sách, phiên bản, năm xuất bản và sách chuyên đề. Không mặc định danh sách này là toàn bộ mãi mãi.
3. Lập danh mục đến từng yêu cầu cần đạt, hiện tượng, thí nghiệm và dạng nhiệm vụ; không chỉ đến tên chương. Ghi nguồn, trang/mục, phiên bản, ngày kiểm tra, người kiểm tra. Dùng tài liệu được phép truy cập, không chép nguyên sách vào sản phẩm.
4. Phân biệt nội dung cần mô phỏng định lượng, trực quan hóa khái niệm, thực hành đo lường, phân tích dữ liệu và kiến thức an toàn/phương pháp. Không gắn một animation vào mọi mục rồi đánh dấu đã hỗ trợ.
5. Báo hai chỉ số riêng: độ phủ yêu cầu chương trình và độ phủ danh mục mô phỏng/thí nghiệm SGK. Một template có thể đáp ứng nhiều mục nhưng từng ánh xạ phải có nhiệm vụ và bằng chứng nghiệm thu.
6. Mục chưa có nguồn được gắn `unverified`, chưa có implementation là `missing`, có prototype là `implemented`, qua kiểm thử là `tested`, được chuyên gia/giáo viên duyệt là `approved`. Chỉ `approved` được tính vào độ phủ phát hành.
7. 100% = số mục được approved / tổng số mục trong danh mục nguồn đã đóng phiên bản. Báo riêng theo lớp, bộ sách, chuyên đề và loại hoạt động. Không bỏ mục khó khỏi mẫu số. Báo số mục chưa xác minh bên cạnh tỷ lệ; mẫu số chưa xác minh hoàn chỉnh thì không được tuyên bố 100%.
8. Nội dung chuyên sâu ngoài chương trình chuẩn phải có phạm vi riêng; không trộn chuẩn 2006, 2018, GDTX và chương trình chuyên.

Đầu ra bắt buộc ở giai đoạn kiểm kê:

- `docs/curriculum/sources.md`: danh mục nguồn và tình trạng truy cập/xác minh.
- `docs/curriculum/coverage.csv`: `outcome_id,grade,scope,textbook,edition,chapter,lesson,source_url,source_locator,requirement_summary,representation_type,model_ids,template_ids,activity_ids,test_ids,reviewer,status,gap`.
- `docs/curriculum/gaps.md`: mọi mục thiếu, nguồn thiếu và ước lượng công việc.
- Script kiểm tra ID trùng, mapping mồ côi, trường bắt buộc thiếu và xuất báo cáo coverage. Tỷ lệ phải tính từ dữ liệu, không viết tay.

Nguồn khởi đầu để truy xuất và xác minh, không phải danh sách đủ để chứng nhận:

- Văn bản Thông tư 32/2018 trên cơ sở dữ liệu Bộ GDĐT: https://vbpl.moj.gov.vn/bogiaoducdaotao/Pages/vbpq-toanvan.aspx?ItemID=146721
- Chương trình tổng thể trên website Bộ, chỉ dùng làm bối cảnh: https://moet.gov.vn/content/vanban/Lists/VBPQ/Attachments/1483/vbhn-chuong-trinh-tong-the.pdf
- Tìm phụ lục chương trình môn Vật lí và danh mục SGK/phụ lục sửa đổi từ nguồn cơ quan ban hành, nhà xuất bản. Nếu chưa lấy được, ghi nguồn thiếu; không bịa mục lục hoặc số trang.

## 3. Hiện trạng cần kế thừa

Các đường dẫn sau tính từ root `physLive_preview/`:

| Thành phần | Đường dẫn và trách nhiệm hiện có |
|---|---|
| Trích xuất đề | `backend/src/main/java/com/example/backend/service/problem/ProblemService.java`, `backend/src/main/java/com/example/backend/ai/extraction/`: AI trích dữ kiện và schema |
| Hợp đồng/độ sẵn sàng | `backend/src/main/java/com/example/backend/service/problem/SchemaDefinitionService.java`, `SpecificationReadinessService.java` |
| Catalog vật lý | `backend/src/main/resources/schemas/catalog.json`: hiện quan sát được 7 schema, gồm chuyển động 1D, ném, lực, va chạm đàn hồi, lò xo, RC nạp/xả |
| Catalog giáo trình | `backend/src/main/resources/curriculum/catalog.json`: dữ liệu khởi đầu, phải audit lại lớp và phạm vi, không phải nguồn học thuật |
| Tính và kiểm định | `backend/src/main/java/com/example/backend/service/simulation/SimulationService.java`, `PhysicsValidationService.java`; `backend/src/main/java/com/example/backend/physics/solver/`, `reference/`, `validation/` |
| DTO | `backend/src/main/java/com/example/backend/dto/simulation/SimulationResponse.java`; `react-client/src/api/simulationApi.ts`; `react-client/src/types/physlive.ts` |
| Scene | `react-client/src/simulation-scene/SceneGraph.ts`, `SceneCompiler.ts`, `BindingResolver.ts` |
| Runtime | `react-client/src/simulation-runtime/SimulationData.ts`, `SimulationRuntime.ts`, `interpolate.ts` |
| Vẽ | `react-client/src/simulation-renderer/CanvasRenderer.ts`, `PrimitiveRendererRegistry.ts`, `quality.ts` |
| Render | Renderer procedural ở `react-client/src/simulation-renderer/` và scene compiler; không dùng asset catalog cố định |
| Tương thích | `react-client/src/components/simulation-canvas/model.ts`: presentation mặc định cho mô phỏng cũ |
| Giao diện dùng chung | `react-client/src/components/simulation-canvas/CanvasPhysicsScene.tsx`, `components/simulation/`, `components/workspace/LearningWorkspace.tsx` |
| Bài tập | `AssignmentService`, các trang teacher/student, API assignment; snapshot lần chạy, giao lớp, dự đoán/nộp/chấm phải được bảo toàn |

Hiện có đường đọc sceneGraph/spec ở frontend nhưng không được suy ra rằng AI/backend đã tạo và lưu đầy đủ sceneGraph cho mọi mô phỏng. Kiểm tra toàn tuyến serialize → persist → API → normalize → compile → replay.

Timeseries `time`, `positions`, `velocities`, `accelerations`, `values` chưa đủ biểu diễn tổng quát trường sóng theo không gian. Không tạo hàng nghìn actor hoặc nhồi lưới không gian thành vô số series để né thiết kế hợp đồng.

## 4. Nguyên tắc cập nhật an toàn

- Đọc AGENTS.md nếu có; xem git status/diff; giữ nguyên thay đổi người dùng. Ghi baseline test và lỗi có sẵn trước khi sửa.
- Không thay framework, viết lại ứng dụng, đổi route hoặc đổi toàn bộ tên DTO chỉ để làm kiến trúc đẹp hơn.
- Chia thành các thay đổi nhỏ có thể kiểm chứng. Dùng adapter cho payload cũ, registry cho capability mới, feature flag nếu cần triển khai từng phần.
- Không chỉnh checksum migration đã chạy. Dùng migration tăng thêm, backfill có điều kiện và kiểm thử trên bản sao dữ liệu; không tự sửa database production.
- Pin model, solver, schema, template, scene contract, asset manifest và input snapshot theo từng simulation run. Replay bài đã giao phải giữ nguyên vật lý, tên/đơn vị và bối cảnh kể cả khi template hoặc specification hiện hành đổi.
- Giữ tương thích các mô phỏng cũ và bài giao cũ. Không gỡ fallback trước khi có migration/adapter được kiểm thử.
- Giữ phân quyền owner/teacher/student/school/public, kiểm duyệt thư viện và quota AI. Các thao tác playback/solver thuần không được vô tình gọi AI mỗi frame.
- UI mới dùng layout/token/component dùng chung; CSS phải scope theo component/feature. Không thêm selector toàn cục như `main`, `button`, `canvas` làm lệch trang khác.
- Khi sửa bộ lọc hoặc thêm trạng thái, kiểm tra loading, empty, error, retry, stale response và chuyển route lúc request chưa xong.

## 5. Kiến trúc mục tiêu: cấu hình nội dung, code cho năng lực nền

Tách các trách nhiệm, có thể dùng JSON versioned trước khi có nhu cầu quản trị DB phức tạp:

1. `PhysicsModelDefinition`: đầu vào/đầu ra, đơn vị SI, giả thiết, miền hợp lệ, solver/reference, sai số, điều kiện đầu/biên/kết thúc.
2. `SimulationTemplate`: model đã duyệt, preset, bối cảnh, mapping giáo trình, scene graph, controls và thiết bị đo.
3. `ActivityDefinition`: mục tiêu, nhiệm vụ, biến được đổi, dữ liệu cần thu, rubric, chính sách chấm và retry.
4. `AssetManifest`: ID ổn định, version, loại vector/raster/painter, nguồn, quyền sử dụng, anchor, kích thước hiển thị, bounds, biến thể, preload/fallback.
5. `SimulationRunSnapshot`: toàn bộ version, input thực tế, seed nếu có, output, kết quả kiểm định, scene tham chiếu bất biến.

Registry Java cho solver và TypeScript cho primitive là hợp lệ: thuật toán vật lý và khả năng vẽ cần được viết, kiểm thử, đăng ký. Không hứa thêm mọi hiện tượng mới chỉ bằng JSON. Một bài mới dùng các capability sẵn có phải thêm template/activity/assets mà không sửa switch theo tên bài trong component.

Không nhân bản công thức ở React, AI prompt, backend và chấm điểm. Nếu frontend cần preview tức thời, xác định evaluator được kiểm định hoặc worker với parity test với backend; kết quả chấm do backend tính trên snapshot chuẩn. Reference solver không gọi lại solver đang được kiểm tra.

Không cho JSON/AI thực thi JavaScript, Java, SQL hoặc URL tùy ý. Binding dùng path/biểu thức giới hạn với AST/allowlist, kiểm tra đơn vị, dependency cycle và giới hạn tài nguyên. Thiếu binding vật lý phải báo lỗi, không âm thầm thay thành 0 rồi phát hành.

Asset/environment phục vụ hình ảnh. Thay ô tô thành thang máy có thể cần đổi hướng trục, vector và bố trí, không chỉ đổi icon. Phải khai báo world coordinates, camera, đơn vị, aspect ratio và mức phóng đại. Phóng đại biên độ hoặc biến dạng để dễ nhìn phải có nhãn rõ.

### 5.1 Hợp đồng hiển thị 2D và 2.5D

- 2D: góc nhìn chính diện, nhìn ngang hoặc nhìn từ trên; ưu tiên cho đo đạc, đồ thị, tia sáng, mạch điện, quỹ đạo và vector.
- 2.5D: cùng dữ liệu vật lý, thêm phép chiếu trực giao/xiên cố định, lớp trước–sau, độ dày hình học minh họa, sprite có phối cảnh và bóng nhẹ. Chiều sâu trang trí không trở thành một bậc tự do vật lý mới.
- Tách `physicalDimension` khỏi `viewMode`. Sóng có trường u(x,t) có thể trình bày trên dây có chiều sâu; trường u(x,y,t) có thể hiển thị bản đồ màu hoặc mặt nước 2.5D. Không suy ra số chiều solver từ số chiều hình vẽ.
- Nội dung cần mô tả quan hệ không gian ba chiều theo giáo trình vẫn phải được biểu diễn trung thực bằng các hình chiếu đồng bộ, mặt cắt, ký hiệu hướng vào/ra mặt phẳng hoặc view 2.5D. Không bỏ nội dung chỉ vì renderer ưu tiên 2D; ghi rõ giới hạn biểu diễn.
- Khai báo view versioned trong template/scene contract: `viewMode`, `projection`, `worldAxes`, `camera`, `depthOrder`, `displayExaggeration`. Đây là trường đề xuất, phải bổ sung schema/DTO/normalizer/adapter và kiểm thử trước khi sử dụng; payload cũ mặc định view 2D tương thích.
- `ProjectionAdapter` chuyển world → screen và hỗ trợ screen → miền tương tác đã xác định. Phép chiếu 2.5D không có nghịch đảo duy nhất cho mọi điểm 3D: kéo vật/probe phải bị ràng buộc trên mặt phẳng, dây hoặc bề mặt vật lý cụ thể.
- Áp cùng phép chiếu cho vật thể, quỹ đạo, vector và thước. Giá trị đo được tính trong tọa độ vật lý SI trước phép chiếu, không suy từ khoảng cách pixel hoặc chiều dài vector nhìn thấy.
- Thước và probe phải nêu trục/đại lượng đo. Cho chuyển về view 2D để đo chính xác nếu phối cảnh làm khó đọc. Đổi view, zoom, bóng và asset không thay đổi solver output, bằng chứng học tập hay điểm số.
- Asset manifest bổ sung anchor, silhouette/hit area, hướng nhìn hỗ trợ, layer và biến thể 2D/2.5D. Tách hit testing khỏi ảnh trang trí; không để bóng che probe hay vector.
- Tái sử dụng CanvasRenderer/PrimitiveRendererRegistry; tách projection và các painter khi cần, tránh switch theo từng bài. Chỉ thêm WebGL renderer cho trường dày sau benchmark chứng minh Canvas không đáp ứng, dùng cùng contract và fallback 2D. Không yêu cầu dựng lại scene bằng Three.js cho mọi model.
- Test bắt buộc: điểm world đã biết chiếu đúng; vector và trajectory khớp actor; kéo probe đúng miền; resize/zoom không lệch hit area; đổi view không đổi kết quả đo; thứ tự lớp và clipping không che thông tin quan trọng.

## 6. Hợp đồng dữ liệu mở rộng

Giữ timeseries cũ qua adapter và bổ sung capability theo nhu cầu đã chứng minh:

- `particle/rigidBody`: vị trí, vận tốc, lực và sự kiện.
- `scalarField/vectorField`: miền không gian, tọa độ, shape, đơn vị, sampling, trục thời gian, boundary, dữ liệu và cách nội suy.
- `circuit`: topology, node/branch, nguồn và các đại lượng; không mặc định mọi mạch là một RC.
- `ray/geometry`: đối tượng quang học, tia và quan hệ hình học, khi coverage cần.
- `statistical`: seed, phân bố, số mẫu và thống kê cho hiện tượng ngẫu nhiên, khi coverage cần.

Không triển khai tất cả abstraction trước khi dùng. Sóng 1D là trường hợp đầu để chứng minh field contract. Viết ADR so sánh field samples/chunks với evaluator tham số được kiểm định; chọn phương án theo payload, thời gian seek, khả năng replay và laptop trường học. Không tải tensor x–y–t không giới hạn.

Schema validation phải kiểm tra version, type, số hữu hạn, shape, độ dài, monotonic time, thứ nguyên, axis, asset/binding tồn tại, node ID duy nhất và quota tài nguyên. Physics precision và render precision tách biệt; Float32 cho vẽ phải có ngân sách sai số, không dùng làm nguồn đáp án chấm.

## 7. Chuẩn học thuật và chống “đúng giả”

Mỗi model có hồ sơ: nguồn công thức, quy ước dấu/trục, SI, giả thiết, miền áp dụng, initial/boundary conditions, thuật toán, bước tính, absolute/relative tolerance và giới hạn. Không dùng một tolerance cố định cho mọi đại lượng.

Kiểm định nhiều lớp:

1. Unit/dimension checks, finite values, dữ kiện thiếu/mâu thuẫn và miền tham số.
2. Golden cases tính tay hoặc nguồn học thuật có thể đối chiếu.
3. Reference độc lập hoặc nghiệm giải tích; không dùng hai bản cùng thuật toán sai để xác nhận nhau.
4. Conservation/invariant phù hợp giả thiết: năng lượng, động lượng, điện tích… Không áp bảo toàn năng lượng cơ học cho hệ có ma sát.
5. Property/metamorphic tests: symmetry, đổi đơn vị tương đương, giới hạn zero, scale, phase/translation khi mô hình cho phép.
6. Convergence và stability với solver số; sai số gần 0 dùng absolute tolerance, vùng khác kết hợp relative tolerance.
7. Đối chiếu hình ảnh với dữ liệu: vật va chạm đúng thời điểm, trục và vector đúng dấu, tốc độ phát lại không đổi kết quả vật lý.
8. Người có chuyên môn duyệt nội dung trước `approved`; AI chỉ hỗ trợ review, không tự giả danh người duyệt.

Mô hình minh họa phải ghi rõ giản lược: đường sức không phải quỹ đạo hạt; hình sóng điện từ không phải dây dao động; phân rã là ngẫu nhiên; animation không thay thế phép đo/thực hành thật. Nội dung an toàn/thao tác phải có hoạt động phù hợp và không bị bỏ khỏi ma trận.

### 7.1 Công thức, lập luận và bằng chứng bắt buộc cho mỗi model

Mỗi mô phỏng phải kèm hồ sơ `docs/physics/models/<model-id>.md` và metadata công thức versioned để UI có thể hiển thị. Không chỉ liệt kê công thức cuối rồi gọi là chứng minh. Hồ sơ gồm:

1. Định luật/nền tảng sử dụng và nguồn học thuật có trang/mục; phân biệt định luật thực nghiệm, định nghĩa, giả thiết và hệ quả được suy ra. Không tuyên bố chứng minh định luật thực nghiệm chỉ bằng animation.
2. Bảng ký hiệu: ý nghĩa, đơn vị SI, chiều/dấu, đại lượng độc lập và phụ thuộc, miền hợp lệ.
3. Chuỗi suy luận từ định luật → phương trình → điều kiện đầu/biên → nghiệm hoặc thuật toán số. Nêu rõ bước xấp xỉ và khi nào không còn đúng.
4. Công thức khớp chính xác solver đang chạy; ví dụ số thay bằng input của run, hiển thị đơn vị, kết quả và quy tắc làm tròn. Đáp án chấm dùng số đầy đủ trước khi làm tròn UI.
5. Bằng chứng định lượng: kết quả solver, reference độc lập, sai số tuyệt đối/tương đối, tolerance có lý do, kết quả PASS/FAIL. Kiểm thử thực thi hỗ trợ kiểm chứng implementation, không thay thế lập luận học thuật.
6. Với nghiệm số không có dạng đóng, công bố phương pháp rời rạc, bậc hội tụ, điều kiện ổn định nếu có, kiểm tra refinement và invariant phù hợp. Không dựng một nghiệm giải tích giả để đủ hồ sơ.

Ví dụ cấu trúc suy luận cho chuyển động gia tốc không đổi:

```text
Giả thiết: hệ quy chiếu quán tính, chuyển động một chiều, a không đổi.
dv/dt = a, v(0) = v0
⇒ v(t) = v0 + a*t
dx/dt = v0 + a*t, x(0) = x0
⇒ x(t) = x0 + v0*t + (1/2)*a*t²
Kiểm tra: dx/dt = v(t), d²x/dt² = a; đơn vị mỗi hạng của x là m.
Ví dụ: x0=0 m, v0=10 m/s, a=2 m/s², t=8 s
⇒ v=26 m/s, x=144 m.
```

UI phải có bảng “Cơ sở vật lý” có thể mở rộng cạnh mô phỏng: giả thiết, công thức đang dùng, ý nghĩa ký hiệu, thay số tại thời điểm/probe hiện tại và nguồn. Thể hiện công thức bằng renderer toán học phù hợp, không ảnh chụp chữ. Nội dung công thức là dữ liệu trình bày; không `eval` chuỗi LaTeX để tính solver.

Trong bài đo hoặc dự đoán, chính sách giáo viên kiểm soát thời điểm mở phần thay số/đáp án để không lộ kết quả trước khi học sinh nộp. Công thức nền và giả thiết có thể được cho xem riêng. Khi hiện công thức trong view 2.5D phải dùng tọa độ vật lý, không dùng tọa độ pixel đã chiếu.

## 8. Pilot bắt buộc: sóng trên dây, sau đó mở rộng

### 8.1 Lát cắt đầu tiên

Triển khai sóng ngang điều hòa 1D trên dây lý tưởng, biên độ nhỏ, không tán sắc, không suy hao. Phân biệt cấu hình sóng tuần hoàn đã tồn tại toàn miền với nguồn bắt đầu phát tại t=0. Không hiển thị sóng xuất hiện tức thời khắp dây khi mô tả nguồn vừa bật.

Với sóng truyền +x đã ổn định:

`u(x,t) = A cos(kx - omega*t + phi)`

`omega = 2*pi*f`, `k = 2*pi/lambda`, `c = f*lambda`.

Chọn các đại lượng độc lập rõ ràng: ví dụ A, f, c, phi; lambda được suy ra. Nếu dùng lực căng F và khối lượng riêng dài mu, `c = sqrt(F/mu)` trong giả thiết dây lý tưởng; không đồng thời cho chỉnh c độc lập mâu thuẫn F, mu.

Hồ sơ sóng phải kèm suy luận sau và nguồn đối chiếu, dùng F là độ lớn lực căng không đổi, mu là khối lượng trên một đơn vị chiều dài:

```text
Đoạn dây dx có khối lượng mu*dx. Với độ dốc nhỏ:
F_y ≈ F*[∂u/∂x tại x+dx − ∂u/∂x tại x]
    ≈ F*(∂²u/∂x²)*dx.
Định luật II Newton: mu*dx*(∂²u/∂t²) = F*(∂²u/∂x²)*dx
⇒ ∂²u/∂t² = c²*∂²u/∂x², c² = F/mu.

Đặt theta = k*x − omega*t + phi, u = A*cos(theta):
∂²u/∂t² = −omega²*u; ∂²u/∂x² = −k²*u.
Thế vào phương trình sóng ⇒ omega² = c²*k².
Với c,k,omega > 0: c = omega/k = f*lambda.
Pha không đổi: k*x − omega*t + phi = hằng số
⇒ dx/dt = omega/k = c, xác nhận chiều truyền +x.
Vận tốc phần tử: ∂u/∂t = A*omega*sin(theta).
Gia tốc phần tử: ∂²u/∂t² = −omega²*u.
```

Đây là nghiệm sóng tuần hoàn với trạng thái đầu phù hợp, không mặc định dây đứng yên ở t=0. Với nguồn vừa bật trên dây bán vô hạn ban đầu đứng yên, mô tả theo tín hiệu nguồn trễ `u(x,t)=s(t−x/c)` cho `t>=x/c`, và `u=0` phía trước mặt sóng; chọn s và độ trơn tại điểm bật phù hợp điều kiện đầu. Phản xạ cần nghiệm/boundary riêng.

Fixture số bắt buộc: A=0.02 m, f=2 Hz, c=4 m/s, phi=0 ⇒ lambda=2 m, omega=4*pi rad/s, k=pi rad/m. Tại x=0.5 m,t=0: u=0 trong tolerance, vận tốc phần tử=0.08*pi m/s; tại x=0,t=0: u=0.02 m và gia tốc=−0.32*pi² m/s². Dùng fixture độc lập để bắt nhầm dấu, đơn vị và nhầm tốc độ sóng với vận tốc phần tử.

Khi mở rộng sóng dừng, giải thích phép chồng chất bằng đồng nhất thức `sin(kx−omega*t)+sin(kx+omega*t)=2*sin(kx)*cos(omega*t)`. Với hai đầu cố định, u(0,t)=u(L,t)=0 ⇒ kL=n*pi, n>=1 ⇒ lambda_n=2L/n. Nêu rõ A là biên độ mỗi sóng thành phần, biên độ bụng bằng 2A; không dùng A cho hai nghĩa khác nhau.

Giải thích và kiểm định riêng `du/dt` là vận tốc phần tử dây, không phải tốc độ truyền sóng c. Điểm đánh dấu giữ vị trí cân bằng x cố định và dao động ngang trục truyền. Không vẽ toàn bộ phần tử chạy theo đỉnh sóng.

### 8.2 Giao diện thí nghiệm

- View mặc định 2D nhìn ngang dây để thấy đúng li độ. View 2.5D tùy chọn dùng cùng u(x,t), tạo chiều sâu cho thiết bị và dây; không thêm dao động ngoài mô hình. Ưu tiên hoàn tất view 2D có đo đạc trước.
- Cảnh dây và nguồn, điểm đánh dấu, màu pha có chú giải, mũi tên truyền sóng, thước đo bước sóng.
- Hai góc nhìn đồng bộ: ảnh chụp u(x) tại t và đồ thị u(t) tại vị trí probe x0. Ghi rõ trục/đơn vị để học sinh không nhầm hai đồ thị.
- Play/pause/reset, seek, single-step, tốc độ phát, kéo probe, khóa tham số theo bài giao, so sánh trước/sau.
- A, f, c, phi với đơn vị và giới hạn; số đọc có độ chính xác phù hợp. Không đổi scale tự động gây cảm giác biên độ không thay đổi.
- Học sinh xem mô phỏng, đo, ghi bảng và nộp trên cùng trang. Không điều hướng sang trang khác để nộp.
- Thay tham số phải công bố chính sách: reset toàn thí nghiệm hoặc thay đổi nguồn liên tục; không âm thầm đổi toàn bộ trường đã lan truyền thành một trạng thái mới rồi mô tả là quá trình thật.

### 8.3 Bộ kiểm thử tối thiểu

- A=0 cho li độ 0; x và x+lambda cùng pha; x và x+lambda/2 ngược pha.
- Ở x cố định, u(t+T)=u(t). Đỉnh sóng tiến +c*dt đúng chiều.
- Đạo hàm li độ theo thời gian khớp vận tốc/gia tốc phần tử trong tolerance đã nêu.
- Nguồn bật tại t=0: vùng x>ct chưa chịu nhiễu, trạng thái đầu và điều kiện nguồn nhất quán.
- Kiểm tra probe/đồ thị/đáp án server trên cùng x,t; kiểm tra reset, seek, replay và thay tham số.
- Sampling đủ cả không gian và thời gian, có guard chống aliasing. Nếu dùng sai phân, kiểm tra điều kiện ổn định của chính thuật toán đó, hội tụ khi giảm dx/dt và không bịa CFL cho solver giải tích.
- Lưu → mở lại → giao lớp → học sinh đo/nộp → giáo viên chấm, dùng cùng run version.

### 8.4 Các bước tiếp theo của họ sóng

Sau khi pilot đạt, thêm theo coverage: xung truyền, sóng dọc, phản xạ đầu cố định/tự do, chồng chất, sóng dừng, giao thoa mặt nước, âm và các hiện tượng thuộc nguồn đã kiểm kê. Tách solver/renderer cần thiết, dùng lại probe và activity.

Sóng dừng trên dây hai đầu cố định: kiểm tra nút tại biên và điều kiện mode `lambda_n=2L/n`; nút và bụng đúng vị trí. Hai sóng ngược chiều phải có điều kiện biên/pha phù hợp. Không chỉ cộng hai animation tùy ý.

Sóng mặt nước chỉ mở rộng 2D sau khi thống nhất field contract và ngân sách hiệu năng. Nêu rõ khi dùng mô hình scalar đơn giản; không giả vờ đã mô phỏng thủy động lực đầy đủ.

Với sóng mặt nước, dùng view 2D nhìn từ trên với màu li độ có chú giải và view 2.5D chiếu xiên mặt u(x,y,t). Hai view dùng cùng field, thời gian và probe. Nếu phóng đại độ cao để thấy rõ sóng phải ghi hệ số; không dùng bóng/ánh sáng thay cho thông tin pha và biên độ định lượng.

## 9. Đa dạng có ý nghĩa và vai trò AI

Mỗi mục được phủ cần hoạt động thực sự khác về nhận thức, không đếm đổi màu/đổi số thành năng lực vật lý mới. Template có thể tạo biến thể bối cảnh, dữ liệu, ràng buộc, nhiệm vụ dự đoán/đo/khảo sát/giải thích và mức hỗ trợ. Cấu hình chưa được solver hỗ trợ phải được từ chối hoặc đề xuất mẫu gần nhất có giải thích.

AI được: chọn model/template được duyệt, trích dữ kiện có provenance, đề xuất preset hợp lệ, soạn nhiệm vụ/rubric, chọn asset trong manifest, dịch lời dẫn và gợi ý đối chiếu lỗi hiểu biết. AI không được: tự cung cấp series giả, tự thêm công thức thực thi, khẳng định PASS/100%, hoặc sửa physics snapshot để làm khớp đáp án.

AI output phải qua schema + domain validation. Thiếu giả thiết quan trọng thì yêu cầu giáo viên xác nhận. Giới hạn retry và token, ghi nhận usage theo cơ chế OpenRouter/SchoolService sẵn có. Nội dung công khai cần version và kiểm duyệt.

### 9.1 Nhận đề Vật lí THPT theo ngữ nghĩa, phản hồi ngoài phạm vi sớm

Yêu cầu sản phẩm: người dùng nhập đề Vật lí lớp 10, 11, 12 bằng cách diễn đạt bất kỳ trong phạm vi giáo trình đã kiểm kê, hệ thống phải tiếp nhận và hiểu mục tiêu vật lý, không yêu cầu khớp tên mẫu hoặc chứa từ khóa đặc biệt. Mọi đề hợp lệ phải có đường xử lý phù hợp: mô phỏng định lượng, trực quan hóa khái niệm, hoạt động đo/phân tích hoặc yêu cầu bổ sung dữ kiện. Không đồng nhất “đã nhận đề” với “đã đủ dữ kiện để chạy”.

Không thể suy ra bảo đảm mọi cách diễn đạt chỉ từ một prompt hoặc một điểm confidence của AI. Điều kiện nghiệm thu là bao phủ từng yêu cầu giáo trình và vượt bộ đánh giá nhận đề đa dạng, có báo cáo lỗi còn lại. Bài Vật lí hợp lệ mà chưa có model là thiếu năng lực sản phẩm, phải ghi vào gap matrix và tiếp tục triển khai, không được gắn nhãn “không phải Vật lí”.

Luồng bắt buộc:

```text
Đầu vào text/ảnh/tài liệu
→ đọc nội dung và kiểm tra chất lượng OCR khi cần
→ phân loại ngữ nghĩa môn học, phạm vi, mục tiêu và mức đủ dữ kiện
→ truy xuất yêu cầu giáo trình + capability/model/formula registry đã duyệt
→ kiểm tra schema, đơn vị, giả thiết, khả năng giải và khả năng biểu diễn
→ trả trạng thái rõ ràng hoặc chạy solver + kiểm định
→ hiển thị mô phỏng cùng cơ sở công thức, suy luận và dữ liệu đối chiếu
```

Phân loại trước khi tạo simulation run, dựng cảnh hoặc gọi chuỗi xử lý sâu. “Báo liền” nghĩa là trả kết quả ngay khi bước kiểm tra đầu vào có đủ bằng chứng, không chạy solver rồi mới báo. UI hiển thị đang kiểm tra, sau đó lý do ngắn và hành động sửa phù hợp. Lỗi timeout/provider/OCR không được biến thành kết luận ngoài môn.

Hợp đồng kết quả đề xuất, phải version hóa và nối qua API/UI:

| Trạng thái | Điều kiện | Phản hồi/hành vi |
|---|---|---|
| `READY` | Đề trong phạm vi, đủ dữ kiện, model và renderer tương thích | Chạy và chỉ công bố kết quả sau kiểm định |
| `NEEDS_CLARIFICATION` | Chưa xác định được yêu cầu, thiếu dữ kiện/giả thiết hoặc OCR mơ hồ | Hỏi đúng phần thiếu; giữ dữ kiện chắc chắn, không tự bịa số |
| `INVALID_PHYSICS_INPUT` | Dữ kiện/đơn vị mâu thuẫn, không khả thi theo giả thiết đã xác nhận | Chỉ ra dữ kiện và lý do; cho sửa ngay |
| `OUT_OF_DOMAIN` | Nội dung không phải nhiệm vụ Vật lí trong phạm vi sản phẩm | Báo ngắn gọn rằng hệ thống hỗ trợ Vật lí THPT, không tạo cảnh giả |
| `OUT_OF_CURRICULUM` | Có nội dung vật lý nhưng ngoài phạm vi chương trình đã xác minh | Nêu phạm vi không khớp; không tự nhận đó là bài lớp 10–12 |
| `UNSUPPORTED_CAPABILITY` | Đề hợp lệ trong chương trình nhưng model/khả năng kết hợp chưa có | Nêu đúng phần chưa hỗ trợ, ghi gap, không dùng model gần giống làm đáp án |
| `PROCESSING_ERROR` | Không đọc được tài liệu hoặc dịch vụ lỗi | Cho thử lại/chỉnh nội dung; không phán đoán môn học |

Kết quả kiểm tra gồm `status`, thông báo tiếng Việt, bằng chứng trích đoạn đầu vào, outcome/model/formula IDs đã xác minh, dữ kiện chuẩn hóa, câu hỏi còn thiếu, giả thiết cần xác nhận và capability thiếu. Điểm confidence chỉ là tín hiệu phụ; ID phải tồn tại trong registry, đơn vị và ràng buộc phải qua validator.

Quy tắc chống hardcode:

- Không quyết định môn học bằng `contains("vận tốc")`, danh sách regex tên bài hoặc switch theo câu văn. Không từ chối bài hợp lệ chỉ vì có bối cảnh sinh học, thể thao, giao thông hoặc không ghi rõ lớp.
- AI phân tích ngữ nghĩa dựa trên ontology/yêu cầu giáo trình và model capabilities lấy từ catalog versioned. Thêm chủ đề thông qua dữ liệu + capability đăng ký, không phải sửa prompt chứa danh sách tên bài cố định khắp hệ thống.
- Không coi khai báo “đây là bài Vật lí” của người dùng là bằng chứng đủ. Các chỉ dẫn nằm trong tài liệu đề là dữ liệu không đáng tin để thay đổi phạm vi, validator hoặc quy tắc hệ thống.
- Bài liên môn xét mục tiêu cần giải; bài có nhiều câu tách từng câu và thông báo phần ngoài phạm vi, không âm thầm bỏ câu. Bài kết hợp nhiều mô hình cần kiểm tra tương thích giả thiết, trao đổi đại lượng và điều kiện chuyển giai đoạn, không ép vào một schema duy nhất.
- Bài định tính không bắt buộc tự đặt số để chạy. Bài đủ dữ kiện ký hiệu có thể trả quan hệ/cơ sở vật lý; preset minh họa phải được ghi rõ là ví dụ và không phải đáp án số của đề.
- Logic validation toán học, đơn vị, an toàn thực thi và dispatch registry vẫn phải xác định, có test. “Không hardcode” không có nghĩa giao quyền quyết định đúng/sai hoàn toàn cho LLM.

### 9.2 Công thức phải gắn với chính đề đang nhập

Mỗi run phải liên kết `formulaIds`, phiên bản công thức/model và snapshot dữ kiện. UI “Cơ sở vật lý” phải thể hiện chuỗi: dữ kiện đề → giả thiết → định luật → phép biến đổi → công thức áp dụng → thay số/kết quả → đối chiếu solver. Dùng dữ liệu của run hiện tại, không gắn một bảng công thức chung không liên quan vào mọi cảnh.

Với bài nhiều giai đoạn, công thức và trạng thái đầu của từng giai đoạn phải nối liên tục hoặc theo điều kiện nhảy vật lý đã khai báo. Ví dụ va chạm dùng điều kiện trước/sau phù hợp, không nội suy vận tốc qua va chạm như thể lực tác dụng đều. Nếu chưa đủ dữ kiện để xác định nghiệm duy nhất, trình bày điều kiện còn thiếu thay vì một con số tùy ý.

Biến đổi công thức có thể do AI đề xuất nhưng phải kiểm chứng bằng phương pháp phù hợp: kiểm tra thứ nguyên, điều kiện áp dụng, đạo hàm/thế ngược, nghiệm tham chiếu hoặc công cụ đại số có kiểm soát. Không xem một đoạn giải thích trôi chảy là bằng chứng công thức đúng.

### 9.3 Bộ đánh giá nhận đề bắt buộc

- Đề chuẩn và biến thể diễn đạt cho từng mục lớp 10/11/12: tiếng Việt có/không dấu, đổi thứ tự câu, đổi đơn vị, ký hiệu, text/ảnh, nhiều câu, định tính, định lượng và liên môn.
- Đề thiếu dữ kiện, OCR sai, mâu thuẫn đơn vị, ngoài môn, ngoài chương trình, đề hợp lệ nhưng capability thiếu và nội dung cố điều khiển AI bỏ validator.
- Bài kiểm tra tổng hợp chưa dùng làm ví dụ trong prompt/catalog; tách tập xây dựng khỏi tập đánh giá để tránh chỉ nhận đúng câu mẫu.
- Đo false rejection của đề hợp lệ, false acceptance của đề ngoài phạm vi, phân loại sai trạng thái, model/formula mapping sai và khả năng chỉ rõ dữ kiện thiếu. Báo theo lớp/chủ đề, không chỉ accuracy trung bình.
- Mỗi lỗi nhận đề hợp lệ cần test hồi quy và cập nhật ontology/capability/validator thích hợp; không vá bằng cách hardcode nguyên câu đề.

## 10. Tiêu chí UI và trải nghiệm đủ tốt để thương mại hóa

- Ngôn ngữ hình ảnh thống nhất 2D/2.5D: hình học rõ, màu vật thể phân biệt, chiều sâu vừa đủ, thiết bị có chi tiết phù hợp bài học. Có view phân tích ít trang trí; đồ thị và bảng đo giữ 2D dễ đọc. Đa dạng đến từ bối cảnh, thiết bị và tương tác, không từ hiệu ứng chuyển động gây nhiễu.
- Thư viện khám phá theo lớp/chương/bài/bộ sách, kết quả có ảnh/video từ mô phỏng thật, mục tiêu và tương tác nổi bật. Không hứa capability chưa triển khai.
- Cảnh thí nghiệm chiếm ưu tiên thị giác; control, probe và giải thích ở cạnh, ít chữ trên sân khấu. Layout dùng chung nhưng bố trí thiết bị/cảnh theo đúng hiện tượng.
- Asset đa dạng bằng manifest; cung cấp ảnh/vector có nguồn và license rõ, anchor và scale nhất quán, không dùng asset thiếu dẫn đến cảnh trống.
- Tương phản rõ, nhãn tiếng Việt chuẩn, công thức và đơn vị dễ đọc, thao tác bàn phím, không dùng màu làm tín hiệu duy nhất; reduced motion vẫn cho phép đo bằng stepping.
- Desktop 1366x768, 1920x1080 và tablet 768px: không tràn ngang, không che nút nộp, không nhiều thanh cuộn lồng nhau. Kiểm thử fullscreen, resize và theme.
- Một màn hình giáo viên soạn/giao; một màn hình học sinh thí nghiệm/trả lời/nộp; lịch sử và rubric dễ truy xuất.
- Mục tiêu pilot trên thiết bị/browser chuẩn được ghi lại: 60 fps khi có thể, không thấp hơn 30 fps ổn định ở cấu hình chất lượng thấp; phản hồi control trong 100ms bằng feedback/preview, không đồng nghĩa backend luôn trả dưới 100ms. Đo p95/frame time, RAM, payload và thời gian tải; không tự báo đạt khi chưa benchmark.
- Không setState React cho từng phần tử mỗi frame; dùng runtime/canvas hiện có, cache static layer, worker khi đo đạc chứng minh cần, hủy request cũ khi đổi tham số.
- Nghiệm thu với ít nhất các kịch bản giáo viên tạo/giao và học sinh đo/nộp; báo người tham gia thật, không bịa user testing. Chất lượng mua hàng là giả thuyết cần pilot, không thể bảo đảm bằng prompt.

## 11. Bài tập, chấm và bằng chứng

Kế thừa 4 loại hoạt động hiện có. Bài đo chấm theo đúng model/run và tham số được giao; nếu cho đổi tham số phải ghi run/input đo và chính sách đáp án tương ứng. Không chấm kết quả của thí nghiệm biến đổi bằng đáp án của preset ban đầu.

Lưu prediction trước quan sát, bảng đo có x/t/đơn vị/input snapshot, kết luận, rubric và phản hồi. Server kiểm soát biến được đổi và quyền nộp; ẩn control ở UI không đủ. Lưu bằng chứng theo sự kiện có ích, không gửi mỗi frame lên server.

Điểm số tự động chỉ dựa tiêu chí định lượng đã cấu hình; lập luận, kết luận và thiết kế thí nghiệm có rubric giáo viên duyệt. Không gọi điểm đo số là đã chấm được toàn bộ năng lực học tập.

## 12. Trình tự thực thi liên tục, có cổng nghiệm thu

| Giai đoạn | Đầu ra và điều kiện chuyển bước |
|---|---|
| A — Kiểm kê | Baseline, nguồn và coverage inventory, ADR contract/versioning, risk register. Không đếm catalog cũ là đã phủ |
| B — Chứng minh kiến trúc | Pilot sóng 1D end-to-end, công thức/suy luận, field contract, test vật lý, replay, assignment, ảnh/video và benchmark |
| C — Chứng minh mở rộng | Mở rộng họ sóng; chuyển một model cũ sang template mới để chứng minh tương thích; công cụ author/validate template |
| D — Phủ nội dung | Triển khai theo gap matrix và model family thực sự cần; hồ sơ công thức từng model; ghép asset/activity dùng chung; review học thuật |
| E — Hoàn thiện | Xử lý các gap, regression, accessibility, hiệu năng và bằng chứng sử dụng giáo viên/học sinh thực tế khi có |
| F — Nghiệm thu | Báo cáo coverage thực đo, hồ sơ chứng minh, migration rehearsal, phương án release/rollback. Ghi rõ mọi phụ thuộc hoặc gap chưa thể xác minh |

Không cố định số solver/template để suy ra 100%. Số lượng được quyết định sau kiểm kê, ước lượng và kiểm thử. Không đổi mục tiêu 100% thành “đủ demo”; có thể phát hành từng phạm vi đã duyệt với trạng thái rõ.

## 13. Cách thực thi từ một prompt

1. Đọc repo và baseline; cập nhật `docs/implementation/physics-rollout.md` với thay đổi đã có, trạng thái từng cổng, phụ thuộc và rủi ro. Giữ checkpoint để tiếp tục khi context được rút gọn, không bắt đầu lại hoặc làm mất mục tiêu tổng thể.
2. Đề xuất ADR ngắn, chỉ rõ API/file sẽ thêm/sửa và cách payload cũ chạy tiếp. Tiến hành phần đã đủ thông tin, chỉ hỏi khi thiếu quyết định không thể suy ra.
3. Triển khai từng lát cắt end-to-end, có dữ liệu thật và error handling; sau khi qua cổng kiểm thử tiếp tục phần kế tiếp trong cùng nhiệm vụ. Không dừng ở component mock, tài liệu thiết kế hoặc pilot; không yêu cầu người dùng gửi prompt mới để thực hiện phần đã được giao.
4. Chạy kiểm thử liên quan. Frontend có `npm run check:architecture`, `npm run lint`, `npm test`, `npm run build` (hoặc `npm run check`); backend dùng `./mvnw test`, Windows `mvnw.cmd test`. Phân biệt lỗi có sẵn với lỗi mới, không vô hiệu hóa rule để pass.
5. Kiểm thử contract/replay cũ và mới; migration trên DB kiểm thử PostgreSQL tương thích; kiểm thử quyền và request ngoài miền.
6. Kiểm tra UI thật, lưu ảnh/video và số đo. Test không thực thi được phải ghi lý do và phần chưa xác minh.
7. Tổng kết file thay đổi, hành vi trước/sau, test và bằng chứng, gap còn lại, bước tiếp theo. Không claim hoàn thành toàn chương trình khi mới xong pilot.

## 14. Definition of done

- Mỗi nội dung phát hành truy xuất được đến nguồn giáo trình, model version, template, activity, bộ test và người duyệt.
- Model có giả thiết/miền áp dụng và kết quả kiểm định độc lập; renderer phản ánh đúng dữ liệu.
- Mỗi model có công thức, suy luận, bảng ký hiệu/đơn vị, nguồn, ví dụ thay số và test đối chiếu độc lập; UI trình bày cơ sở vật lý theo chính sách bài tập.
- Nhận đề theo ngữ nghĩa qua toàn bộ phạm vi đã kiểm kê; báo ngoài phạm vi ở bước đầu; phân biệt thiếu dữ kiện, thiếu capability và lỗi dịch vụ. Công thức hiển thị truy xuất được tới đúng input/model/run và đã được kiểm chứng.
- Pilot sóng chứng minh field + timeseries phối hợp, không animation giả và không lệch probe/đáp án.
- View 2D/2.5D tuân thủ projection contract; các view của cùng run trả cùng phép đo và điểm số. Không có chiều sâu trang trí bị hiểu nhầm là chuyển động vật lý.
- Các mô phỏng đã lưu, bài đã giao và lớp/học sinh cũ vẫn hoạt động; không đổi kết quả replay do cập nhật template.
- UI đạt các kịch bản và viewport đã nêu, có minh chứng; backend/FE tests và build tương ứng được báo trung thực.
- Tất cả gap hiển thị trong ma trận; “100%” chỉ được gắn sau khi đóng phạm vi nguồn và duyệt toàn bộ mục.

**Bắt đầu bằng kiểm kê nguồn + audit capability, triển khai pilot sóng ngang 1D kèm công thức và chứng minh qua pipeline thực. Sau khi pilot và tương thích qua nghiệm thu, tiếp tục phủ ma trận cho tới khi hoàn thành phạm vi đã giao. Một prompt là đầu vào cho toàn bộ nhiệm vụ; không phải bằng chứng tự động rằng mọi mục đã đúng hoặc đã hoàn thành.**
