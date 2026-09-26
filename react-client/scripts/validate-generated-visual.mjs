import { readFile } from "node:fs/promises";
import vm from "node:vm";
import { validateVisualProgram } from "../src/matter-flow/visualCodeSafety.ts";
import { instrumentVisualProgram } from "../src/matter-flow/visualWorkBudget.ts";

const file = process.argv[2];
if (!file) throw Error("Supply the live cases JSON artifact.");
const cases = JSON.parse(await readFile(file, "utf8"));
for (const item of cases) {
  const spec = item.simulationSpec;
  if (spec?.runtimeKind !== "VISUAL") continue;
  const params = Object.fromEntries(item.parameters.map((entry) => [entry.name, entry.value]));
  const unsafe = validateVisualProgram(spec.visualProgram, Object.keys(params));
  if (unsafe) throw Error(`${item.case}: ${unsafe}`);
  const program = instrumentVisualProgram(spec.visualProgram);
  let commands = [];
  const add = (kind, values, text) => {
    if (commands.length >= 1000 || values.some((v) => !Number.isFinite(v)))
      throw Error("Invalid drawing command");
    commands.push({ kind, values, ...(text === undefined ? {} : { text }) });
  };
  const paint = Object.freeze({
    circle: (x, y, r) => add("circle", [x, y, r]),
    rect: (x, y, w, h) => add("rect", [x, y, w, h]),
    line: (x, y, xx, yy, _color, size = 2) => add("line", [x, y, xx, yy, size]),
    arrow: (x, y, xx, yy, _color, size = 2) => add("arrow", [x, y, xx, yy, size]),
    text: (x, y, value, _color, size = 16) => add("text", [x, y, size], String(value)),
  });
  const context = vm.createContext({ params: Object.freeze(params), width: 960, height: 540,
    paint, dt: 1 / 60 }, { codeGeneration: { strings: false, wasm: false } });
  new vm.Script(`function init(params,width,height){${program.init}}\n`
    + `function step(state,dt,params,width,height){${program.step}}\n`
    + `function draw(state,paint,params,width,height){${program.draw}}\n`
    + "state=init(params,width,height); state.pointer={x:480,y:270,down:false};")
    .runInContext(context, { timeout: 100 });
  const step = new vm.Script("step(state,dt,params,width,height);");
  const draw = new vm.Script("draw(state,paint,params,width,height);");
  let maximumCommands = 0;
  const sampleLabels = new Set();
  for (let frame = 0; frame <= Math.ceil(spec.durationSeconds * 60); frame++) {
    if (frame) step.runInContext(context, { timeout: 50 });
    let visited = 0;
    const state = JSON.stringify(context.state, (_key, value) => {
      if (++visited > 12000 || Array.isArray(value) && value.length > 2000
        || typeof value === "string" && value.length > 256000)
        throw Error("State exceeded its data budget");
      if (["function", "symbol", "bigint"].includes(typeof value)) throw Error("Nonserializable state");
      if (typeof value === "number" && !Number.isFinite(value)) throw Error("Non-finite state");
      return value;
    });
    if (!state || state.length > 256000) throw Error("Invalid or excessive state");
    commands = [];
    draw.runInContext(context, { timeout: 50 });
    maximumCommands = Math.max(maximumCommands, commands.length);
    if (frame === 0) commands.filter((c) => c.kind === "text").forEach((c) => sampleLabels.add(c.text));
  }
  console.log(JSON.stringify({ case: item.case, status: "RUNTIME_OK", physics: "UNVERIFIED",
    durationSeconds: spec.durationSeconds, maximumCommands, sampleLabels: [...sampleLabels],
    finalState: context.state }));
}
