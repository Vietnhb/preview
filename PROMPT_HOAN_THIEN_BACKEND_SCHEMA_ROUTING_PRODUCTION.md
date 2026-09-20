# Prompt duy nhất: Hoàn thiện Backend Physics và luồng xác định schema cho AI ở mức production

> Đây là prompt triển khai bắt buộc dành cho coding agent làm việc trực tiếp trong repository PhysLive. Thực hiện liên tục cho đến khi toàn bộ điều kiện thành công ở cuối tài liệu đều đạt. Không lập timeline, không chia thành kế hoạch theo ngày/tuần, không dừng ở prototype và không tuyên bố production-ready chỉ vì build hoặc một nhóm test đã pass.

## 1. Vai trò và mục tiêu

Bạn là kỹ sư trưởng chịu trách nhiệm hoàn tất kiến trúc backend physics và toàn bộ luồng xác định schema trước khi gọi AI.

Kết quả cuối cùng phải đạt đồng thời:

1. Không gửi toàn bộ catalog schema cho AI.
2. Schema ứng viên được tìm bằng hybrid retrieval dựa trên dữ liệu: BM25 + embedding/pgvector + Reciprocal Rank Fusion (RRF) + kiểm tra contract vật lý.
3. Không hardcode từ khóa, topic, schema ID, quantity ID, unit hoặc luật chọn schema trong `if`, `switch`, `contains` hay regex theo từng bài học.
4. Thêm schema mới bằng dữ liệu/version mới; không sửa router trung tâm, solver switch trung tâm hoặc prompt cố định.
5. AI chỉ trích xuất dữ kiện từ một tập schema ứng viên nhỏ; AI không tính công thức, không phát sinh code và không tự tạo schema ID ngoài candidate set.
6. Backend là nguồn quyết định cuối: pin schema/version, canonicalize key, normalize unit, materialize default, validate miền vật lý rồi mới gọi solver.
7. Solver và reference runtime không nhận raw `JsonNode`, alias hoặc payload AI.
8. Numerical solver và reference oracle độc lập đủ để phát hiện lỗi công thức.
9. Output, end condition, persistence snapshot, replay và catalog lifecycle đều có contract typed, versioned và được kiểm thử.
10. Toàn bộ backend chạy được, migration chạy được, test/build/contract/curriculum gate pass và có báo cáo bằng chứng cuối cùng.

Prompt này kế thừa các yêu cầu chưa hoàn thành trong `PROMPT_REFACTOR_BACKEND_PHYSICS_PRODUCTION.md`, nhưng phải được thực hiện như một đặc tả hoàn chỉnh, không dùng kết luận cũ thay cho kiểm tra repository hiện tại.

## 2. Nguyên tắc bắt buộc

- Đọc `AGENTS.md` nếu có và đọc đầy đủ file liên quan trước khi sửa.
- Chạy `git status`; không reset, checkout hoặc xóa thay đổi của người dùng.
- Đo baseline trước khi sửa và phân biệt lỗi có sẵn với regression mới.
- Dùng migration tăng dần; không sửa migration đã chạy và không mutate dữ liệu production ngoài migration/backfill có kiểm soát.
- Giữ lịch sử schema, solver binding, simulation run và assignment snapshot.
- Compatibility cũ phải đi qua adapter versioned có test replay; không để compatibility lan vào runtime mới.
- Không dùng reflection/service locator/string magic để che hardcode.
- Không đưa công thức vật lý sang JSON, frontend, embedding document hoặc prompt AI.
- Không thêm public ID có hậu tố `1d`/`2d`; mặt phẳng là mặc định. Không đổi token kỹ thuật bắt buộc như Canvas `getContext("2d")` hay literal Java `1d` kiểu double.
- Không dùng AI hoặc embedding làm nguồn đúng duy nhất. Retrieval chỉ đề xuất ứng viên; compiled schema và backend validation quyết định tính hợp lệ.
- Không log toàn bộ đề bài, ảnh, prompt hoặc dữ liệu nhạy cảm. Telemetry chỉ lưu ID/version, score, latency, trạng thái và mã lỗi an toàn.
- Không tuyên bố hoàn thành nếu còn bất kỳ checkbox bắt buộc nào chưa đạt.

## 3. Hiện trạng phải đo lại

Không tin số liệu cũ nếu repository đã thay đổi. Trước khi sửa, ghi lại tối thiểu:

- Số schema, schema version, topic, solver và reference implementation.
- Số solver/reference còn nhận `JsonNode`.
- Số parameter class còn `from(JsonNode, Map...)`.
- Số lần gọi `PhysicsValues.require/optional` trên production path.
- Số switch dispatch theo `modelId` hoặc `schemaId`.
- Các solver ID/reference ID được nhiều model dùng chung.
- Số schema đang bind vào compatibility `applications_solver`/`applications_reference`.
- Các reference oracle gọi chung công thức với numerical path.
- Các default, constant, output key và alias bị khai báo lặp.
- Kích thước catalog và token ước tính hiện đang gửi cho AI.
- Query repository dạng `findAll()` rồi lọc trong memory trên request path.
- Mojibake và public identifier chứa hậu tố chiều.
- Backend clean test, frontend check, scene contract, curriculum coverage và topic coverage.

Ghi baseline và kết quả cuối vào `docs/implementation/backend-schema-routing-production-report.md`.

## 4. Kiến trúc đích bắt buộc

```text
Problem text / OCR text
  -> Query normalization (Unicode, symbols, unit tokens; không luật theo schema)
  -> Dynamic SchemaSearchDocument index
  -> BM25 lexical retrieval
  -> Embedding generation
  -> pgvector cosine retrieval
  -> Reciprocal Rank Fusion
  -> Schema-contract reranker/verifier
  -> Candidate decision policy
       high confidence -> top candidates
       low confidence  -> explicit ambiguity, không đoán
  -> AI receives base extraction contract + only top candidate contracts
  -> Strict JSON validation before Jackson binding
  -> Candidate/schema/version membership verification
  -> Backend unit normalization from raw value + original unit
  -> Canonical quantity resolution at ingress only
  -> Default materialization from compiled schema
  -> Immutable CanonicalQuantityBag
  -> Typed PhysicsModuleRegistry
  -> Model-specific typed parameter binder
  -> Numerical solver
  -> Typed output contract validation
  -> Independent reference/golden/invariant validation
  -> End-condition strategy
  -> Immutable persisted run snapshot
```

Luồng resolve ambiguity phải dùng schema/version đã pin trong specification. Không chạy lại toàn catalog và không gửi lại mọi schema cho AI.

## 5. Cấu trúc package và file sạch

Không dồn thêm code vào `SchemaDefinitionService`, `ProblemService` hoặc `OpenRouterExtractionProvider`. Tạo package theo capability. Có thể điều chỉnh tên theo convention hiện tại, nhưng trách nhiệm phải tương đương:

```text
com.example.backend.schema
  catalog/
    SchemaCatalogLoader
    SchemaCatalogGenerator
  compiler/
    SchemaCompiler
    CompiledSchema
    QuantityDefinition
    OutputDefinition
    ValidationDefinition
  registry/
    SchemaRegistry
    SolverBindingRegistry
  routing/
    model/
      SchemaSearchDocument
      SchemaCandidate
      SchemaRoutingDecision
      RetrievalScore
    index/
      SchemaSearchIndex
      SchemaSearchDocumentBuilder
      SchemaEmbeddingIndexer
    lexical/
      Bm25SchemaRetriever
      UnicodePhysicsTokenizer
    vector/
      EmbeddingClient
      EmbeddingResult
      PgVectorSchemaRetriever
    fusion/
      ReciprocalRankFusion
    verification/
      SchemaContractReranker
      CandidateDecisionPolicy
    service/
      SchemaRoutingService

com.example.backend.ai.extraction
  prompt/
    ExtractionPromptBuilder
    CandidateContractProjection
  validation/
    StrictSpecificationValidator

com.example.backend.physics
  module/<topic>/
  binding/<topic>/
  solver/<topic>/
  reference/<topic>/
  output/
  validation/endcondition/
  compatibility/
```

Không tạo abstraction nếu không có consumer thực. Nếu convention hiện tại yêu cầu package khác, ghi lý do trong ADR và vẫn giữ ranh giới trách nhiệm trên.

## 6. Schema search document động, không hardcode

Tạo `SchemaSearchDocument` immutable và versioned. Nội dung được sinh hoàn toàn từ schema/curriculum metadata đã approve:

```text
schemaId
schemaVersion
topic
name/title
description/learning outcomes nếu có
canonical quantity keys
aliases
symbols
allowed units
relation types
end-condition capabilities
curriculum lesson/module labels có liên kết
source checksum
```

Không đưa các phần sau vào search text/embedding:

- công thức hoặc code solver;
- visualization/scene graph/asset;
- output samples;
- numerical/reference implementation detail;
- dữ liệu người dùng;
- lifecycle metadata không mang nghĩa tìm kiếm.

Yêu cầu:

- Cùng schema/version/checksum phải sinh cùng search document deterministic.
- Unicode normalization bảo toàn ký hiệu vật lý có nghĩa.
- Có representation phù hợp cho `λ`, `Δ`, chỉ số dưới, đơn vị và tên canonical mà không tạo bảng từ khóa theo schema.
- Alias, symbol và unit lấy từ compiled schema/unit catalog; không sao chép sang Java constants.
- Khi schema approve/version mới/checksum hoặc embedding model đổi, index được cập nhật idempotent.
- Khi schema retired, không còn là candidate cho request mới nhưng historical replay vẫn dùng được.

## 7. BM25 lexical retrieval

Triển khai BM25 thực sự hoặc thư viện BM25 được quản lý rõ ràng. Không gọi `contains()` rồi đặt tên là BM25.

Yêu cầu:

- Corpus là toàn bộ `SchemaSearchDocument` đang approved/enabled.
- Tính term frequency, document frequency, document length và average document length đúng công thức BM25.
- `k1`, `b`, topK và giới hạn token nằm trong typed configuration có validation; không rải magic number.
- Tokenizer dùng Unicode normalization và tách được từ, canonical key, symbol, số và unit.
- Stopword nếu dùng phải là resource/config versioned chung cho ngôn ngữ, không chứa luật chọn từng schema.
- Index immutable snapshot, swap atomically sau rebuild.
- Rebuild theo catalog checksum; request không rebuild index.
- Tie-break deterministic bằng schema ID/version.
- Có test công thức BM25 bằng fixture nhỏ tính tay.

Với corpus nhỏ có thể giữ BM25 index trong memory. Không thêm Elasticsearch/OpenSearch nếu chưa có benchmark chứng minh cần thiết.

## 8. Embedding và pgvector

Backend đang dùng PostgreSQL + Flyway. Dùng `pgvector` trong PostgreSQL hiện có; không thêm vector database riêng nếu chưa có bằng chứng vận hành bắt buộc.

### 8.1 Migration

Tạo migration tăng dần sau version hiện tại, ví dụ tên version kế tiếp phù hợp repository. Migration phải tương đương:

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE schema_search_embeddings (
    schema_id         varchar(120) NOT NULL,
    schema_version    varchar(40)  NOT NULL,
    topic             varchar(80)  NOT NULL,
    search_text       text         NOT NULL,
    embedding         vector(<configured-dimension>) NOT NULL,
    embedding_provider varchar(80) NOT NULL,
    embedding_model   varchar(160) NOT NULL,
    embedding_dimension integer    NOT NULL,
    source_checksum   varchar(64)  NOT NULL,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    PRIMARY KEY (schema_id, schema_version, embedding_provider, embedding_model)
);
```

Không copy nguyên SQL trên nếu naming/convention DB hiện tại khác. Dimension là contract của model embedding, được kiểm tra giữa configuration, response và DB. Không âm thầm truncate/pad vector.

HNSW chỉ thêm khi benchmark hoặc ngưỡng dữ liệu cấu hình cho thấy cần. Với vài trăm schema, exact cosine search là mặc định đơn giản và chính xác. Nếu có HNSW, migration/index và operator class phải đúng cosine distance.

Phải có chiến lược môi trường không hỗ trợ extension:

- production/staging fail startup với message hành động được nếu vector routing được bật nhưng extension thiếu;
- test dùng PostgreSQL/Testcontainers có pgvector hoặc adapter fake có contract test tương đương;
- không âm thầm đổi sang random/in-memory result trong production.

### 8.2 Embedding provider abstraction

```java
public interface EmbeddingClient {
    EmbeddingResult embed(String text);
    String providerId();
    String modelId();
    int dimension();
}
```

Yêu cầu:

- Provider/model/dimension/config lấy từ typed properties.
- Timeout, retry có giới hạn, circuit breaker hoặc failure policy rõ ràng.
- Không log raw search text.
- Validate vector không null, đúng dimension, toàn bộ finite.
- Cache query embedding có giới hạn nếu thực sự cần; không cache vô hạn.
- Test dùng fake deterministic embedding, không gọi mạng.
- Đổi provider/model làm index cũ không được dùng nhầm; tạo/reindex theo model identity.

### 8.3 Vector retrieval

- Dùng cosine distance và trả similarity đã định nghĩa rõ.
- Chỉ query schema approved/enabled và đúng embedding model hiện hành.
- Limit/topK lấy từ config có bounds.
- Tie-break deterministic.
- Native query/JDBC mapping nằm trong vector infrastructure package, không rò kiểu pgvector sang domain.
- Repository query có index/constraint phù hợp và không load toàn bộ vector về JVM trên production path.

## 9. Fusion và xác minh candidate

### 9.1 Reciprocal Rank Fusion

Không cộng trực tiếp BM25 score với cosine score vì khác thang đo. Dùng RRF:

```text
rrfScore = Σ 1 / (k + rankFromRetriever)
```

- `k` nằm trong typed configuration.
- Candidate thiếu ở một retriever vẫn được xử lý đúng.
- Không phụ thuộc thứ tự iteration của map.
- Có unit test tính tay và test tie-break.

### 9.2 Schema-contract reranker

Reranker chỉ dùng evidence sinh động từ catalog/unit registry, không dùng luật kiểu:

```java
if (text.contains("ném xiên")) return "projectile_motion";
switch (schemaId) { ... }
```

Các evidence hợp lệ:

- canonical key/alias/symbol có trong search document;
- unit token được nhận diện từ unit catalog;
- relation type/curriculum metadata;
- required quantity coverage;
- unit compatibility;
- contradiction với required/allowed contract.

Mọi weight/threshold phải nằm trong validated configuration hoặc typed policy, có test sensitivity. Không khai báo weight theo từng schema.

### 9.3 Decision policy

Decision phải có ít nhất:

- candidate list đã pin schema ID + version;
- lexical rank/score;
- vector rank/similarity;
- RRF score;
- verification evidence;
- confidence/status;
- reason code an toàn.

Nếu top candidate không đạt minimum evidence hoặc margin với candidate kế tiếp không đủ:

- không đoán schema;
- trả trạng thái ambiguous;
- cho AI xem top candidate hợp lệ để tạo ambiguity hoặc yêu cầu giáo viên xác nhận;
- không mở lại toàn catalog trong prompt.

## 10. Prompt AI tối thiểu và strict

Sửa `OpenRouterExtractionProvider.systemPrompt()` hiện đang serialize toàn bộ approved catalog.

Tách `ExtractionPromptBuilder` và `CandidateContractProjection`. Mỗi request AI chỉ chứa:

- base extraction rules;
- đề bài/OCR text;
- tối đa topK candidate đã cấu hình;
- schema ID, version, topic và model ID;
- required/optional canonical quantities;
- aliases/symbols để hiểu đầu vào;
- accepted input units và domain constraints;
- relation/end-condition contract cần thiết.

Không gửi:

- toàn bộ catalog;
- visualization/scene graph/assets;
- solver/reference ID;
- công thức;
- output values/validation oracle;
- DB/lifecycle nội bộ không cần thiết.

AI response bắt buộc:

- chọn `schemaId` và `schemaVersion` nằm trong candidate set;
- trả JSON đúng strict contract;
- numeric field là JSON number, không phải numeric string;
- quantity name có thể là canonical/alias trong candidate contract, nhưng backend canonicalize đúng một lần;
- trả raw `value` và `originalUnit`; backend không tin normalized value/unit từ AI;
- không tự tính đại lượng suy diễn nếu đề không cung cấp;
- thiếu hoặc mơ hồ thì tạo ambiguity có code/fieldPath duy nhất.

Backend phải validate trước Jackson binding, rồi validate candidate membership trước khi lookup schema. Không cho AI chọn một approved schema khác ngoài candidate set.

Retry chỉ gửi candidate contract cũ và lỗi contract rút gọn; không gửi lại toàn catalog. Resolve ambiguity dùng schema/version đã pin.

## 11. Canonical runtime contract phải hoàn tất

Loại raw JSON khỏi runtime mới:

```java
public interface PhysicsSolver<P> {
    String solverId();
    SolverOutput solve(P parameters, SimulationClock clock);
}

public interface ReferenceSolver<P> {
    String solverId();
    AnalyticalPoint solve(P parameters, double timeSeconds);
}
```

Hoặc module contract typed tương đương. Điều bắt buộc:

- `SimulationService` compile quantities một lần thành immutable `CanonicalQuantityBag`.
- Solver/reference không nhận `JsonNode`, alias, `originalUnit` hoặc root-field fallback.
- Mỗi model có parameter binder nhận canonical bag.
- Parameter record chỉ chứa dữ liệu và invariant cơ bản; không parse JSON và không giữ toàn bộ công thức solver.
- Default lấy từ compiled schema tại ingress, không lặp trong parameter class.
- `PhysicsValues` không còn trên production path. Nếu giữ, chuyển vào compatibility package và chỉ legacy adapter được gọi.
- Legacy adapter kích hoạt bằng contract/schema version rõ ràng, có metric và replay fixture.
- Không có global suffix stripping cho request mới; legacy ID normalization chỉ nằm trong adapter versioned.

## 12. Compiled schema đầy đủ

`CompiledSchema` phải chứa và validate:

- schema identity: ID, version, topic, model ID, lifecycle, checksum;
- quantity definitions, aliases, symbols, units, constraints và defaults;
- adjustments;
- relation/end-condition definitions;
- typed output definitions gồm key/kind/unit;
- execution contract;
- per-output validation/tolerance/comparison;
- visualization references;
- numerical/reference binding;
- resource limits.

Compiler phải:

- nhận identity từ `SchemaVersion`/catalog entry, không đoán từ nested definition thiếu field;
- kiểm tra allowed unit tồn tại trong unit catalog;
- kiểm tra alias collision kể cả case-insensitive ambiguity nhưng vẫn bảo toàn ký hiệu phân biệt như `r` và `R` khi schema cho phép rõ ràng;
- kiểm tra default đúng type/unit/domain;
- kiểm tra output/visualization/end-condition binding;
- kiểm tra solver/reference implementation tồn tại;
- compile một lần theo `schemaId@version@checksum`;
- invalidation cache đúng khi draft/version/lifecycle thay đổi;
- có unit và integration test riêng.

Không tiếp tục để `SchemaDefinitionService` vừa lookup repository, compile, validate quantity, materialize default, validate visualization và resolve controls.

## 13. Module registry và loại switch mở rộng

- Registry numerical/reference/module dùng immutable map và fail startup khi ID trùng.
- Catalog binding thiếu implementation làm startup/CI fail.
- Một model mới được thêm bằng bean/module + schema version, không sửa switch tổng hợp.
- Tách các multi-model solver/reference còn lại thành module đúng topic/model hoặc family strategy typed thực sự dùng chung thuật toán.
- Catalog version mới bind trực tiếp vào topic module; compatibility ID chỉ phục vụ historical replay.
- Không có solver/reference đa topic trong package sai.
- Không wildcard import hàng loạt.
- Không dùng `modelId` string switch mở rộng vô hạn.

## 14. Reference oracle và đúng học thuật

- Reference không gọi numerical solver hoặc formula method đang được numerical path dùng.
- Công thức dùng chung chỉ được chia sẻ nếu đó là constant/domain primitive không thể khiến oracle tự xác nhận lỗi; ghi rõ trong ADR.
- Mỗi model có ít nhất một golden case độc lập từ tính tay hoặc nguồn học thuật đáng tin.
- Có invariant, boundary và invalid-domain test phù hợp.
- Có mutation test hoặc targeted fault-injection chứng minh validation phát hiện numerical result sai.
- Giữ các sửa đúng dimension/formula đã có cho damped forced oscillator và mở rộng nguyên tắc độc lập sang toàn bộ model.
- Không di chuyển công thức sang schema/prompt/frontend.

## 15. Typed output và validation

Thay domain map lỏng bằng output typed hoặc sealed hierarchy tương đương:

```text
PhysicsOutput
  ScalarOutput
  TimeSeriesOutput
  VectorSeriesOutput
  ScalarFieldOutput
```

Compatibility serialization có thể giữ response API cũ qua mapper versioned, nhưng domain mới phải phân biệt kind.

Trước persist phải kiểm tra:

- output key đã khai báo và không dư/trùng;
- kind đúng;
- unit đúng;
- length/shape/axis đúng;
- time strictly increasing, finite và trong resource limit;
- mọi value finite;
- scalar không bị giả thành repeated time series;
- required probe output tồn tại.

Validation phải hỗ trợ per-output:

- absolute tolerance;
- relative tolerance;
- exact/discrete comparison;
- vector/field strategy nếu schema khai báo;
- message có schema ID, model ID, output key, expected, actual và tolerance.

Sửa mọi message thiếu dữ liệu, gồm trường hợp `expected=` không có giá trị.

## 16. End-condition strategy

Tách `EndConditionResolver` thành strategy registry typed:

```text
TimeLimitStrategy
ThresholdStrategy
EventStrategy
CycleCountStrategy
ManualStrategy
```

- Dispatch theo enum/type đã compile, không substring guessing.
- Source binding trỏ tới typed output definition, không parse group/key tùy tiện ở runtime.
- Event marker phải khai báo trong schema.
- Crossing interpolation deterministic.
- Dynamic horizon bị giới hạn.
- Trim giữ đúng scalar, timeseries, vector và scalar-field shape/axis.
- Mỗi strategy có unit test và integration test với simulation path.

## 17. Catalog module hóa và chống drift

Chia source catalog theo topic/model, ví dụ:

```text
backend/src/main/resources/schemas/source/
  kinematics/
  dynamics/
  circuits/
  waves/
  thermal/
  optics/
  electromagnetism/
  modern/
  practical/

backend/src/main/resources/schemas/generated/catalog.json
```

- Có generator deterministic và validation trước generation.
- Generated artifact được CI kiểm tra không drift.
- Published schema cùng version nhưng khác checksum làm startup/CI fail.
- Khi checksum DB đang null, backfill chỉ sau khi so sánh canonicalized stored definition với source; không ghi checksum source để hợp thức hóa definition khác.
- Solver binding cùng version cũng có checksum và drift detection cho solver ID, reference ID và output contract.
- Published version không mutate; thay đổi tạo version mới.
- Không destructive migration.
- Mojibake trong catalog và message hiển thị phải được sửa về UTF-8 có test phát hiện tái diễn.

## 18. Service, repository và lỗi

Tách trách nhiệm:

- `ProblemService`: orchestration, không tự làm upload/OCR/extraction/canonicalization/persistence detail trong một class.
- `SchemaDefinitionService`: thay bằng registry/compiler/validators/resolvers nhỏ.
- `EndConditionResolver`: strategy như trên.
- `OpenRouterExtractionProvider`: chỉ provider transport; prompt building/routing/validation nằm ngoài.

Repository:

- không `findAll()` rồi lọc trong request path;
- lookup đúng identity/lifecycle/version bằng indexed query;
- tránh N+1 và repeated schema lookup trong một simulation;
- dùng một immutable runtime context chứa compiled schema + binding + canonical bag.

Tạo exception taxonomy có mapping HTTP rõ ràng, ví dụ:

```text
SchemaRoutingException
SchemaCompilationException
CanonicalContractException
SolverBindingException
PhysicsDomainException
OutputContractException
EmbeddingUnavailableException
```

Message phải hành động được và chứa identity cần thiết, không lộ dữ liệu nhạy cảm.

## 19. Configuration và vận hành

Tạo typed validated properties cho routing:

```text
physlive.schema-routing.enabled
physlive.schema-routing.lexical-top-k
physlive.schema-routing.vector-top-k
physlive.schema-routing.candidate-top-k
physlive.schema-routing.rrf-k
physlive.schema-routing.minimum-score
physlive.schema-routing.minimum-margin
physlive.schema-routing.embedding.provider
physlive.schema-routing.embedding.model
physlive.schema-routing.embedding.dimension
physlive.schema-routing.embedding.timeout
```

- Có default an toàn nhưng không lặp ở nhiều layer.
- Reject cấu hình vô lý khi startup.
- Health/readiness phản ánh DB extension, current embedding model và index readiness.
- Metrics tối thiểu: lexical/vector/fusion latency, candidate count, ambiguous rate, selected rank, AI prompt size, embedding failure, legacy adapter usage.
- Không dùng metric label có raw text hoặc cardinality vô hạn.
- Có admin/dry-run reindex command/service idempotent; không tự xóa dữ liệu production.

## 20. Test bắt buộc

### 20.1 Retrieval unit tests

- BM25 fixture tính tay.
- Unicode/Vietnamese/symbol/unit tokenization.
- Vector dimension/null/non-finite rejection.
- Cosine ordering.
- RRF fixture tính tay và deterministic tie-break.
- Contract reranker dùng metadata động, không schema-specific rule.
- Confidence/margin và ambiguous decision.
- Schema mới tự xuất hiện sau index, không sửa router code.

### 20.2 Database/integration tests

- Flyway chạy từ database rỗng và từ schema version trước.
- pgvector extension/table/query hoạt động.
- Index/upsert idempotent theo schema version + checksum + embedding model.
- Retired schema không được route request mới.
- Embedding model đổi không dùng nhầm vector cũ.
- Missing extension/config sai fail rõ ràng.
- Không có destructive mutation dữ liệu lịch sử.

### 20.3 AI flow tests

- Prompt không chứa toàn catalog.
- Prompt chỉ chứa candidate IDs/versions được route.
- Prompt không chứa visualization, solver ID hoặc formula.
- AI chọn schema ngoài candidate set bị từ chối.
- Numeric string/unknown field/duplicate quantity bị từ chối.
- Backend bỏ qua normalized value/unit do AI gửi và tự normalize.
- Retry không mở rộng candidate set.
- Resolve ambiguity giữ schema/version đã pin.
- Prompt size có upper bound.

### 20.4 Physics runtime tests

Mỗi production model cần ma trận phù hợp:

- canonical production payload;
- golden case độc lập;
- boundary case;
- invalid-domain case;
- typed output contract;
- independent reference/invariant;
- mutation/fault detection;
- historical replay nếu có legacy data.

Không để toàn bộ test gọi trực tiếp parameter/solver bằng root-field JSON; test production path phải đi từ routing/extraction boundary đến persist snapshot.

### 20.5 Catalog/curriculum tests

- Generated catalog deterministic.
- Mọi approved schema có search document và embedding đúng model.
- Mọi schema có solver/reference/output/visualization binding hợp lệ.
- Coverage lớp 10, 11, 12 và chuyên đề không giảm.
- Phân biệt rõ internal catalog coverage với official-program evidence; không tuyên bố official 100% nếu chưa có mapping nguồn kiểm chứng.
- Không có public identifier mới chứa hậu tố chiều.

## 21. Cổng kiểm thử cuối

Chạy từ clean state phù hợp repository và lưu exact command/result vào báo cáo:

1. Backend clean test đầy đủ.
2. Test migration PostgreSQL/pgvector.
3. Integration test toàn luồng schema routing → AI fake → canonical runtime → solver → validation → persistence.
4. Frontend architecture/lint/test/build.
5. Scene contract checks.
6. Curriculum coverage checks.
7. Topic coverage report.
8. Catalog generation/checksum drift check.
9. `git diff --check`.
10. Static scan cho raw `JsonNode` trong solver/reference runtime, `PhysicsValues` production call, central model switch, wildcard import, mojibake và public `1d/2d` ID.

Không bỏ qua test fail. Không sửa test chỉ để che behavior sai. Warning còn lại phải được phân loại trong báo cáo.

## 22. Output mong muốn

Sau khi triển khai phải có:

- Migration pgvector an toàn.
- Schema search entity/repository và embedding indexer.
- BM25 retriever, pgvector retriever, RRF và contract reranker.
- Typed routing decision và confidence policy.
- Prompt builder chỉ gửi top candidate contracts.
- Strict candidate membership + AI response validation.
- Canonical runtime hoàn chỉnh, không raw JSON trong solver/reference.
- Typed module registry không central switch.
- Independent reference validation.
- Typed output và end-condition strategies.
- Catalog source phân cấp + deterministic generator.
- Service/repository/exception refactor.
- Unit/integration/replay/migration/physics tests.
- ADR tối thiểu:
  - `docs/adr/0005-hybrid-schema-routing.md`
  - `docs/adr/0006-pgvector-schema-index.md`
  - ADR bổ sung nếu thay public persistence/runtime contract.
- Báo cáo `docs/implementation/backend-schema-routing-production-report.md` gồm baseline, thay đổi, migration/backfill, test evidence, compatibility và rủi ro còn lại.

Nếu tạo file mới, đặt đúng package/topic/capability như mục 5; không đặt class tiện ích chung vào package không có ownership rõ ràng.

## 23. Điều kiện thành công bắt buộc

### Schema routing

- [ ] Không còn serialize toàn bộ approved catalog vào AI prompt.
- [ ] Search document được sinh động từ schema/curriculum metadata.
- [ ] BM25 đúng công thức, không giả lập bằng keyword `contains`.
- [ ] pgvector lưu và truy vấn embedding đúng model/dimension/checksum.
- [ ] RRF hợp nhất rank deterministic.
- [ ] Reranker không chứa luật theo schema ID/topic cụ thể.
- [ ] Low confidence tạo ambiguity, không đoán.
- [ ] Schema mới được route sau approve/index mà không sửa router code.

### AI contract

- [ ] AI chỉ nhận top candidate contracts cần thiết.
- [ ] AI không nhận visualization, solver implementation hoặc formula.
- [ ] AI không thể chọn schema ngoài candidate set.
- [ ] Strict validation chạy trước Jackson binding.
- [ ] Backend tự canonicalize và normalize từ raw value/unit.
- [ ] Retry/ambiguity không gửi lại toàn catalog.

### Canonical physics runtime

- [ ] Solver/reference runtime không nhận raw `JsonNode`.
- [ ] Quantity compile đúng một lần/request thành immutable lookup.
- [ ] Alias chỉ xử lý tại ingress.
- [ ] Default có một nguồn từ compiled schema.
- [ ] Legacy payload chỉ qua adapter versioned có replay test.
- [ ] `PhysicsValues` không còn trên production path.

### Module và học thuật

- [ ] Registry immutable và fail duplicate/missing binding.
- [ ] Thêm model mới không sửa central switch.
- [ ] Không còn solver/reference đa topic sai ownership.
- [ ] Reference oracle không gọi công thức production đang kiểm tra.
- [ ] Mỗi model có golden/invariant/boundary/invalid evidence phù hợp.
- [ ] Mutation/fault injection chứng minh validation phát hiện lỗi.

### Output và end condition

- [ ] Domain output phân biệt scalar/timeseries/vector/field.
- [ ] Key/kind/unit/length/shape/finiteness được kiểm tra trước persist.
- [ ] Tolerance theo output, hỗ trợ absolute/relative/exact/discrete.
- [ ] End condition dùng typed strategy + compiled binding.
- [ ] Trim/replay giữ đúng mọi output contract.

### Catalog và persistence

- [ ] Catalog source chia theo topic/model và artifact generated deterministic.
- [ ] Schema definition và solver binding có checksum drift protection.
- [ ] Null-checksum backfill không hợp thức hóa dữ liệu khác source.
- [ ] Published version không mutate.
- [ ] Historical run/assignment snapshot replay không đổi ngoài migration được chứng minh.
- [ ] Migration không destructive.

### Chất lượng và vận hành

- [ ] Service lớn đã tách đúng trách nhiệm.
- [ ] Request path không `findAll()` rồi lọc.
- [ ] Exception typed và message hành động được.
- [ ] Không còn mojibake trong source/catalog/message hiển thị.
- [ ] Config/health/metrics/reindex có test và không lộ dữ liệu.
- [ ] Không có alias/default/output ID bị sao chép vô lý qua nhiều layer.
- [ ] `git diff --check` sạch.

### Build và kiểm thử

- [ ] Backend clean test pass.
- [ ] PostgreSQL/pgvector migration + integration test pass.
- [ ] Full routing-to-persistence test pass.
- [ ] Frontend architecture/lint/test/build pass.
- [ ] Scene contract không có error.
- [ ] Curriculum/topic coverage không giảm.
- [ ] Không có public dimensional suffix mới.
- [ ] Không regression API/replay/assignment snapshot.
- [ ] Warning còn lại được phân loại, không bị che giấu.

## 24. Cách báo cáo cuối cùng

Báo cáo theo sự thật, gồm:

1. Kiến trúc trước và sau.
2. Danh sách file/package/migration đã thêm hoặc sửa.
3. Cách schema routing hoạt động và vì sao không hardcode.
4. Cách prompt AI được thu nhỏ và bảo vệ candidate membership.
5. Cách canonical runtime loại raw JSON khỏi solver.
6. Bằng chứng reference độc lập và validation phát hiện lỗi.
7. Exact command cùng kết quả test/build/migration/coverage.
8. Compatibility/replay/backfill behavior.
9. Warning/rủi ro còn lại.
10. Bảng toàn bộ checkbox mục 23 với bằng chứng file/test tương ứng.

Nếu còn một điều kiện bắt buộc chưa đạt, ghi rõ `NOT PRODUCTION READY`, tiếp tục sửa và chạy lại cổng kiểm thử; không đổi tiêu chí để hợp thức hóa kết quả.
