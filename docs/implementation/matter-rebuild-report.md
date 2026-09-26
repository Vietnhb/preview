# Simulation creation rebuild — implementation and evidence

## Current flow

`/workspace` accepts text, LaTeX, or a pasted/dropped PNG, JPEG, or WebP image. Typed input and OCR both require recognition confirmation. JEV ranks eight broad high-school physics topic packs; the understanding model receives every registered pack, with concise vocabulary and declared runtime capabilities. Ranking does not exclude a topic or force a particular runtime.

One AI call produces the explanation, disclosed defaults, requested objects and counts, connections, fixed physical quantities, initial relations, and runtime choice. It uses JSON mode with a supplied response contract; inventory shape and source cues support user review. The model asks one essential interaction question when needed, otherwise returns an explanation or an honest unsupported result. A second user confirmation starts generation. There is no separate inventory call or fidelity-proof call.

The model chooses between two generic runtimes, based on the confirmed physics and declared capabilities:

- **MATTER:** AI supplies numeric SI scene data. The server derives optional local controls, checks executable scene structure, and compiles a Matter.js setup. This covers supported rigid bodies, initial motion, gravity, contacts, fixed geometry, and built-in constraints.
- **VISUAL:** AI supplies `init`, `step`, and `draw` JavaScript bodies for a numerical and visual model. Behavior and drawings derive from the description and governing high-school physics; there is no backend catalog of equations for individual exercises. Electrical, wave, thermal, electromagnetic, modern-physics, and measurement packs declare this runtime. Mechanical packs declare both runtimes.

Matter's numeric, object, link, and duration limits are given to AI during understanding so it can select a declared VISUAL runtime when the request exceeds Matter's representation budget. A visual model may map an explicitly requested physical time span into a shorter presentation interval; it must retain and label the physical clock correctly.

Source-number gaps, paraphrased cues, object-count differences, and directly comparable scene-value differences are advisory review warnings. The intent screen shows the inventory and assumptions inside the existing confirmation. Strict geometric fidelity classes, scene reconciliation, and exhaustive physical binding gates were removed. Missing direct scene fields are reported as not automatically verifiable rather than guessed into a field mapping.

The frontend checks generated JavaScript with a modern JavaScript parser before embedding it in the sandbox. The duplicate backend VISUAL parser was removed: the server does not execute those programs, and its older parser rejected some valid modern syntax. The backend checks the response shape, size and numeric controls and normalizes equivalent function wrappers. Matter's deterministic compiler retains its structural checks.

Local helper functions, flat data destructuring, numeric or array-length loops, optional parameter reads, template labels and owned-array operations are supported. Frontend instrumentation bounds loop/helper work to 2000 operations per phase, call depth to 128, numeric indices/array operations to 2000 entries, and primitive string growth to 256,000 characters. Execution uses a dedicated worker inside an opaque-origin iframe, with network disabled, bounded serialized state/drawing work, and a CPU watchdog. Controls have readable labels and body IDs to distinguish repeated names. Slider changes and supported pointer interaction rerun locally without calling AI. Generated drawing primitives and labels require no saved asset ID, asset substitution confirmation, or asset retry loop. Generation and startup failures are explicit; the pipeline does not retry code until it happens to pass.

Matter's background worker compares different timestep resolutions, checks eligible momentum/energy invariants, flags swept-contact risk, and checks declared expected contacts. These checks run after display and do not stop the visible simulation. The VISUAL runtime reports **UNVERIFIED**, because successful execution is not independent evidence of physical correctness; a runtime error is flagged. Actual flags are recorded with the description, generated program, controls, and available metrics. Hidden tabs and completed runs pause workers until restart.

The active creation API is `/api/matter-flow`. Retired problem creation and solver execution HTTP entrypoints return 410. Historical assignment replay, library reads, and export compatibility remain separate from new creation.

## Main source changes

- `backend/src/main/java/com/example/backend/matter/`: staged owner-bound sessions, combined understanding gateway, visual program gateway, advisory scene audit, numeric parameterizer, Matter compiler and its safety gate. The redundant visual AST gate was deleted.
- `backend/src/main/resources/prompts/matter-understanding-response-schema.json`, `matter-scene-response-schema.json`, and `visual-simulation-response-schema.json`: contracts for understanding, numeric scenes, and visual programs. Obsolete inventory/fidelity schemas and checks were removed.
- `backend/src/main/resources/schemas/catalog.json` and `schemas/source/**`: eight broad v2 data packs with open vocabulary and runtime capability declarations, replacing enumerated exercise packs. They contain no exercise examples, executable code samples, or fixed asset assignments.
- `react-client/src/pages/teacher/MatterPipelineWorkspace.tsx`, `src/api/matterFlowApi.ts`, and `src/matter-flow/`: staged confirmation, readable controls, both sandbox runtimes, dynamic drawings, and background status.
- The un-routed teacher `Workspace.tsx`, its old creation UI subtree, dead problem API client, and retired simulation creation/adjustment client functions were deleted. Shared assignment, student, library, and replay components were retained.
- Old RK4/postfix/declarative-dynamics execution code and the simulation WebSocket adjustment entrypoint were removed. Historical typed outputs, replay compatibility, and database migrations remain for existing records.

Disclosed defaults no longer have a 20-entry or 500-character-per-entry limit; the aggregate text budget remains. Regression coverage includes preserving all 25 disclosed defaults, many-body values beyond the optional control count, lexical scopes and sibling-loop work, safe local array/object mutation, number formatting, host access rejection, and invalid syntax rejection. These tests validate specific mechanisms; they do not prove correctness for all descriptions.

## Verification and real-provider evidence — 2026-09-26

The final opt-in service integration passed all nine expected case paths. Six programs were actually executed: five Matter scenes and one generated visual model. Three Matter runs produced nonblocking flags, which were submitted to the production service's reviewer log with the generated code, original description, parameter values and metrics. LaTeX and image cases exercised recognition confirmation and understanding; they did not generate a second program.

The test used the configured real JEV, text AI and OCR providers, the production flow service and template registry, and mocked current-user/quota services. It made no database writes. This is not an authenticated browser-to-backend deployment test. Full input/output, code, inventories and runtime reports are archived in [final-nine-cases.json](evidence/final-nine-cases.json); captured flags are in [validation-flags.txt](evidence/validation-flags.txt).

Commands: `mvn -q clean verify` passed with 61 tests discovered, 60 executed, one opt-in live test skipped, and no failures/errors. The separate live run used `mvn --% -q -Dphyslive.matter-live-e2e=true -Dtest=MatterFlowLivePipelineTest test` and passed. Frontend `npm run check` passed architecture checks, all eight template contracts, ESLint, 53 tests, TypeScript and the production build. Vite reports large output chunks; it does not fail the build.

### Complete stated motion

**Actual input:**

> On a horizontal frictionless surface, two circular pucks of 1 kg and radius 0.1 m
> start at x = 0 m and x = 2 m. The first moves right at 2 m/s and the second is
> stationary. Their collision is perfectly elastic. Show the motion for 3 seconds.

**Stages:** RECOGNITION → EXPLAIN → SIMULATION (MATTER). Runtime: **OK**; recorded review status: **OK**.

**Actual explanation excerpt:**

> Hai vật tròn (pucks) khối lượng 1 kg, bán kính 0.1 m nằm trên mặt phẳng ngang không ma sát. Vật thứ nhất ở x=0 m có vận tốc ban đầu 2 m/s sang phải, vật thứ hai ở x=2 m đứng yên. Vì va chạm hoàn toàn đàn hồi (hệ số phục hồi = 1) và khối lượng bằng nhau, sau va chạm chúng hoán đổi vận tốc: vật thứ nhất dừng lại, vật thứ hai di chuyển sang phải với 2 m/s. Động học được mô phỏng trong 3 s bằng luật II Newton và bảo toàn động lượng, năng lượng.

**Retained scene data:** 2 bodies; physical duration 3 s; gravity (0, 0) m/s².

- vật (puck0): circle; mass 1 kg; centre (0, 0) m; velocity (2, 0) m/s; radius 0.1 m; restitution 1.
- vật (puck1): circle; mass 1 kg; centre (2, 0) m; velocity (0, 0) m/s; radius 0.1 m; restitution 1.

**Selected generated setup lines:**

```js
const body0 = Matter.Bodies.circle((width / 2 + ((0.0) - centerX) * scale), (height / 2 + ((0.0) - centerY) * scale), (params.body0_radius) * scale, { isStatic: false, restitution: params.body0_restitution, friction: 0.0, frictionAir: 0.0, label: "vật", plugin: { physliveId: "puck0" } });
Matter.Body.setMass(body0, params.body0_mass);
Matter.Body.setVelocity(body0, { x: (params.body0_vx) * scale / 60, y: (params.body0_vy) * scale / 60 });
Matter.Body.setMass(body1, params.body1_mass);
Matter.Body.setVelocity(body1, { x: (params.body1_vx) * scale / 60, y: (params.body1_vy) * scale / 60 });
```

**Actual numerical report:**

```json
{
  "status": "OK",
  "flags": [],
  "metrics": {
    "maxPositionDeviationPx": 3.0745758289252763,
    "maxSweptTravelToFeatureRatio": 0.17095843573031305,
    "expectedContactCount": 1,
    "observedExpectedContactCount": 1,
    "maxRelativeMomentumDrift": 2.4178190314058967e-14,
    "maxRelativeEnergyDrift": 0.048696043999267885
  }
}
```

### Essential interaction clarification

**Actual input:**

> Two 1 kg pucks are on a horizontal frictionless table. They interact for 3 seconds,
> but the interaction itself is unspecified: they might collide or be connected.
> Simulate what happens.

**Stages:** RECOGNITION → CLARIFY → EXPLAIN → SIMULATION (MATTER). Runtime: **OK**; recorded review status: **OK**.

**Actual question:**

> Bạn muốn các pucks va chạm (đàn hồi, không đàn hồi) hay được nối bằng lò xo, hay một kiểu tương tác khác?

**User's added answer:**

> They collide elastically. The first puck moves right at 2 m/s and the second is stationary.

**Actual explanation excerpt:**

> Hai vật (mỗi khối lượng 1 kg) trượt trên mặt bàn không ma sát. Khi va chạm đàn hồi, vì khối lượng bằng nhau, vận tốc được trao đổi: sau va chạm, vật thứ nhất dừng lại và vật thứ hai di chuyển sang phải với 2 m/s. Với vị trí ban đầu giả định (vật 1 ở x=0 m, vật 2 ở x=4 m) va chạm xảy ra khoảng t=2 s và kéo dài tới 3 s tổng thời gian mô phỏng. Sau va chạm, vật 2 tiếp tục di chuyển 2 m/s trong 1 s còn lại, trong khi vật 1 đứng yên.

**Retained scene data:** 2 bodies; physical duration 3 s; gravity (0, 0) m/s².

- vật (puck1): circle; mass 1 kg; centre (0, 0) m; velocity (2, 0) m/s; radius 0.1 m; restitution 1.
- vật (puck2): circle; mass 1 kg; centre (4, 0) m; velocity (0, 0) m/s; radius 0.1 m; restitution 1.

**Selected generated setup lines:**

```js
const body0 = Matter.Bodies.circle((width / 2 + ((0.0) - centerX) * scale), (height / 2 + ((0.0) - centerY) * scale), (params.body0_radius) * scale, { isStatic: false, restitution: params.body0_restitution, friction: 0.0, frictionAir: 0.0, label: "vật", plugin: { physliveId: "puck1" } });
Matter.Body.setMass(body0, params.body0_mass);
Matter.Body.setVelocity(body0, { x: (params.body0_vx) * scale / 60, y: (params.body0_vy) * scale / 60 });
Matter.Body.setMass(body1, params.body1_mass);
Matter.Body.setVelocity(body1, { x: (params.body1_vx) * scale / 60, y: (params.body1_vy) * scale / 60 });
```

**Actual numerical report:**

```json
{
  "status": "OK",
  "flags": [],
  "metrics": {
    "maxPositionDeviationPx": 3.583441412931734,
    "maxSweptTravelToFeatureRatio": 0.1699355660978578,
    "expectedContactCount": 1,
    "observedExpectedContactCount": 1,
    "maxRelativeMomentumDrift": 3.552713678800493e-14,
    "maxRelativeEnergyDrift": 0.03756344318648859
  }
}
```

### Generated model beyond rigid bodies

**Actual input:**

> Simulate the voltage across a capacitor while an RC circuit charges from a battery.

**Stages:** RECOGNITION → EXPLAIN → SIMULATION (VISUAL). Runtime: **RUNTIME_OK**; recorded review status: **UNVERIFIED**.

**Actual explanation excerpt:**

> The circuit consists of a voltage source (battery), a resistor, and a capacitor connected in series (RC circuit). The capacitor starts uncharged (0 V). When the switch is closed, the capacitor charges according to the RC‑charging law Vc(t)=Vb·(1‑e^{‑t/(R·C)}). The simulation will display the capacitor voltage versus time, showing the exponential rise toward the battery voltage. Assumed values (not given in the description) are a 5 V battery, a 1 kΩ resistor, and a 100 µF capacitor. These assumptions are marked as ASSUMPTION in the fixedQuantities list.

**Actual controls:** Battery voltage (V) = 5 V; Resistance (Ω) = 1000 Ω; Capacitance (F) = 0.0001 F.

**Generated program excerpt:**

```js
// init
const margin=32;const plotX=margin+200;const plotY=margin;const plotW=width-plotX-margin;const plotH=height-2*margin;const tau=params.R*params.C;return{t:0,Vc:0,Vb:params.Vb,tau:tau,prevX:plotX,prevY:plotY+plotH,plot:{x:plotX,y:plotY,w:plotW,h:plotH},margin:margin};
// step
state.t+=dt;const Vb=params.Vb;const tau=params.R*params.C;state.tau=tau;state.Vb=Vb;const exp=Math.exp(-state.t/tau);state.Vc=Vb*(1-exp);if(state.Vc>Vb)state.Vc=Vb;
```

The bounded runtime completed 20 presentation seconds, at most 16 draw commands per frame, and final Vc=5 V. This proves execution, not independent correctness of its physics or drawings.

### Outside high-school physics

**Actual input:**

> Simulate how a shop's monthly sales change when it launches a social-media campaign.

**Stages:** RECOGNITION → UNSUPPORTED.

**Actual explanation excerpt:**

> The request concerns a shop's monthly sales and a social‑media campaign, which is outside the scope of high‑school physics topics supported by the available concept capabilities.

No code was generated for this case.

### High-speed contact risk

**Actual input:**

> On a frictionless horizontal surface, a circular 1 kg puck with radius 0.05 m starts at x = 0 m and
> moves right at 10000 m/s toward a thin fixed wall at x = 1 m. The collision is
> perfectly elastic. Show the motion for 0.1 seconds.

**Stages:** RECOGNITION → EXPLAIN → SIMULATION (MATTER). Runtime: **FLAGGED**; recorded review status: **FLAGGED**.

**Actual explanation excerpt:**

> Một vật tròn (puck) khối lượng 1 kg, bán kính 0.05 m nằm trên mặt phẳng ngang không ma sát. Ban đầu nó ở vị trí x = 0 m và di chuyển sang phải với vận tốc 10000 m/s. Ở x = 1 m có một bức tường mỏng cố định. Khi puck chạm tường, va chạm được giả định hoàn toàn đàn hồi, vì vậy vận tốc của puck đổi hướng nhưng giữ nguyên độ lớn (‑10000 m/s). Trong 0.1 s, puck di chuyển sang phải trong 0.0001 s, va chạm, rồi di chuyển sang trái trong phần thời gian còn lại. Không có lực ngoại lực nào (không có trọng lực trên mặt ngang), không có ma sát, và va chạm được coi là tương tác bảo toàn năng lượng.

**Retained scene data:** 2 bodies; physical duration 0.1 s; gravity (0, 0) m/s².

- vật (puck): circle; mass 1 kg; centre (0, 0) m; velocity (10000, 0) m/s; radius 0.05 m; restitution 1.
- bề mặt (wall): rectangle; fixed geometry; centre (1, 0) m; velocity (0, 0) m/s; size 0.01 × 1 m; restitution 1.

**Selected generated setup lines:**

```js
const body0 = Matter.Bodies.circle((width / 2 + ((0.0) - centerX) * scale), (height / 2 + ((0.0) - centerY) * scale), (params.body0_radius) * scale, { isStatic: false, restitution: params.body0_restitution, friction: 0.0, frictionAir: 0.0, label: "vật", plugin: { physliveId: "puck" } });
Matter.Body.setMass(body0, params.body0_mass);
Matter.Body.setVelocity(body0, { x: (params.body0_vx) * scale / 60, y: (params.body0_vy) * scale / 60 });
```

**Actual numerical report:**

```json
{
  "status": "FLAGGED",
  "flags": [
    "A fast body may cross another body's collision feature in one physics step; contact may be missed.",
    "Expected contact between puck and wall was not observed within the requested duration."
  ],
  "metrics": {
    "maxPositionDeviationPx": 0,
    "maxSweptTravelToFeatureRatio": 143.71257485029935,
    "expectedContactCount": 1,
    "observedExpectedContactCount": 0
  }
}
```

### Vietnamese description with fixed quantities

**Actual input:**

> Một quả bóng khối lượng 1 kg, bán kính 0,1 m được thả từ độ cao 2 m
> xuống sàn ngang cố định. Lấy g = 9,81 m/s² hướng xuống, hệ số đàn hồi
> giữa bóng và sàn là 0,8. Mô phỏng chuyển động trong 3 giây.

**Stages:** RECOGNITION → EXPLAIN → SIMULATION (MATTER). Runtime: **FLAGGED**; recorded review status: **FLAGGED**.

**Actual explanation excerpt:**

> Quả bóng (khối lượng 1 kg, bán kính 0,1 m) được thả từ độ cao 2 m so với sàn ngang cố định. Trọng lực g = 9,81 m/s² hướng xuống tạo ra lực trọng trường F = m·g. Khi bóng chạm sàn, va chạm được mô tả bằng định luật II Newton và hệ số phục hồi e = 0.8: vận tốc sau va chạm = –e·v_before (đảo chiều và giảm độ lớn). Không có lực cản không khí hay ma sát, vì vậy chỉ có trọng lực và lực va chạm. Mô phỏng dùng Matter.js (runtime MATTER) với một vật rắn (ball) và một vật tĩnh (floor). Thời gian mô phỏng 3 s, đủ để thấy quá trình rơi, va chạm đầu tiên và một phần nảy lên rồi rơi lại.

**Retained scene data:** 2 bodies; physical duration 3 s; gravity (0, 9.81) m/s².

- vật (ball0): circle; mass 1 kg; centre (0, -2) m; velocity (0, 0) m/s; radius 0.1 m; restitution 0.8.
- bề mặt (floor1): rectangle; fixed geometry; centre (0, 0) m; velocity (0, 0) m/s; size 5 × 0.2 m; restitution 0.

**Selected generated setup lines:**

```js
const body0 = Matter.Bodies.circle((width / 2 + ((0.0) - centerX) * scale), (height / 2 + ((params.body0_y) - centerY) * scale), (params.body0_radius) * scale, { isStatic: false, restitution: params.body0_restitution, friction: 0.0, frictionAir: 0.0, label: "vật", plugin: { physliveId: "ball0" } });
Matter.Body.setMass(body0, params.body0_mass);
Matter.Body.setVelocity(body0, { x: (params.body0_vx) * scale / 60, y: (params.body0_vy) * scale / 60 });
```

**Actual numerical report:**

```json
{
  "status": "FLAGGED",
  "flags": [
    "The simulation changes noticeably at a finer timestep."
  ],
  "metrics": {
    "maxPositionDeviationPx": 25.59691824620353,
    "maxSweptTravelToFeatureRatio": 0.5014387211367932,
    "expectedContactCount": 1,
    "observedExpectedContactCount": 1
  }
}
```

### Distinct objects and unequal masses

**Actual input:**

> Three circular pucks move on a frictionless horizontal surface. Puck A has
> mass 1 kg, radius 0.1 m, starts at x = 0 m and moves right at 2 m/s.
> Puck B has mass 2 kg, radius 0.1 m, starts at x = 1.5 m and is stationary.
> Puck C has mass 3 kg, radius 0.1 m, starts at x = 3 m and is stationary.
> Their collisions are perfectly elastic. Show the motion for 3 seconds.

**Stages:** RECOGNITION → EXPLAIN → SIMULATION (MATTER). Runtime: **FLAGGED**; recorded review status: **FLAGGED**.

**Actual explanation excerpt:**

> Three circular pucks slide on a friction‑free horizontal plane. Their masses, radii, initial x‑positions and initial velocities are taken directly from the description. No external forces (gravity, friction) act, and the collisions are perfectly elastic (coefficient of restitution = 1). The system is modeled with Matter.js as three rigid circular bodies that can collide elastically. The simulation runs for the physical duration of 3 s (well within the 0.1–40 s limit).

**Retained scene data:** 3 bodies; physical duration 3 s; gravity (0, 0) m/s².

- Puck A (puckA): circle; mass 1 kg; centre (0, 0) m; velocity (2, 0) m/s; radius 0.1 m; restitution 1.
- Puck B (puckB): circle; mass 2 kg; centre (1.5, 0) m; velocity (0, 0) m/s; radius 0.1 m; restitution 1.
- Puck C (puckC): circle; mass 3 kg; centre (3, 0) m; velocity (0, 0) m/s; radius 0.1 m; restitution 1.

**Selected generated setup lines:**

```js
const body0 = Matter.Bodies.circle((width / 2 + ((0.0) - centerX) * scale), (height / 2 + ((0.0) - centerY) * scale), (params.body0_radius) * scale, { isStatic: false, restitution: params.body0_restitution, friction: 0.0, frictionAir: 0.0, label: "Puck A", plugin: { physliveId: "puckA" } });
Matter.Body.setMass(body0, params.body0_mass);
Matter.Body.setVelocity(body0, { x: (params.body0_vx) * scale / 60, y: (params.body0_vy) * scale / 60 });
Matter.Body.setMass(body1, params.body1_mass);
Matter.Body.setVelocity(body1, { x: (params.body1_vx) * scale / 60, y: (params.body1_vy) * scale / 60 });
```

**Actual numerical report:**

```json
{
  "status": "FLAGGED",
  "flags": [
    "Energy drift exceeds the expected tolerance."
  ],
  "metrics": {
    "maxPositionDeviationPx": 3.213466308054876,
    "maxSweptTravelToFeatureRatio": 0.17254601226993946,
    "expectedContactCount": 0,
    "observedExpectedContactCount": 0,
    "maxRelativeMomentumDrift": 1.0263395072090353e-13,
    "maxRelativeEnergyDrift": 0.15240231355645129
  }
}
```

### Raw LaTeX confirmation

**Actual input:**

> \text{Two circular pucks collide elastically on a frictionless surface.}
> \quad m_1=m_2=1\,\mathrm{kg},\quad v_1=2\,\mathrm{m/s},\quad v_2=0.

**Stages:** RECOGNITION → EXPLAIN.

**Actual explanation excerpt:**

> Hai vòng tròn (puck) tròn, khối lượng mỗi cái 1 kg, di chuyển trên mặt phẳng không ma sát. Puck 1 có vận tốc ban đầu 2 m/s về phía +x, puck 2 đứng yên. Vì bề mặt không ma sát và không có lực ngoài nào tác dụng, chỉ có lực va chạm nội tại. Va chạm được giả định hoàn toàn đàn hồi (hệ số phục hồi = 1), nên năng lượng cơ học được bảo toàn và sau va chạm các vật sẽ đổi vận tốc: puck 1 dừng lại, puck 2 nhận vận tốc 2 m/s về phía +x. Mô hình sẽ vẽ hai vòng tròn, ban đầu ở vị trí x = –1 m và x = +1 m (giả định), sau đó hiển thị quá trình va chạm và các vận tốc sau va chạm.

No code was generated for this case.

### Image OCR confirmation

**Actual image input:** a generated 1400×350 PNG containing the three printed lines below. The configured vision provider returned:

> Two pucks collide on a frictionless horizontal surface.
> m1 = 1 kg, v1 = 2 m/s right; m2 = 1 kg, v2 = 0.
> The collision is elastic. Show the motion.

**Stages:** RECOGNITION → EXPLAIN.

**Actual explanation excerpt:**

> Hai pucks trên mặt phẳng ngang không ma sát, mỗi khối lượng 1 kg. Puck 1 di chuyển sang phải với vận tốc 2 m/s, puck 2 đứng yên. Va chạm là đàn hồi, vì khối lượng bằng nhau, sau va chạm vận tốc được trao đổi: puck 1 dừng lại, puck 2 di chuyển sang phải với 2 m/s. Động học được mô phỏng bằng Matter.js với hai thân tròn, không lực ngoại lực, không ma sát, và tương tác bảo toàn năng lượng (elastic collision).

**Recognition notice:** OCR confidence is unavailable. Check the recognized text before continuing.

No code was generated for this case.

### Browser evidence and observed shortcomings

The browser harness loads the actual generated programs into the production sandbox components. [Complete motion](evidence/complete-browser.png), [three named objects](evidence/three-bodies-browser.png), [flagged contact case after completion](evidence/instability-browser.png), and [completed visual model](evidence/circuit-browser.png) were inspected. Normal completion now snapshots the last frame into a bounded bitmap before terminating the OffscreenCanvas producer; the 0.1-second scene and completed visual model retain their display. These screenshots do not cover the authenticated workspace/HTTP/database path.

The final circuit executes and shows component geometry, voltage axes and a marker, but its drawing retains only the latest short line segment. It does not draw the full charging curve or clearly label every component despite the prompt requesting these. Generated rendering quality still needs review.

Language and provenance remain unreliable: some English requests, including the clarification question, receive Vietnamese output; explicitly given elasticity/friction is sometimes described as an assumption. The final three-body run preserves Puck A/B/C names, masses 1/2/3 kg, positions 0/1.5/3 m, radii 0.1 m and velocities 2/0/0 m/s. Other inventories use generic labels such as “vật”; body IDs keep instances distinguishable.

The Vietnamese scene retains mass, radius, g and restitution, but its ball centre is y = −2 m while the floor centre is y = 0 with thickness 0.2 m: the centre is 1.9 m above the top surface and the lower edge 1.8 m above it. It therefore does not establish the requested 2 m surface height. In an earlier run, this clearance was 2.1 m. Advisory warnings expose missing source numerals but do not prove geometry. In the high-speed case the body tunnels through the wall and leaves the initial view; the warning is correct and the displayed explanation's predicted bounce is not realized.

Earlier provider runs failed strict provider-schema validation or generation/startup syntax checks. Generic wrapper normalization, modern frontend parsing, optional data reads and removal of duplicate backend parsing fixed false rejections. Genuine malformed quotes/braces and absent state values remain possible model errors; they are not repaired into guessed physics. [An earlier failed provider output](evidence/circuit-latest-failure-raw.json) is retained alongside the final passing run. No automatic model or asset retry loop was added.

The [deleted paths inventory](evidence/deleted-working-tree-files.json) records each deleted tracked path in the current working tree and its category reason. Newly introduced strict fidelity/inventory classes and the duplicated backend visual parser were also removed during this rebuild; those untracked intermediate files do not appear in Git's deletion list.

## Limits and remaining work

- Open vocabulary and AI-authored programs support varied descriptions within Physics grades 10–12, but do not guarantee every possible description or mathematically correct simulation. AI may misunderstand names, quantities, units, geometry, language, or governing laws. Confirmation and advisory review do not prove semantic equivalence.
- Matter represents its available rigid-body primitives; its spring constraints are not a universal physical spring solver. The visual runtime can express other grounded models, but their physical behavior is not independently validated by a solver. An executable visual must not be presented as independently verified physics.
- Matter generation is bounded to 60 bodies, 80 links, absolute SI scene values up to 1,000,000, and 0.1–40 seconds. Both runtimes expose at most 30 optional controls while preserving other given values in scene/model data. The visual runtime also bounds code size, loop work, serialized state, draw commands, and execution time. These resource limits prevent literal infinite objects or unrestricted computation.
- Assets are currently drawn from canvas primitives and labels. Rich externally fetched or generated artwork is not integrated; the isolated sandbox blocks network and media access. The pipeline does not require repository artwork for every requested participant.
- Worker resource guards bound ordinary strings, arrays, state and drawing work; they are not a process-wide heap quota. Native implicit string conversion of highly shared nested arrays can allocate temporary data before the result guard. Isolation and termination mitigate execution stalls but do not prove an absolute heap bound.
- Source inventory and direct-field audit are advisory. Derived quantities, arbitrary field names, and visual-program semantics may remain uncheckable automatically. Requests involving grouped members and different individual relationships still rely on the model preserving that structure correctly.
- Sessions are kept in memory with owner checks and expiry. New generated sessions are not migrated into the historical assignment/library output contract. Persisted-history migration and a full authenticated deployment smoke test remain separate work.
