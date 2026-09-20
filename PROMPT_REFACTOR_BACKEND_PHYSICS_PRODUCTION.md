# Prompt duy nhất: Refactor kiến trúc Backend Physics của PhysLive để sẵn sàng production

> Đây là prompt triển khai hoàn chỉnh dành cho coding agent. Thực hiện liên tục đến khi đạt toàn bộ cổng nghiệm thu. Không lập timeline, không ước lượng ngày/tuần, không dừng ở bản demo và không tuyên bố hoàn thành nếu còn tiêu chí bắt buộc chưa đạt.

## 1. Vai trò và mục tiêu

Bạn là kỹ sư trưởng chịu trách nhiệm refactor backend physics của PhysLive trong repository hiện tại. Hãy sửa toàn bộ các vấn đề kiến trúc, hardcode, lặp khai báo, contract lỏng, validation vòng tròn và coupling giữa topic được liệt kê trong prompt này.

Mục tiêu cuối cùng:

1. AI chỉ trích xuất dữ kiện; AI không tính công thức, không chọn thuật toán tùy ý và không tạo code thực thi.
2. Mọi dữ kiện đi vào solver phải được chuẩn hóa thành canonical quantity đúng schema, đúng kiểu, đúng đơn vị và đúng miền vật lý.
3. Solver không đọc `JsonNode`, không đoán alias và không hỗ trợ nhiều hình dạng payload cùng lúc.
4. Thêm một physics model mới không yêu cầu sửa switch trung tâm hoặc khai báo lại ID/output/default ở nhiều nơi không cần thiết.
5. Numerical solver và reference oracle phải độc lập về công thức hoặc thuật toán, để một lỗi không thể tự xác nhận chính nó.
6. Schema, solver binding, output contract, visualization và catalog phải được kiểm tra nhất quán trước khi application phục vụ request.
7. Giữ nguyên behavior vật lý đúng hiện tại, sửa behavior sai có bằng chứng học thuật, giữ tương thích dữ liệu/replay cũ bằng adapter versioned có test.
8. Backend phải chia package theo topic/capability rõ ràng. Không đặt solver đa topic trong package sai chủ đề.
9. Không đưa công thức vật lý sang JSON, frontend hoặc prompt AI. Công thức vẫn là code Java được review và test.
10. Không dùng hậu tố `1d`, `2d` trong public model/schema/asset ID. Mặt phẳng là chế độ hiển thị mặc định. Không đổi các token kỹ thuật bắt buộc như Canvas API `getContext("2d")`.

## 2. Quy tắc làm việc bắt buộc

- Đọc toàn bộ file liên quan trước khi sửa. Kiểm tra `AGENTS.md` nếu có.
- Chạy `git status` và giữ nguyên mọi thay đổi không thuộc phạm vi. Không reset, checkout hoặc xóa thay đổi của người dùng.
- Ghi baseline test trước khi refactor. Phân biệt lỗi có sẵn với regression do thay đổi mới.
- Thực hiện refactor theo dependency kỹ thuật, nhưng hoàn thành trong cùng một luồng công việc; không đưa timeline.
- Không thay Spring Boot/JPA/Jackson chỉ để đổi phong cách kiến trúc.
- Không sửa migration đã chạy. Nếu cần thay đổi DB, tạo migration tăng thêm, có backfill và test tương thích.
- Không tự động sửa dữ liệu production. Cung cấp migration/adapter và báo cáo dry-run rõ ràng.
- Không làm mất lịch sử schema, solver version, simulation run hoặc assignment snapshot.
- Không xóa compatibility path trước khi có adapter tách biệt và test replay payload cũ.
- Mọi lỗi contract phải fail sớm với message có `schemaId`, `modelId`, quantity/output key liên quan; không để `NullPointerException` hoặc `NaN` đi sâu vào solver.
- Không dùng reflection tùy tiện, service locator toàn cục hoặc stringly-typed magic để thay một dạng hardcode bằng dạng hardcode khó thấy hơn.
- Không tạo abstraction không có consumer thực tế. Mỗi abstraction mới phải loại được duplication/coupling đo được.
- Không công bố production-ready chỉ vì build thành công. Phải đạt tất cả điều kiện thành công cuối prompt.

## 3. Hiện trạng phải kiểm chứng lại

Các đường dẫn tính từ root `physLive_preview/`:

- `backend/src/main/resources/schemas/catalog.json`: catalog schema/solver/visualization hiện hành.
- `backend/src/main/resources/units/catalog.json`: catalog đơn vị.
- `backend/src/main/resources/prompts/physics-specification-system.txt`: hợp đồng đầu ra AI.
- `backend/src/main/java/com/example/backend/physics/model/PhysicsValues.java`: đang đọc quantity từ overrides, `quantities[]` và field root.
- `backend/src/main/java/com/example/backend/physics/model/**/**Parameters.java`: bind dữ kiện, validation và đôi khi chứa luôn công thức.
- `backend/src/main/java/com/example/backend/physics/solver/`: numerical solvers.
- `backend/src/main/java/com/example/backend/physics/reference/`: reference solvers.
- `backend/src/main/java/com/example/backend/physics/validation/EndConditionResolver.java`: normalize, validate, resolve và trim end condition.
- `backend/src/main/java/com/example/backend/service/problem/SchemaDefinitionService.java`: đang gộp repository lookup, quantity contract, adjustment và visualization validation.
- `backend/src/main/java/com/example/backend/service/problem/SpecificationReadinessService.java`: readiness và ambiguity generation.
- `backend/src/main/java/com/example/backend/service/problem/ProblemService.java`: CRUD, upload, OCR, AI extraction, specification và ambiguity workflow.
- `backend/src/main/java/com/example/backend/service/simulation/PhysicsValidationService.java`: đối chiếu numerical/reference.
- `backend/src/main/java/com/example/backend/bootstrap/PhysicsCatalogInitializer.java`: bootstrap schema/solver version.
- `backend/src/test/java/com/example/backend/physics/`: physics tests hiện tại.

Trước khi sửa, đo và ghi lại tối thiểu:

- số schema/model/solver/reference;
- số lần gọi `PhysicsValues.require/optional`;
- số switch dispatch theo model;
- số wildcard import trong physics;
- các model dùng chung solver ID;
- các default và hằng số bị lặp;
- các test đang truyền quantity ở root thay vì payload production;
- kết quả backend test, frontend check, scene contract và curriculum coverage.

Không dùng số liệu cũ trong prompt làm sự thật nếu repository đã thay đổi.

## 4. Kiến trúc đích bắt buộc

Luồng production phải trở thành:

```text
AI response / teacher edit / legacy replay
  -> Strict specification contract validation
  -> Legacy payload adapter (chỉ khi payload cũ)
  -> Schema selection và version pinning
  -> Canonical name resolution tại boundary
  -> Unit normalization tại boundary
  -> Default materialization tại boundary
  -> Compiled schema validation
  -> CanonicalQuantityBag bất biến
  -> PhysicsModuleRegistry
  -> Model-specific parameter binder
  -> Numerical solver
  -> Output contract validation
  -> Independent reference/invariant/golden validation
  -> Persisted immutable run snapshot
```

Solver và reference solver không được đọc trực tiếp:

- AI aliases;
- symbol;
- `originalUnit`;
- raw `JsonNode` specification;
- field root legacy;
- schema JSON động ở giữa vòng tính.

## 5. Workstream A — Canonical input contract và strict AI output

### 5.1 Tạo kiểu dữ liệu canonical

Tạo các kiểu tương đương, tên có thể điều chỉnh theo convention hiện có:

```java
public record CanonicalQuantity(
        String key,
        BigDecimal value,
        String unit,
        String sourceText) {}

public final class CanonicalQuantityBag {
    public double require(String key);
    public double optional(String key, double defaultValue);
    public BigDecimal requireDecimal(String key);
    public boolean contains(String key);
    public Set<String> keys();
}
```

Yêu cầu:

- Bất biến sau khi compile.
- Lookup O(1), không quét lại `quantities[]` cho mỗi parameter.
- Chặn key trùng.
- Chặn null, `NaN`, infinity và số ngoài miền biểu diễn cần thiết.
- Giữ `BigDecimal` qua bước parse/normalize; chỉ đổi sang `double` tại biên solver khi model thực sự dùng double.
- Error message phải nêu đúng canonical key.
- Không chứa alias matching trong `CanonicalQuantityBag`.

### 5.2 Strict validator trước Jackson binding

AI hiện chỉ được yêu cầu trả JSON object. Bổ sung validation nghiêm ngặt trước `treeToValue`:

- root phải là object;
- `schemaVersion`, `schemaId`, `topic` đúng kiểu;
- `objects`, `quantities`, `relations`, `ambiguities` đúng kiểu array;
- `value`, `normalizedValue`, `confidence` phải là JSON number, không nhận chuỗi số;
- các field bắt buộc phải tồn tại;
- unknown field phải bị từ chối hoặc được quản lý bởi một policy versioned rõ ràng;
- không cho duplicate quantity sau canonicalization;
- không cho duplicate ambiguity code/fieldPath chưa giải quyết;
- giới hạn độ dài string, số object, quantity, relation và ambiguity;
- không cho số quá lớn gây overflow/DoS;
- không tin `normalizedValue` và `normalizedUnit` do AI gửi; backend tự chuẩn hóa từ `value` và `originalUnit`;
- nếu provider hỗ trợ JSON Schema strict, gửi schema strict; backend vẫn phải tự validate, không tin provider tuyệt đối.

Tắt scalar coercion riêng cho luồng AI thay vì thay đổi `ObjectMapper` toàn application nếu việc đó gây regression cho API khác.

### 5.3 Canonicalization chỉ ở boundary

Sửa `canonicalQuantityKey`:

- canonical key hợp lệ -> trả canonical key;
- alias hợp lệ -> trả canonical key;
- unknown key -> trả failure có cấu trúc hoặc throw contract exception;
- không trả nguyên `raw` cho unknown key;
- kiểm tra alias collision trong schema lúc compile;
- kiểm tra key/symbol/alias không nhập nhằng trong cùng schema.

Sau bước này, runtime chỉ chấp nhận canonical key. `SchemaDefinitionService.validateSpecification()` không được tiếp tục dò aliases/symbol như một fallback bí mật.

### 5.4 Legacy adapter

`PhysicsValues` hiện đọc cả `quantities[]` và field root. Tách tương thích cũ thành adapter rõ ràng:

```text
LegacySpecificationAdapter
  input: persisted legacy specification/version
  output: canonical specification hoặc CanonicalQuantityBag
```

Yêu cầu:

- chỉ kích hoạt theo contract/schema version hoặc dấu hiệu payload cũ đã xác định;
- ghi metric/log an toàn khi legacy path được dùng;
- không để solver biết payload là cũ;
- giữ adapter ID cũ có hậu tố chiều nếu dữ liệu đã persist, nhưng output canonical không phát sinh hậu tố mới;
- có fixture replay từ payload cũ;
- bỏ root-field fallback khỏi `PhysicsValues` sau khi adapter đã được test;
- chuyển test hiện tại sang payload production; test legacy chỉ nằm trong suite adapter.

## 6. Workstream B — Schema compiler thay cho đọc `JsonNode` rải rác

Tạo `CompiledSchema` hoặc cấu trúc typed tương đương, được build một lần khi schema được approve/load:

```text
CompiledSchema
  identity: schemaId, version, topic, modelId
  quantities: Map<key, QuantityDefinition>
  aliases: Map<alias, canonicalKey>
  adjustments: Map<key, AdjustmentDefinition>
  outputs: Map<key, OutputDefinition>
  execution: ExecutionDefinition
  validation: ValidationDefinition
  visualization contract
  solver binding
  checksum
```

Schema compiler phải kiểm tra:

- `schemaId`, version, topic và model ID hợp lệ;
- canonical key duy nhất;
- alias duy nhất, không xung đột canonical key khác;
- `allowedUnits` tồn tại trong unit catalog;
- default đúng kiểu, đúng unit canonical và đúng miền;
- không đồng thời khai báo constraint mâu thuẫn;
- integer/default/min/max hợp lệ;
- adjustable parameter phải trỏ tới quantity có thật;
- min/max/step hữu hạn, `min <= max`, `step > 0`;
- solver và reference implementation tồn tại;
- output key duy nhất;
- visualization source chỉ trỏ tới output đã khai báo;
- `probeSeries`, event binding và end-condition binding chỉ trỏ tới output hợp lệ;
- validation checkpoint nằm trong `(0, 1]`, duy nhất và tăng dần;
- tolerance hợp lệ theo từng output;
- scene/vector binding và resource limits hiện tại vẫn được giữ.

Tách `SchemaDefinitionService` thành các trách nhiệm nhỏ, ví dụ:

```text
SchemaRegistry                 // lookup/version/lifecycle
SchemaCompiler                 // JSON -> CompiledSchema
QuantityContractValidator      // input contract
AdjustmentResolver             // controls/defaults/overrides
VisualizationContractValidator // scene/vector contract
SolverBindingRegistry          // binding lookup
```

Không bắt buộc đúng tên trên, nhưng không để một service tiếp tục làm tất cả các nhiệm vụ hiện tại.

## 7. Workstream C — Module registry không switch trung tâm

Thiết kế một module contract typed theo model, ví dụ:

```java
public interface PhysicsModule<P> {
    String modelId();
    String numericalSolverId();
    String referenceSolverId();
    P bind(CanonicalQuantityBag quantities);
    SolverOutput solve(P parameters, SimulationClock clock);
    AnalyticalPoint reference(P parameters, double timeSeconds);
}
```

Có thể tách numerical/reference thành hai interface nếu cần bảo đảm độc lập. Điều bắt buộc:

- registry là immutable `Map`, không phải tìm tuyến tính trên `List`;
- fail startup nếu trùng model ID, solver ID hoặc reference ID;
- fail startup nếu catalog trỏ đến implementation không tồn tại;
- thêm model mới bằng thêm module/bean, không sửa switch tổng hợp;
- module nằm đúng package topic;
- không có `ApplicationsSolver` trong `electromagnetism` xử lý circuits, waves, modern và practical;
- không có wildcard import toàn bộ model packages;
- các multi-model solver chỉ được giữ nếu chúng thực sự cùng một family/algorithm và dispatch được cấu hình typed, không switch string mở rộng vô hạn.

Tách các model đang nằm trong `ApplicationsSolver` và `ApplicationsReferenceSolver` về topic đúng:

- thermistor, diode, sensor/op-amp -> circuits;
- radio communication/signal chain -> waves hoặc communications package rõ ràng;
- ultrasound -> waves/medical theo ownership đã thống nhất, không import chéo tùy tiện;
- eclipse -> modern/astronomy;
- energy environment -> practical/data;
- mỗi model có module/binding rõ ràng.

Tương tự, xem xét và giảm switch trong:

- `MechanicsFoundationSolver`;
- `KinematicsSolver`;
- `DynamicsSolver`;
- `MedicalImagingSolver`;
- `OpticalInstrumentSolver`;
- `GasProcessSolver`;
- các reference solver tương ứng.

## 8. Workstream D — Parameter record chỉ chứa dữ liệu

Parameter record chịu trách nhiệm:

- nhận canonical values qua binder;
- thể hiện kiểu dữ liệu đầu vào của model;
- giữ invariant cơ bản sau construction.

Không để parameter record vừa parse `JsonNode`, vừa đoán default, vừa chứa toàn bộ nghiệm/công thức như hiện tại.

Mục tiêu dạng:

```java
public record HydrostaticsParameters(
        double fluidDensity,
        double depth,
        double displacedVolume,
        double gravity,
        double atmosphericPressure) {}

final class HydrostaticsParameterBinder
        implements ParameterBinder<HydrostaticsParameters> {
    public HydrostaticsParameters bind(CanonicalQuantityBag q) { ... }
}
```

Tạo helper validation dùng chung, ví dụ:

```java
PhysicalChecks.finite(value, key);
PhysicalChecks.positive(value, key);
PhysicalChecks.nonNegative(value, key);
PhysicalChecks.integer(value, key);
PhysicalChecks.range(value, min, max, key);
```

Giữ validation quan hệ đặc thù trong binder/model validator, ví dụ `finalTemperature > initialTemperature` hoặc `finalLevel < initialLevel`. Không biến mọi validation thành metadata nếu logic quan hệ sẽ khó đọc hơn code Java.

Default phải được materialize từ schema tại boundary. Không lặp default như `9.81`, `101325`, sample count hoặc phase ở cả schema và parameter class. Derived default phụ thuộc input khác phải được biểu diễn bằng code có tên rõ ràng và test, không dùng `Double.NaN` làm sentinel nếu có thể dùng optional/typed resolution.

## 9. Workstream E — Sửa đúng học thuật dao động tắt dần/cưỡng bức

Audit và sửa `DampedForcedOscillationParameters` cùng solver/reference liên quan.

Schema hiện khai báo `damping_coefficient` đơn vị `kg/s`, tức hệ số `c` trong:

```text
m x'' + c x' + kx = F0 cos(ωt)
```

Nếu giữ contract này, mọi công thức phải dùng:

```text
ω0 = sqrt(k/m)
γ = c/(2m)
ωd = sqrt(ω0² - γ²)
A = (F0/m) / sqrt((ω0² - ω²)² + (2γω)²)
φ = atan2(2γω, ω0² - ω²)
```

Phân loại:

```text
under-damped: γ < ω0
critical:     γ = ω0  <=> c = 2 sqrt(km)
over-damped:  γ > ω0
```

Yêu cầu:

- không so sánh trực tiếp `c [kg/s]` với `ω0 [1/s]`;
- không dùng `exp(-c*t)`;
- kiểm tra dấu và điều kiện đầu;
- kiểm tra giới hạn `c -> 0`, `F0 -> 0`, resonance và mass khác 1;
- reference oracle không gọi lại `stateAt()` hoặc formula kernel production;
- thêm golden case tính tay hoặc từ nguồn học thuật đáng tin;
- test hiện tại dùng `mass = 1` không đủ; bắt buộc thêm `mass != 1` để bắt lỗi dimension;
- ghi rõ quy ước `c` hay `γ` trong tài liệu model và schema; không dùng một tên với unit của đại lượng khác.

Audit các model khác để tìm cùng loại lỗi: schema unit một kiểu nhưng code dùng như đại lượng khác. Tạo automated dimension/contract checks khi khả thi.

## 10. Workstream F — Reference oracle phải độc lập

Reference solver được phép dùng chung:

- immutable parameter DTO;
- hằng số vật lý chuẩn;
- unit-normalized input;
- tên output typed.

Reference solver không được dùng chung:

- hàm nghiệm/công thức production đang được kiểm tra;
- numerical integration routine;
- `stateAt()` của production model;
- output builder chứa phép tính vật lý;
- branch logic quyết định kết quả vật lý.

Với model giải tích:

- triển khai công thức reference độc lập hoặc đối chiếu golden values tính tay/nguồn chuẩn;
- test mutation-style: cố ý thay đổi dấu/hệ số trong numerical path phải làm validation fail.

Với model số:

- dùng phương pháp độc lập, nghiệm giải tích giới hạn, refinement/convergence hoặc invariant;
- reference step phải nhỏ hơn/độc lập hợp lý;
- không gọi numerical solver rồi đổi tên thành reference.

Thêm architecture test cấm package `physics.reference` import `physics.solver`. Nếu parameter DTO chứa formula production thì reference cũng không được gọi formula đó.

## 11. Workstream G — Output contract typed và hiệu quả

Hiện `SolverOutput` dùng nhiều `Map<String, List<Double>>`. Bổ sung output contract được compile từ schema.

Trước khi persist, kiểm tra:

- timeline hữu hạn, tăng đơn điệu và trong resource limit;
- mọi series có độ dài hợp lệ;
- mọi giá trị hữu hạn, trừ trường hợp undefined được contract biểu diễn tường minh;
- output key thuộc schema;
- mọi required output tồn tại;
- output nằm đúng kind/group;
- scalar field đúng axes/shape/unit/sampling;
- không có output thừa do typo;
- unit của output được khai báo.

Hỗ trợ rõ các loại:

```text
ScalarOutput
TimeSeriesOutput
ScalarFieldOutput
```

Không nhân một kết quả tĩnh thành hàng trăm phần tử bằng `Collections.nCopies` nếu client có thể nhận scalar. Nếu phải giữ response cũ, dùng adapter serialize scalar thành legacy time series ở API boundary; domain output không được giả dạng.

Tạo `SimulationTimeline`/`SamplingGrid` trong runtime common. Không để solver topic khác gọi `TemperatureScaleSolver.staticTime()`.

Tạo output builder dùng chung chỉ cho thao tác kỹ thuật, không chứa công thức vật lý.

## 12. Workstream H — Validation có ý nghĩa học thuật

Thay một tolerance chung bằng tolerance theo output:

```json
{
  "key": "displacement",
  "absoluteTolerance": 1e-9,
  "relativeTolerance": 1e-6,
  "comparison": "numeric"
}
```

Yêu cầu:

- gần 0 dùng absolute tolerance;
- vùng khác kết hợp absolute và relative tolerance;
- boolean/discrete state dùng exact/enum comparison;
- angle hỗ trợ periodic comparison khi cần;
- checkpoint hợp lệ, duy nhất, tăng dần;
- validation báo missing/extra/non-finite series rõ ràng;
- validation không pass khi reference trả rỗng;
- validation ghi numerical value, expected, absolute error, relative error và tolerance áp dụng;
- invariant phù hợp từng model: bảo toàn động lượng, cân bằng năng lượng, giới hạn zero, symmetry, monotonicity, dimensional consistency;
- không áp invariant sai giả thiết, ví dụ bảo toàn cơ năng cho hệ có damping.

Mỗi model production phải có ít nhất:

1. input/domain tests;
2. golden case độc lập;
3. boundary/zero case;
4. invalid input case;
5. output contract test;
6. numerical/reference comparison phù hợp;
7. invariant hoặc metamorphic test nếu áp dụng.

## 13. Workstream I — End condition theo strategy và schema binding

Tách `EndConditionResolver` thành các thành phần nhỏ:

```text
EndConditionParser
EndConditionValidator
TimeLimitResolver
ThresholdResolver
ContactResolver
CollisionResolver
CycleResolver
OutputTrimmer
SeriesLocator
```

Yêu cầu:

- loại condition là enum/value object, không switch string rải rác;
- operator là enum allowlist;
- contact/collision series phải được schema binding rõ;
- không dò event bằng `key.contains("contact")` hoặc `key.contains("collision")`;
- `SeriesLocator` chỉ resolve output đã khai báo;
- interpolation dùng chung một implementation được test;
- cycle detection nêu rõ giả thiết và fail an toàn với tín hiệu không tuần hoàn;
- giới hạn horizon và resource guard vẫn được giữ;
- trim output không phá scalar field shape/timeline;
- giữ compatibility với persisted end condition cũ qua parser adapter.

## 14. Workstream J — Catalog module hóa và chống drift DB

Tách catalog theo topic/model:

```text
backend/src/main/resources/schemas/
  kinematics/
  dynamics/
  circuits/
  waves/
  thermal/
  optics/
  electromagnetism/
  modern/
  practical/
```

Mỗi schema là một file versioned dễ review. Có build/verification script tổng hợp nếu runtime vẫn cần `catalog.json`.

Catalog compiler phải:

- kiểm tra JSON schema;
- kiểm tra ID trùng;
- kiểm tra binding mồ côi;
- kiểm tra unit/output/scene references;
- sắp xếp deterministic;
- sinh checksum;
- fail CI nếu generated catalog khác source files;
- không yêu cầu sửa tay cả source lẫn generated artifact.

Sửa bootstrap:

- cùng `schemaId + version` và cùng checksum -> idempotent;
- cùng identity nhưng checksum khác -> fail startup với hướng dẫn tăng version;
- không âm thầm bỏ qua catalog đã thay đổi;
- production không tự approve nội dung mới ngoài policy rõ ràng;
- schema và solver version phải nhất quán;
- implementation ID phải tồn tại trong registry;
- không sửa bản published; tạo version mới.

Nếu cần DB column checksum, thêm migration tăng thêm và backfill an toàn.

## 15. Workstream K — Tách service và tối ưu repository

Tách `ProblemService` theo use case, tối thiểu phân biệt:

```text
ProblemCommandService
ProblemQueryService
SourceAssetService
ExtractionWorkflow
SpecificationEditor
AmbiguityWorkflow
```

Không bắt buộc tạo đúng sáu class nếu một số trách nhiệm nhỏ, nhưng service orchestration không được tiếp tục chứa CRUD, upload, OCR, hashing, AI và specification mutation cùng lúc.

Sửa `SchemaService`/registry lookup:

- không `findAll().stream()` để tìm một schema;
- thêm repository query đúng identity/lifecycle/topic;
- tránh tải toàn bộ topics cho mỗi request;
- dùng unique constraints hiện có;
- normalize case/ID tại boundary nhất quán;
- cache immutable compiled approved schema theo version, invalidation khi lifecycle/version thay đổi.

Không cache entity JPA mutable ngoài transaction. Cache compiled immutable representation.

## 16. Workstream L — Hằng số, encoding và thông báo lỗi

Tạo `PhysicalConstants` có nguồn/phiên bản rõ ràng cho các giá trị chuẩn bị lặp:

- speed of light;
- Planck constant;
- elementary charge;
- Boltzmann constant;
- gravitational constant;
- standard gravity nếu dùng như constant mặc định;
- các hằng số học thuật khác thực sự dùng chung.

Không gom các default theo bài vào `PhysicalConstants`; default thuộc schema.

Sửa chuỗi tiếng Việt mojibake trong `SpecificationReadinessService`. Chuyển text teacher-facing sang message resource/i18n hoặc message factory UTF-8. Không hardcode câu tiếng Việt lỗi encoding trong service.

Chuẩn hóa exception taxonomy:

```text
SpecificationContractException
UnknownQuantityException
UnitNormalizationException
PhysicalDomainException
SolverBindingException
OutputContractException
ValidationException
```

Map exception sang HTTP status phù hợp, không lộ stack trace/secrets và không biến mọi lỗi thành một message chung khó debug.

## 17. Workstream M — Test phải đi qua production path

Giữ unit tests công thức, nhưng bổ sung tests toàn tuyến:

```text
raw AI JSON
  -> strict validation
  -> canonicalization
  -> unit conversion
  -> default materialization
  -> parameter binding
  -> solver
  -> output validation
  -> reference validation
```

Chuyển các test đang đặt quantity trực tiếp ở root sang `quantities[]` canonical giống production. Chỉ suite legacy adapter được dùng root payload.

Test bắt buộc:

- AI trả chuỗi số thay number -> reject;
- unknown quantity -> reject;
- alias hợp lệ -> canonicalize một lần;
- alias collision trong schema -> schema không được approve;
- duplicate canonical quantity -> reject;
- unsupported unit -> reject;
- equivalent units -> cùng canonical value;
- default chỉ lấy từ schema;
- override chỉ cho adjustable key;
- registry trùng ID -> fail startup;
- catalog binding tới solver thiếu -> fail;
- output typo/missing/extra/non-finite/wrong length -> fail;
- legacy replay -> kết quả không đổi;
- current specification -> không đi qua legacy adapter;
- reference independence architecture rule;
- mutation/deliberate numerical error -> validation fail;
- damping với `mass != 1` và ba damping regimes;
- per-output absolute/relative/discrete tolerance;
- end-condition strategy và schema-declared series;
- checksum drift cùng version -> fail;
- lifecycle/version snapshot replay giữ nguyên.

Không dùng test “numerical bằng reference” nếu hai bên gọi chung công thức. Thêm giá trị expected độc lập.

## 18. Không được làm

- Không chuyển công thức vật lý sang AI prompt.
- Không dùng `eval`, script hoặc expression tùy ý từ schema.
- Không tạo universal formula interpreter để né viết solver Java.
- Không tiếp tục thêm `matchesXXX()` hoặc danh sách alias vào `PhysicsValues`.
- Không thêm switch trung tâm theo `schemaId`, lesson ID hoặc model ID.
- Không dùng reflection scan để gọi method công thức theo tên string.
- Không để frontend tự tính đáp án chuẩn thay backend.
- Không xóa reference solver chỉ vì khó giữ độc lập.
- Không đánh dấu reference “independent” khi nó gọi cùng formula method.
- Không hardcode default ở parameter nếu schema đã sở hữu default.
- Không thay đổi public API ngay lập tức nếu có dữ liệu cũ; dùng versioned adapter.
- Không đổi tên token Canvas API hoặc Java numeric suffix `1d` vì chúng không phải public physics model ID.
- Không đưa lại hậu tố chiều vào schema/model/asset ID.
- Không tự đánh dấu official curriculum coverage 100% nếu audit nguồn chính thức chưa chứng minh.

## 19. Đầu ra mong muốn

Coding agent phải bàn giao đầy đủ:

1. Code refactor chạy được, không chỉ tài liệu thiết kế.
2. Typed canonical input pipeline và strict AI response validation.
3. Legacy adapter có tests và phạm vi kích hoạt rõ ràng.
4. Compiled schema và các validator/service đã tách trách nhiệm.
5. Physics module registry dạng immutable map, fail duplicate/missing binding.
6. Các solver đa topic được tách về package đúng.
7. Parameter records không còn phụ thuộc raw `JsonNode` trong runtime mới.
8. Công thức damping được sửa đúng dimension và có reference độc lập.
9. Reference solvers không tự xác nhận production formula.
10. Typed output validation và hỗ trợ scalar/time-series/field hợp lý.
11. Per-output validation tolerances và báo cáo sai số đầy đủ.
12. End-condition strategies không dò event bằng substring.
13. Catalog tách theo topic/model, có compiler/checksum/drift protection.
14. Service orchestration và repository lookup được làm gọn.
15. Physical constants dùng chung và text UTF-8 sạch.
16. Tests unit, contract, integration, architecture và backward compatibility.
17. ADR mô tả quyết định kiến trúc và giới hạn.
18. Báo cáo refactor ghi rõ:
    - file/class đã thêm, sửa, di chuyển;
    - duplication/switch/wildcard import trước và sau;
    - compatibility giữ bằng cách nào;
    - lỗi học thuật đã sửa;
    - test đã chạy và kết quả;
    - rủi ro còn lại nếu có.

Tài liệu tối thiểu:

```text
docs/adr/<next>-canonical-physics-input.md
docs/adr/<next>-physics-module-registry.md
docs/adr/<next>-independent-reference-validation.md
docs/implementation/backend-physics-refactor-report.md
docs/physics/models/damped-forced-oscillation.md
```

## 20. Điều kiện thành công bắt buộc

Chỉ được kết luận hoàn thành khi tất cả điều kiện sau đạt:

### Contract đầu vào

- [ ] Solver/reference không nhận raw `JsonNode` trong contract runtime mới.
- [ ] Mỗi request compile quantity đúng một lần thành immutable lookup map.
- [ ] Unknown và duplicate quantity bị từ chối.
- [ ] Alias chỉ được xử lý ở ingress boundary.
- [ ] AI numeric string không được tự ép thành number.
- [ ] Backend tự tính normalized value/unit.
- [ ] Default có một nguồn sự thật từ schema.
- [ ] Legacy payload chỉ đi qua adapter versioned.

### Kiến trúc module

- [ ] Registry dùng immutable map và fail khi ID trùng.
- [ ] Catalog binding thiếu implementation làm startup/CI fail.
- [ ] Không còn solver/reference đa topic trong package sai.
- [ ] Thêm model mới không cần sửa switch trung tâm.
- [ ] Không còn wildcard import hàng loạt trong physics production code.
- [ ] Không còn dependency chung vào `TemperatureScaleSolver.staticTime()`.

### Đúng học thuật

- [ ] Damping coefficient đúng dimension và công thức.
- [ ] Test `mass != 1` pass.
- [ ] Reference oracle không gọi production formula đang kiểm tra.
- [ ] Có golden/invariant/boundary evidence cho từng model được migrate.
- [ ] Mutation hoặc lỗi cố ý trong numerical path bị validation phát hiện.

### Output và validation

- [ ] Output key/length/finiteness/kind/unit được kiểm tra trước persist.
- [ ] Scalar không bị bắt buộc giả thành repeated timeseries trong domain model mới.
- [ ] Tolerance theo output, có absolute và relative.
- [ ] Discrete output không dùng relative floating comparison.
- [ ] End condition dùng binding khai báo, không dò substring.
- [ ] Replay trim giữ đúng series và scalar field contract.

### Catalog và persistence

- [ ] Catalog source được chia nhỏ, generated artifact deterministic.
- [ ] Cùng version nhưng khác checksum bị phát hiện.
- [ ] Published version không bị mutate.
- [ ] Run snapshot cũ replay không đổi kết quả.
- [ ] Không có destructive migration.

### Chất lượng code

- [ ] `SchemaDefinitionService`, `ProblemService`, `EndConditionResolver` không còn giữ các trách nhiệm không liên quan đã nêu.
- [ ] Không còn mojibake trong source/message hiển thị.
- [ ] Không còn alias/default/output ID được sao chép vô lý qua nhiều layer.
- [ ] Exception có loại và message hành động được.
- [ ] `git diff --check` sạch.

### Kiểm thử và build

Chạy tối thiểu:

```powershell
cd backend
.\mvnw.cmd -q clean test

cd ..\react-client
npm run check

cd ..
node scripts/check-scene-contracts.mjs
node scripts/check-curriculum-coverage.mjs
node scripts/report-topic-coverage.mjs
git diff --check
```

- [ ] Backend clean test pass.
- [ ] Frontend architecture/lint/test/build pass.
- [ ] Scene contract không có error.
- [ ] Curriculum coverage không giảm do đổi ID/binding.
- [ ] Topic coverage không giảm.
- [ ] Không có public identifier mới chứa hậu tố chiều.
- [ ] Không có regression API/replay/assignment snapshot.
- [ ] Warning còn lại được phân loại; không giấu warning mới do refactor.

## 21. Cách báo cáo cuối cùng

Báo cáo trung thực theo cấu trúc:

1. Kết quả đã hoàn thành.
2. Các lỗi kiến trúc đã loại bỏ.
3. Các lỗi vật lý đã sửa và căn cứ công thức.
4. Compatibility và migration.
5. Kết quả từng lệnh test/check.
6. Số liệu trước/sau về duplication, switch, wildcard import và legacy fallback.
7. Phần chưa hoàn thành hoặc chưa có bằng chứng, nếu có.

Không dùng các câu “production-ready”, “100% đúng” hoặc “hoàn thành toàn bộ” nếu còn bất kỳ checkbox bắt buộc nào chưa đạt. Nếu bị chặn bởi dữ liệu/quyền truy cập/nguồn học thuật, tiếp tục hoàn thành các phần không bị chặn rồi ghi chính xác blocker và bằng chứng cần bổ sung.
