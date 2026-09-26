# PhysLive: Hoàn thiện UI mô phỏng trực quan từ schema và backend hiện có

## 1. Vai trò và mục tiêu

Bạn là implementation owner làm việc trực tiếp trong repository PhysLive này. Hãy triển khai hoàn chỉnh khả năng dựng mô phỏng trực quan từ các schema đã được phê duyệt và kết quả tính toán của backend. Không dừng ở phân tích, mockup, kế hoạch hoặc một vài màn hình demo. Tiếp tục thực hiện cho đến khi luồng thực tế từ đề bài → specification → simulation response → UI trực quan → điều chỉnh tham số hoạt động nhất quán và có bằng chứng kiểm thử.

Không cần timeline, ước lượng ngày công hay phần trăm hoàn thành. Báo cáo theo bằng chứng đã chạy được và các blocker thật sự còn lại.

Mục tiêu cuối cùng:

- Tận dụng và nâng cấp UI hiện có, không xây một frontend song song.
- Mỗi schema hiện hành dùng được trong production phải có cách trình bày trực quan phù hợp với bản chất vật lý của nó.
- UI và renderer phải data-driven từ contract, không tăng dần các nhánh `if/switch` theo `schemaId`.
- Asset SVG phải đủ đa dạng theo các họ hiện tượng vật lý, tái sử dụng bằng semantic metadata và có biến thể hợp lý.
- Các tham số điều chỉnh phải lấy từ schema/backend, có đơn vị, miền giá trị và bước thay đổi đúng học thuật; thay đổi phải gọi lại backend solver thay vì tính công thức vật lý ở frontend.
- Mọi hình ảnh, đồ thị, vector, tia, trường, mạch và số liệu phải nhất quán với output đã được backend validation chấp nhận.

## 2. Hiện trạng bắt buộc phải đọc trước khi sửa

Đọc đầy đủ các file liên quan, không suy đoán kiến trúc từ tên file:

- `react-client/src/components/workspace/LearningWorkspace.tsx`
- `react-client/src/components/workspace/learning/LearningInspector.tsx`
- `react-client/src/components/simulation/PhysicsScene.tsx`
- `react-client/src/components/simulation-canvas/CanvasPhysicsScene.tsx`
- `react-client/src/simulation-runtime/SimulationRuntime.ts`
- `react-client/src/simulation-runtime/SimulationData.ts`
- `react-client/src/simulation-scene/SceneGraph.ts`
- `react-client/src/simulation-scene/SceneCompiler.ts`
- `react-client/src/simulation-scene/BindingResolver.ts`
- `react-client/src/simulation-scene/PrimitiveCapabilities.ts`
- `react-client/src/simulation-scene/VectorScene.ts`
- `react-client/src/simulation-renderer/CanvasRenderer.ts`
- `react-client/src/simulation-renderer/PrimitiveRendererRegistry.ts`
- `react-client/src/simulation-renderer/VectorSceneRenderer.ts`
- `react-client/src/types/physlive.ts`
- `react-client/src/api/simulationApi.ts`
- `backend/src/main/java/com/example/backend/service/simulation/SimulationService.java`
- `backend/src/main/java/com/example/backend/service/problem/SchemaDefinitionService.java`
- `backend/src/main/resources/schemas/source/**`
- `scripts/check-scene-contracts.mjs`
- `scripts/generate-schema-catalog.mjs`
- `docs/implementation/schema-catalog-source-modules.md`

Kiểm tra lại số liệu từ working tree trước khi triển khai. Tại thời điểm viết prompt, generated catalog có 182 schema-version entries: 92 entries có `sceneGraph`, 90 entries đang dùng scene sinh từ series/actors, toàn bộ có `adjustableParameters`, nhưng thư viện mới có 11 SVG. Đây chỉ là snapshot để phát hiện drift, không phải con số được hardcode vào implementation hoặc acceptance test.

## 3. Kiến trúc phải giữ

Giữ luồng dữ liệu sau làm nguồn sự thật:

```text
Approved schema/version
  → backend solver binding
  → numerical output đã validate
  → SimulationResponse
  → visualization contract + controls + series/fields
  → SceneCompiler
  → BindingResolver
  → generic primitive renderers + semantic SVG assets
  → LearningWorkspace / student workspace
```

Các nguyên tắc không được vi phạm:

1. Backend là nguồn sự thật cho vật lý, đơn vị, output và miền tham số. Frontend không được tự chép công thức solver để tạo dữ liệu mô phỏng.
2. Renderer không được dispatch theo `schemaId`, topic, lesson hoặc tên environment. Nó chỉ được dispatch theo primitive/capability đã khai báo.
3. Schema chọn primitive, binding, asset hint và cách trình bày; schema không chứa mã JavaScript hoặc biểu thức tùy ý có thể thực thi.
4. Không sửa trực tiếp một schema version đã xuất bản. Nếu visualization contract, controls hoặc output công khai thay đổi, tạo version mới theo quy trình catalog hiện tại và giữ version cũ để replay.
5. Không làm hỏng historical simulation. Fallback compatibility chỉ dành cho snapshot cũ; schema hiện hành phải có metadata rõ ràng.
6. Không dùng một hình khối generic cho mọi bài để đạt coverage hình thức. Cũng không tạo một renderer riêng cho từng schema.
7. Không đưa dữ liệu AI chưa kiểm tra trực tiếp vào canvas. Chỉ render contract và output đã qua backend validation.

## 4. Audit coverage trước khi triển khai

Tạo một inventory có thể sinh lại bằng script, lấy danh sách identity hiện hành từ nguồn catalog/runtime chính xác thay vì tự ghi tay. Với mỗi schema hiện hành, ghi nhận:

- `schemaId@version`, topic, model và solver binding.
- Các output scalar, time series, vector, field và đơn vị.
- Scene hiện tại là explicit scene graph hay fallback.
- Primitive, binding source, environment, effect và asset hint đang dùng.
- Các `adjustableParameters`: key, label, symbol, unit, min, max, step, default/initial value.
- Mức độ phù hợp học thuật của scene: physical animation, ray/circuit diagram, field visualization, graph/data view hoặc kết hợp.
- Asset còn thiếu, binding còn thiếu, output không đủ để dựng đúng, control vô nghĩa hoặc khoảng điều chỉnh không hợp lý.

Phân nhóm theo capability, ít nhất gồm:

- Kinematics và mechanics nhiều vật.
- Force, collision, spring, oscillation, orbit và fluid/hydrostatics.
- Wave, sound, interference, reflection và scalar field.
- Circuit DC/AC, RC/RLC, diode, sensor và transformer.
- Electric field, magnetic force và induction.
- Thermal, gas, calorimetry, phase change và expansion.
- Optics: lens, ray, refraction, interference và optical instruments.
- Modern physics, atomic/nuclear, radiation và medical imaging.
- Measurement, experimental graph và energy/environment.

Inventory phải phân biệt “scene render được” với “scene đúng và có giá trị sư phạm”. Một graph mặc định không được xem là hoàn thiện cho schema vốn cần sơ đồ tia, topology mạch, nhiều vật thể hoặc trường không gian.

## 5. Hoàn thiện visualization contract theo hướng data-driven

Mở rộng contract hiện có, không tạo contract song song. Ưu tiên sử dụng:

- `visualization.scene`
- `visualization.series`
- `visualization.presentation`
- `visualization.presentation.sceneGraph.nodes`
- `visualization.controls` được backend sinh từ `adjustableParameters`
- `visualization.spec`/entities nếu contract hiện tại đã hỗ trợ và có dữ liệu backend tương ứng
- `scalarOutputs` và `scalarFields` đúng shape/version

Mỗi node cần có ID ổn định, primitive được hỗ trợ, layer rõ ràng, transform/binding hợp lệ và property đã validate. Binding chỉ được lấy từ:

- Constant hữu hạn.
- Quantity/adjustable parameter đã khai báo.
- Series/output đã khai báo.
- Entity binding hợp lệ.
- Bộ expression operator hữu hạn, an toàn và được validate hiện có.

Nếu một capability chung còn thiếu, bổ sung primitive tổng quát vào `PrimitiveCapabilities`, validator, renderer registry và test. Ví dụ capability có thể cần hoàn thiện: rigid body, multi-body transform, force arrow, vector decomposition, spring/rope, field probe, heat flow, circuit component/wire/current direction, lens/ray, wavefront, detector/screen, decay population, uncertainty interval và data marker. Chỉ thêm capability khi ít nhất một nhóm schema cần nó; không thêm primitive mang tên schema.

Scene phải hỗ trợ số lượng object theo contract/output thực tế. Không hardcode một xe hoặc hai vật nếu model hợp lệ có nhiều entity; đồng thời không dựng thêm object mà backend không có trạng thái để điều khiển.

## 6. Asset SVG và ngôn ngữ hình ảnh

Không tạo thư viện asset tĩnh. Mỗi scene phải dùng primitive renderer hoặc markup do AI sinh theo mô tả đã xác nhận; không có SVG catalog, asset selector hay asset manifest trong runtime.

Tối thiểu phải có coverage phù hợp cho:

- Xe, xe thí nghiệm, vật nặng, quả bóng, hạt, vật treo, mặt phẳng nghiêng, ròng rọc, lò xo và dây.
- Nguồn điện, điện trở, tụ, cuộn cảm, diode, công tắc, ampe kế, vôn kế, cảm biến, op-amp và transformer.
- Điện tích, bản tụ, nam châm, cuộn dây, đường sức và đầu dò.
- Nguồn sóng, dây, loa, mặt nước, vật cản, detector và wavefront.
- Thấu kính hội tụ/phân kỳ, gương nếu catalog cần, vật, ảnh, màn, kính hiển vi và kính thiên văn.
- Bình nhiệt lượng, nhiệt kế, piston/xilanh, chất rắn, chất lỏng, khí và chỉ báo truyền nhiệt.
- Hạt nhân/nguyên tử, photon, electron, detector bức xạ và thiết bị imaging ở mức sơ đồ giáo dục.
- Thước đo, đồng hồ, điểm dữ liệu, thanh sai số và dụng cụ thí nghiệm cơ bản.

Mỗi definition cần tags/aliases theo ý nghĩa, variants có màu/hình dáng khác nhau, `viewBox` đúng, anchor hợp lý và selection ổn định theo seed. SVG phải là asset cục bộ, không dùng script, external URL, embedded credential hoặc nội dung không kiểm soát. Bảo đảm hiển thị rõ ở dark/light theme, không phụ thuộc riêng màu sắc để truyền đạt trạng thái và không gây nhầm kích thước hiển thị với tỷ lệ vật lý.

Không để asset tải bất đồng bộ làm scene nhấp nháy hoặc biến mất. Có preload/caching hợp lý, fallback tương thích và invalidation rõ ràng. Không decode SVG hoặc cấp phát object nặng trong mỗi animation frame.

## 7. Tham số điều chỉnh và tương tác

Nâng cấp controls hiện có trong `LearningWorkspace`, `LearningInspector` và student parameter panel; không tạo panel mới tách rời.

Yêu cầu:

- Sinh control hoàn toàn từ `visualization.controls`/`adjustableParameters`.
- Hiển thị label, symbol, unit, giá trị hiện tại, min/max/step và trạng thái invalid rõ ràng.
- Dùng slider kết hợp numeric input khi phù hợp; hỗ trợ bàn phím và mobile.
- Với miền rất rộng hoặc nhiều bậc độ lớn, bổ sung scale/step strategy khai báo bằng metadata thay vì hardcode theo schema.
- Nếu có nhiều control, nhóm theo ý nghĩa như initial condition, environment, source, material hoặc observation; grouping cũng phải đến từ capability/metadata tổng quát.
- Chỉ thêm nhiều adjustable parameter khi chúng thật sự có ý nghĩa học thuật và solver/binder hiện hành sử dụng chúng. Không biến hằng số vật lý thành slider và không thêm control giả chỉ để đủ số lượng.
- Mỗi request điều chỉnh phải qua API backend, kiểm tra bounds phía server, chạy lại solver và validation. Frontend chỉ debounce, cancel/stale-response guard và cập nhật UI.
- Trong lúc đang kéo, dừng playback nhất quán, hiển thị pending state nhẹ, không trộn frame cũ với parameters mới.
- Nếu backend từ chối hoặc validation fail, giữ simulation được xác nhận gần nhất, chỉ rõ control gây lỗi và cho phép reset.
- Reset phải phục hồi đúng baseline server-generated simulation, không phục hồi từ một response điều chỉnh trung gian.
- Teacher workspace, student assignment, shared library và replay phải sử dụng cùng contract và cùng quy tắc control, chỉ khác quyền ghi/persist.

Không ép mọi schema phải có cùng số control. Với bài chỉ có một biến độc lập có ý nghĩa thì một control là đúng; với model nhiều tham số, phải cho phép điều chỉnh nhiều biến liên quan thay vì chỉ expose một biến tùy tiện.

## 8. Quy tắc trực quan và học thuật

Chọn hình thức hiển thị theo bản chất mô hình:

- Chuyển động: vật thể, quỹ đạo, trục tọa độ, vector vận tốc/gia tốc/lực và mốc thời gian.
- Nhiều vật: ID/nhãn ổn định, màu phân biệt, trạng thái trước/sau sự kiện và bảo toàn liên quan.
- Sóng: biên độ, pha, bước sóng, nút/bụng, wavefront hoặc scalar field; không mô phỏng sóng bằng một vật chạy ngang.
- Mạch: topology mạch đúng, cực tính, chiều dòng quy ước, trạng thái tụ/cuộn cảm và đồ thị đại lượng theo thời gian.
- Quang học: trục chính, tiêu điểm, tia đặc biệt, vị trí vật/ảnh và phân biệt ảnh thật/ảo.
- Điện từ: hướng trường/lực/vận tốc theo quy ước; vector phải bám đúng dấu của output.
- Nhiệt học: phân biệt nhiệt độ, nhiệt lượng và trạng thái pha; không dùng độ cao hoặc tốc độ để ngụ ý nhiệt nếu không có chú giải.
- Hiện tượng không phù hợp để “chuyển động hóa” phải dùng diagram, graph, field, interval hoặc synchronized readout thay vì animation giả.

Luôn hiển thị đơn vị. Axes, legends, vector labels, significant values và graph scale phải đọc được. Nếu dùng auto-scale để xem rõ, ghi rõ đây là visual scale; không làm người học tưởng kích thước màn hình là tỷ lệ vật lý thật. Interpolation chỉ dùng cho trình bày giữa các sample backend, không được làm thay đổi kết luận vật lý.

Mọi readout và chart phải lấy cùng `SimulationResponse` và cùng thời điểm runtime với canvas. Không để canvas ở thời điểm này nhưng graph/readout ở index khác.

## 9. Nâng cấp UI hiện có

Giữ cấu trúc học tập hiện tại: vùng quan sát, vùng thử nghiệm, giải thích, graph/data, playback, overlays và parameter controls. Cải thiện tại đúng component đang sở hữu chức năng.

Yêu cầu UX:

- Responsive desktop/tablet/mobile, không làm canvas bị cắt hoặc panel che nội dung.
- Dark/light theme nhất quán.
- Playback, seek, speed, reset và overlays không bị reset ngoài ý muốn khi response adjustment về.
- Hiển thị loading, unsupported contract, invalid scene, failed validation và empty data thành trạng thái có thể hiểu được; không để canvas trắng.
- Sửa toàn bộ mojibake UTF-8 còn hoạt động trong UI liên quan mô phỏng.
- `aria-label`, keyboard focus, contrast và control labeling phải dùng được với assistive technology.
- Không thay đổi thiết kế toàn app hoặc tạo design system mới; nâng cấp các pattern hiện có.

## 10. Kiểm thử bắt buộc

Mở rộng test hiện có thay vì chỉ chụp screenshot thủ công.

### Contract và catalog

- Mọi schema hiện hành có visualization contract hợp lệ.
- Mọi source của series/node/binding tồn tại trong output contract hoặc parameter contract.
- Mọi adjustable parameter tham chiếu quantity hợp lệ, bounds hữu hạn, `min < max`, step dương và initial/default nằm trong bounds.
- Mọi asset hint resolve được theo semantic manifest hoặc fallback được khai báo rõ.
- Không có duplicate node/asset ID, unsupported primitive/effect hoặc cyclic binding.
- Catalog source và generated catalog không drift.

### Renderer/runtime

- Test từng primitive/capability với deterministic fixture.
- Test scene nhiều entity, negative coordinate, zero value, extreme valid range, missing optional series và scalar field 1D/2D.
- Test interpolation, seek, playback end, speed, resize/DPR, dark/light palette và cache invalidation.
- Không có NaN/Infinity truyền tới canvas API.
- Không có dispatch theo `schemaId` trong scene compiler hoặc renderer; architecture check phải bắt được regression.

### Controls/API

- Test bounds, numeric input, slider, reset, debounce, request race và unmount/navigation.
- Chứng minh response cũ không ghi đè response mới.
- Chứng minh mỗi control tiêu biểu làm backend recompute và thay đổi đúng output liên quan.
- Chứng minh invalid adjustment không trở thành simulation accepted.

### Visual QA

Chạy ứng dụng với dữ liệu backend thật và kiểm tra tối thiểu một scene đại diện cho mỗi capability family ở desktop và mobile, dark và light theme. Sau đó kiểm tra toàn bộ current approved identities bằng một gallery/test harness chỉ dùng trong development/test hoặc một script kiểm tra tự động; không mở endpoint production không được bảo vệ.

Đối với mỗi scene đại diện, xác minh bằng tay độc lập ít nhất một trạng thái đầu, giữa và cuối; so sánh vị trí/vector/readout với output backend. Với diagram tĩnh, xác minh quan hệ hình học và dấu/đơn vị thay vì chỉ xác minh “có hình”.

## 11. Lệnh kiểm tra cuối

Khám phá wrapper/lệnh thật trong repository và chạy ít nhất:

```text
backend: .\mvnw.cmd clean test
backend: .\mvnw.cmd package -DskipTests
frontend: npm ci
frontend: npm run check
root: node scripts/generate-schema-catalog.mjs --check
root: node scripts/check-scene-contracts.mjs
root: git diff --check
```

Nếu `npm ci` không cần chạy lại do lockfile/dependencies không đổi, vẫn phải chạy `npm run check`. Không bỏ qua test để lấy kết quả xanh. Nếu một external/browser gate không chạy được, hoàn thành phần độc lập rồi báo chính xác prerequisite còn thiếu.

## 12. Điều kiện hoàn thành

Chỉ coi nhiệm vụ hoàn thành khi có bằng chứng cho tất cả điều sau:

- Mọi schema identity hiện hành được audit và có scene phù hợp; không còn schema vô tình rơi vào generic graph chỉ vì thiếu metadata.
- Mỗi capability family có renderer/primitive và asset coverage đủ dùng, không dựa vào nhánh schema-specific.
- Asset SVG đa dạng, semantic selection ổn định, tải/caching đúng và dark/light đều rõ.
- Controls được sinh từ backend contract, có bounds/units đúng, điều chỉnh được các tham số hợp lý và gọi lại solver thật.
- Canvas, graph, data/readout và parameter state đồng bộ cùng một run/time.
- Các mô phỏng nhiều vật thể hiển thị đúng số entity mà contract/backend thực sự hỗ trợ.
- Không có công thức vật lý mới bị chuyển sang frontend.
- Không phá replay/versioning của schema cũ.
- Không còn active mojibake hoặc trạng thái canvas trắng không giải thích.
- Backend test, frontend check, catalog drift check, scene contract check và diff check đều qua.
- Báo cáo cuối liệt kê file thay đổi, coverage trước/sau, các scene đã kiểm tra trực quan, lệnh đã chạy và mọi giới hạn còn lại; không tuyên bố “đầy đủ” nếu vẫn còn schema hoặc capability chưa có bằng chứng.

## 13. Các shortcut bị cấm

- Không hardcode `schemaId → component` hoặc `schemaId → asset` trong frontend.
- Không viết hàng trăm component React riêng cho từng bài.
- Không coi một line chart giống nhau cho mọi schema là hoàn thiện trực quan.
- Không tạo chuyển động trang trí không liên quan đến output vật lý.
- Không sinh SVG ngẫu nhiên ở runtime hoặc gọi dịch vụ ảnh bên ngoài khi chạy simulation.
- Không tin client-provided bounds, units hoặc normalized values.
- Không nới validation, bỏ reference check hoặc sửa solver chỉ để hình ảnh dễ dựng.
- Không sửa published schema version tại chỗ hoặc xóa historical schema/run.
- Không đổi API hiện hành một cách breaking nếu có thể mở rộng tương thích.
- Không hoàn thành bằng tài liệu hoặc screenshot mà thiếu implementation và test chạy được.
