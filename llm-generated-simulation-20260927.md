# Kết quả test BE và đầu ra LLM

Đề: 2 vật chuyển động thẳng với vận tốc đầu 15 m/s và gia tốc 2 m/s². Hãy mô phỏng trong 11,5 giây.

Luồng API: đăng nhập → `/api/simulation/understand` → `/api/simulation/generate`.

## Dữ kiện LLM đã trả

```json
{
  "durationSeconds": 11.5,
  "durationParameter": "durationSeconds",
  "physicsCoverage": "COMPLETE",
  "parameters": [
    {
      "name": "initial_position",
      "label": "Vị trí ban đầu (m)",
      "value": 0,
      "unit": "m",
      "min": -1000000,
      "max": 1000000,
      "step": 0.1
    },
    {
      "name": "initial_velocity",
      "label": "Vận tốc ban đầu (m/s)",
      "value": 15,
      "unit": "m/s",
      "min": -1000,
      "max": 1000,
      "step": 0.1
    },
    {
      "name": "acceleration",
      "label": "Gia tốc (m/s²)",
      "value": 2,
      "unit": "m/s^2",
      "min": -100,
      "max": 100,
      "step": 0.1
    },
    {
      "name": "durationSeconds",
      "label": "Thời gian mô phỏng (s)",
      "value": 11.5,
      "unit": "s",
      "min": 0.1,
      "max": 1000,
      "step": 0.1
    }
  ],
  "physicsModels": [
    {
      "id": "body1",
      "label": "Vật 1",
      "capabilityId": "uniform_acceleration",
      "inputs": {
        "initial_position": "initial_position",
        "initial_velocity": "initial_velocity",
        "acceleration": "acceleration",
        "elapsed_time": "durationSeconds"
      }
    },
    {
      "id": "body2",
      "label": "Vật 2",
      "capabilityId": "uniform_acceleration",
      "inputs": {
        "initial_position": "initial_position",
        "initial_velocity": "initial_velocity",
        "acceleration": "acceleration",
        "elapsed_time": "durationSeconds"
      }
    }
  ],
  "externalForces": false,
  "friction": false,
  "conservativeInteractions": false,
  "requiredObjects": [
    {
      "label": "Vật chuyển động",
      "count": 2,
      "shape": "point",
      "role": "moving_body",
      "contextual": false,
      "visualDescription": "điểm đại diện cho mỗi vật"
    }
  ],
  "runtimeKind": null,
  "visualIntent": "Hiển thị quỹ đạo vị trí và vectơ vận tốc của hai vật trong khoảng thời gian 11,5 s.",
  "schemaId": "adaptive_kinematics",
  "topic": "KINEMATICS",
  "topicVersion": "2.2",
  "capabilityId": null,
  "solverBinding": null,
  "displayPlan": null,
  "initialState": null,
  "visualHints": [],
  "visualEffects": [],
  "interactions": [],
  "spatialRelations": [],
  "fixedQuantities": []
}
```

## Scene LLM đã sinh — giữ nguyên

Đây là SVG và binding trình bày; không phải JavaScript tính vật lý. PixiJS ở FE đọc scene này và timeline do BE tính.

```json
{
  "width": 650,
  "height": 200,
  "description": "Hai vật chuyển động thẳng với vận tốc ban đầu 15 m/s và gia tốc 2 m/s², hiển thị quỹ đạo vị trí và vectơ vận tốc trong 11,5 s.",
  "nodes": [
    {
      "id": "body1Point",
      "label": "Vật 1",
      "modelId": "body1",
      "anchorX": 0.5,
      "anchorY": 0.5,
      "x": ["mul", "body1.position", 2],
      "y": 100,
      "rotation": 0,
      "scaleX": 1,
      "scaleY": 1,
      "alpha": 1,
      "svg": "<circle r=\"5\" fill=\"#1f77b4\"/>"
    },
    {
      "id": "body2Point",
      "label": "Vật 2",
      "modelId": "body2",
      "anchorX": 0.5,
      "anchorY": 0.5,
      "x": ["mul", "body2.position", 2],
      "y": 140,
      "rotation": 0,
      "scaleX": 1,
      "scaleY": 1,
      "alpha": 1,
      "svg": "<circle r=\"5\" fill=\"#ff7f0e\"/>"
    },
    {
      "id": "body1Velocity",
      "label": "Vectơ vận tốc Vật 1",
      "modelId": "body1",
      "anchorX": 0,
      "anchorY": 0.5,
      "x": ["mul", "body1.position", 2],
      "y": 100,
      "rotation": 0,
      "scaleX": ["mul", "body1.velocity", 5],
      "scaleY": 1,
      "alpha": 0.8,
      "svg": "<rect width=\"1\" height=\"4\" fill=\"#ffbb78\"/>"
    },
    {
      "id": "body2Velocity",
      "label": "Vectơ vận tốc Vật 2",
      "modelId": "body2",
      "anchorX": 0,
      "anchorY": 0.5,
      "x": ["mul", "body2.position", 2],
      "y": 140,
      "rotation": 0,
      "scaleX": ["mul", "body2.velocity", 5],
      "scaleY": 1,
      "alpha": 0.8,
      "svg": "<rect width=\"1\" height=\"4\" fill=\"#2ca02c\"/>"
    },
    {
      "id": "groundLine",
      "label": "Đường thời gian",
      "x": 0,
      "y": 180,
      "svg": "<line x1=\"0\" y1=\"0\" x2=\"650\" y2=\"0\" stroke=\"#cccccc\" stroke-width=\"1\"/>"
    },
    {
      "id": "timeLabel",
      "label": "Thời gian (s)",
      "x": 600,
      "y": 190,
      "svg": "<text font-family=\"sans-serif\" font-size=\"12\" fill=\"#666\">t = 0‑11.5 s</text>"
    }
  ]
}
```

## Bằng chứng xác minh từ BE

```json
{
  "flags": [],
  "assumptions": [
    "Acceleration is constant during the interval.",
    "All participants use the same axis convention.",
    "Acceleration is constant during the interval.",
    "All participants use the same axis convention."
  ],
  "convergenceEvidence": [
    "body1: compared 576 checkpoints using independent approved closed-form AST",
    "body2: compared 576 checkpoints using independent approved closed-form AST"
  ],
  "invariantResults": {},
  "solverVersion": "schema-ast-rk4/1.0",
  "formulaSource": "APPROVED_TOPIC_SCHEMA",
  "verificationScope": "Bound canonical quantities; semantic assumptions remain visible for review",
  "status": "VERIFIED_ANALYTICAL",
  "executionMethod": "NUMERICAL",
  "solverMethod": "RK4",
  "verificationMethod": "CLOSED_FORM_CHECKPOINTS",
  "absoluteError": 2.7284841053187847e-12,
  "relativeError": 1.2983964186294203e-14,
  "maxOdeResidual": 1.3358203432289883e-12,
  "benchmarkSummary": "0 approved benchmark bindings checked",
  "referenceSolverVersion": "schema-ast-closed-form/1.0",
  "topicVersion": "2.2"
}
```

Tại 11,5 s:

```json
{
  "t": 11.5,
  "values": {
    "t": 11.5,
    "body1.position": 304.74999999999727,
    "body1.velocity": 37.99999999999951,
    "body1.acceleration": 2,
    "body2.position": 304.74999999999727,
    "body2.velocity": 37.99999999999951,
    "body2.acceleration": 2
  }
}
```

## Lỗi phát hiện trong lần gọi này

LLM trả asset dạng SVG fragment (`<circle>`, `<rect>`, `<line>`, `<text>`) thay vì tài liệu có thẻ gốc `<svg>`. BE vẫn nhận scene; renderer FE hiện yêu cầu thẻ gốc `<svg>`, nên scene này sẽ bị từ chối. Đây là lỗi contract đầu ra render chưa được BE chặn, không phải lỗi solver. Trạng thái VERIFIED_ANALYTICAL chỉ xác nhận các đại lượng vật lý đã binding, không xác nhận SVG có thể render hoặc đẹp.
