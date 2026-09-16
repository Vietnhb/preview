import { assetRegistry, propRegistry } from "./assets";
import {
  actorState, extent, hasEffect, mapRange, readSeries, sample,
  type ActorFrame, type Point, type SceneFrame,
} from "./model";

type ScenePainter = (frame: SceneFrame) => void;
type EnvironmentPainter = (frame: SceneFrame, baseline: number) => void;

function drawBackground(frame: SceneFrame) {
  const { ctx, width, height, palette } = frame;
  const gradient = ctx.createRadialGradient(width * .58, height * .4, 30, width * .5, height * .5, Math.max(width, height));
  gradient.addColorStop(0, palette.backgroundTop);
  gradient.addColorStop(1, palette.backgroundBottom);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);
}

type GridBounds = { left: number; top: number; right: number; bottom: number };

function drawGrid(frame: SceneFrame, bounds?: GridBounds, step = 40) {
  if (!frame.overlays.grid) return;
  const { ctx, palette } = frame;
  const gridBounds = bounds ?? { left: 0, top: 0, right: frame.width, bottom: frame.height };
  ctx.save();
  ctx.strokeStyle = palette.grid;
  ctx.lineWidth = 1;
  for (let x = gridBounds.left; x <= gridBounds.right; x += step) {
    ctx.beginPath(); ctx.moveTo(x, gridBounds.top); ctx.lineTo(x, gridBounds.bottom); ctx.stroke();
  }
  for (let y = gridBounds.top; y <= gridBounds.bottom; y += step) {
    ctx.beginPath(); ctx.moveTo(gridBounds.left, y); ctx.lineTo(gridBounds.right, y); ctx.stroke();
  }
  ctx.restore();
}

function drawBadge(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, color: string) {
  ctx.save();
  ctx.font = "700 11px ui-monospace, SFMono-Regular, Consolas, monospace";
  const width = ctx.measureText(text).width + 14;
  ctx.fillStyle = "rgba(8,15,30,.9)";
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.1;
  ctx.beginPath(); ctx.roundRect(x - width / 2, y - 15, width, 22, 6); ctx.fill(); ctx.stroke();
  ctx.fillStyle = "#ffffff"; ctx.textAlign = "center"; ctx.textBaseline = "middle"; ctx.fillText(text, x, y - 4);
  ctx.restore();
}

function drawVector(frame: SceneFrame, from: Point, delta: Point, color: string, label: string) {
  const { ctx } = frame;
  if (Math.hypot(delta.x, delta.y) < 2) return;
  const to = { x: from.x + delta.x, y: from.y + delta.y };
  const angle = Math.atan2(delta.y, delta.x);
  const head = 10;
  ctx.save();
  ctx.strokeStyle = color; ctx.fillStyle = color; ctx.lineWidth = 2.8; ctx.lineCap = "round";
  ctx.shadowColor = color; ctx.shadowBlur = 9;
  ctx.beginPath(); ctx.moveTo(from.x, from.y); ctx.lineTo(to.x, to.y); ctx.stroke();
  ctx.beginPath(); ctx.moveTo(to.x, to.y);
  ctx.lineTo(to.x - head * Math.cos(angle - Math.PI / 6), to.y - head * Math.sin(angle - Math.PI / 6));
  ctx.lineTo(to.x - head * Math.cos(angle + Math.PI / 6), to.y - head * Math.sin(angle + Math.PI / 6));
  ctx.closePath(); ctx.fill(); ctx.restore();
  drawBadge(ctx, label, (from.x + to.x) / 2, Math.min(from.y, to.y) - 16, color);
}

function drawRuler(frame: SceneFrame, min: number, max: number, y: number, left: number, right: number, unit = "m") {
  const { ctx, palette, width } = frame;
  const count = width < 620 ? 4 : 7;
  ctx.save(); ctx.font = "600 10px ui-monospace, Consolas, monospace"; ctx.textAlign = "center";
  for (let index = 0; index < count; index++) {
    const ratio = index / (count - 1);
    const x = left + ratio * (right - left);
    const value = min + ratio * (max - min);
    ctx.strokeStyle = index === 0 ? palette.cyan : palette.gridStrong;
    ctx.lineWidth = index === 0 ? 2 : 1;
    ctx.beginPath(); ctx.moveTo(x, y - 9); ctx.lineTo(x, y + 10); ctx.stroke();
    ctx.fillStyle = index === 0 ? palette.cyan : palette.muted;
    ctx.fillText(`${value.toFixed(1)} ${unit}`, x, y + 25);
  }
  ctx.restore();
}

const environmentRegistry: Record<string, EnvironmentPainter> = {
  "track.engineering": (frame, y) => {
    const { ctx, width, palette } = frame;
    const rail = ctx.createLinearGradient(0, y, 0, y + 70);
    rail.addColorStop(0, "#1e293b"); rail.addColorStop(1, "#090d16");
    ctx.fillStyle = rail; ctx.fillRect(0, y, width, 70);
    ctx.strokeStyle = palette.cyan; ctx.lineWidth = 2; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
    ctx.strokeStyle = "rgba(148,163,184,.28)"; ctx.setLineDash([14, 14]);
    ctx.beginPath(); ctx.moveTo(0, y + 36); ctx.lineTo(width, y + 36); ctx.stroke(); ctx.setLineDash([]);
  },
  "road.highway": (frame, y) => {
    const { ctx, width } = frame;
    const road = ctx.createLinearGradient(0, y, 0, y + 86);
    road.addColorStop(0, "#182236"); road.addColorStop(1, "#070a11");
    ctx.fillStyle = road; ctx.fillRect(0, y, width, 86);
    ctx.fillStyle = "#334155"; ctx.fillRect(0, y, width, 3);
    ctx.strokeStyle = "#eab308"; ctx.lineWidth = 3; ctx.setLineDash([22, 18]);
    ctx.beginPath(); ctx.moveTo(0, y + 42); ctx.lineTo(width, y + 42); ctx.stroke(); ctx.setLineDash([]);
  },
  "range.projectile": (frame, y) => {
    const { ctx, width } = frame;
    const turf = ctx.createLinearGradient(0, y, 0, frame.height);
    turf.addColorStop(0, "#065f46"); turf.addColorStop(1, "#022c22");
    ctx.fillStyle = turf; ctx.fillRect(0, y, width, frame.height - y);
    ctx.strokeStyle = "#10b981"; ctx.lineWidth = 2.5; ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(width, y); ctx.stroke();
  },
  "track.collision": (frame, y) => {
    const { ctx, width, palette } = frame;
    ctx.fillStyle = "rgba(15,23,42,.82)"; ctx.fillRect(42, y - 30, width - 84, 104);
    ctx.strokeStyle = palette.gridStrong; ctx.lineWidth = 1; ctx.strokeRect(42, y - 30, width - 84, 104);
    for (const offset of [0, 56]) {
      ctx.strokeStyle = offset ? "rgba(249,115,22,.4)" : "rgba(56,189,248,.4)";
      ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(62, y + offset); ctx.lineTo(width - 62, y + offset); ctx.stroke();
    }
  },
  "bench.spring": (frame, y) => {
    const { ctx, width, palette } = frame;
    ctx.fillStyle = "rgba(30,41,59,.9)"; ctx.fillRect(54, y, width - 108, 34);
    ctx.strokeStyle = palette.gridStrong; ctx.strokeRect(54, y, width - 108, 34);
    ctx.fillStyle = "#64748b"; ctx.fillRect(72, y - 125, 20, 125);
    for (let row = y - 116; row < y - 8; row += 16) {
      ctx.strokeStyle = "rgba(203,213,225,.22)"; ctx.beginPath(); ctx.moveTo(72, row); ctx.lineTo(92, row + 12); ctx.stroke();
    }
  },
  "board.circuit": (frame) => {
    const { ctx, width, height, palette } = frame;
    const board = ctx.createLinearGradient(55, 45, width - 55, height - 45);
    board.addColorStop(0, "rgba(15,45,55,.92)"); board.addColorStop(1, "rgba(6,24,31,.96)");
    ctx.fillStyle = board; ctx.beginPath(); ctx.roundRect(55, 42, width - 110, height - 84, 22); ctx.fill();
    ctx.strokeStyle = "rgba(45,212,191,.22)"; ctx.lineWidth = 1.5; ctx.stroke();
    ctx.fillStyle = palette.muted; ctx.font = "700 10px ui-monospace, Consolas, monospace";
    ctx.fillText("PHYSLIVE · CIRCUIT LAB", 78, 70);
  },
};

function paintEnvironment(frame: SceneFrame, baseline: number) {
  const painter = environmentRegistry[frame.presentation.environment ?? ""] ?? environmentRegistry["track.engineering"];
  painter(frame, baseline);
}

function paintActor(frame: SceneFrame, actor: ActorFrame) {
  const painter = assetRegistry[actor.config.asset] ?? assetRegistry["object.block.amber"];
  painter(frame.ctx, actor, frame.palette);
}

function drawHeadlight(frame: SceneFrame, actor: ActorFrame) {
  if (!hasEffect(frame, "vehicle.headlight")) return;
  const { ctx } = frame;
  const { x, y } = actor.position;
  const glow = ctx.createRadialGradient(x + 42, y - 8, 4, x + 145, y - 8, 130);
  glow.addColorStop(0, "rgba(254,240,138,.34)"); glow.addColorStop(1, "rgba(254,240,138,0)");
  ctx.fillStyle = glow; ctx.beginPath(); ctx.moveTo(x + 40, y - 15); ctx.lineTo(x + 175, y - 42); ctx.lineTo(x + 175, y + 26); ctx.closePath(); ctx.fill();
}

function drawBrakeSmoke(frame: SceneFrame, actor: ActorFrame) {
  if (!hasEffect(frame, "vehicle.brake-smoke") || actor.acceleration.x >= -.01 || Math.abs(actor.velocity.x) < .25) return;
  const { ctx, time = 0 } = frame;
  for (let i = 0; i < 6; i++) {
    const phase = (time * 24 + i * 13) % 28;
    const radius = 5 + phase * .34;
    ctx.fillStyle = `rgba(203,213,225,${Math.max(.04, .32 - phase * .009)})`;
    ctx.beginPath(); ctx.arc(actor.position.x - 40 - phase * 1.5, actor.position.y + 12 - Math.sin(i * 2.1) * 5, radius, 0, Math.PI * 2); ctx.fill();
  }
}

function drawMotionScene(frame: SceneFrame) {
  const { ctx, width, height, simulation, presentation, overlays, palette } = frame;
  drawBackground(frame); drawGrid(frame);
  const baseline = height * .65;
  paintEnvironment(frame, baseline);
  const configs = presentation.actors ?? [];
  const allX = configs.flatMap(actor => readSeries(simulation, actor.x));
  const [min, max] = extent([...allX, 0]);
  const left = 88; const right = width - 105;
  drawRuler(frame, min, max, baseline, left, right);
  configs.forEach(config => {
    const state = actorState(frame, config);
    const position = { x: mapRange(state.world.x, min, max, left, right), y: baseline - 17 - (config.lane ?? 0) * 58 };
    const actor: ActorFrame = { ...state, position, scale: width < 620 ? .78 : 1 };
    if (overlays.trajectory && hasEffect(frame, "motion.trail")) {
      const values = readSeries(simulation, config.x);
      ctx.save(); ctx.strokeStyle = "rgba(56,189,248,.34)"; ctx.lineWidth = 4; ctx.lineCap = "round";
      ctx.beginPath();
      values.slice(0, frame.index + 1).forEach((value, index) => {
        const x = mapRange(value, min, max, left, right);
        if (index === 0) ctx.moveTo(x, position.y + 17); else ctx.lineTo(x, position.y + 17);
      });
      ctx.stroke(); ctx.restore();
    }
    drawHeadlight(frame, actor); drawBrakeSmoke(frame, actor); paintActor(frame, actor);
    if (overlays.velocity && Math.abs(actor.velocity.x) > .001) {
      const length = Math.sign(actor.velocity.x) * Math.min(125, Math.max(28, Math.abs(actor.velocity.x) * 6));
      drawVector(frame, { x: position.x, y: position.y - 55 }, { x: length, y: 0 }, palette.green, `v = ${actor.velocity.x.toFixed(2)} m/s`);
    }
    if (overlays.acceleration && Math.abs(actor.acceleration.x) > .001) {
      const length = Math.sign(actor.acceleration.x) * Math.min(105, Math.max(25, Math.abs(actor.acceleration.x) * 10));
      drawVector(frame, { x: position.x, y: position.y - 88 }, { x: length, y: 0 }, palette.red, `a = ${actor.acceleration.x.toFixed(2)} m/s²`);
    }
  });
  const force = sample(simulation.values?.force, simulation.time, frame.time, frame.index);
  if (simulation.values?.force?.length) drawBadge(ctx, `F = ${force.toFixed(2)} N`, 105, 36, palette.amber);
}

function drawProtractor(frame: SceneFrame, center: Point, angle: number) {
  const { ctx, palette } = frame;
  ctx.save(); ctx.fillStyle = "rgba(56,189,248,.10)"; ctx.strokeStyle = "rgba(56,189,248,.68)"; ctx.lineWidth = 1.5;
  ctx.beginPath(); ctx.moveTo(center.x, center.y); ctx.arc(center.x, center.y, 42, -angle, 0); ctx.closePath(); ctx.fill(); ctx.stroke();
  for (let i = 1; i < 6; i++) {
    const a = -angle + angle * i / 6;
    ctx.beginPath(); ctx.moveTo(center.x + Math.cos(a) * 36, center.y + Math.sin(a) * 36); ctx.lineTo(center.x + Math.cos(a) * 42, center.y + Math.sin(a) * 42); ctx.stroke();
  }
  ctx.fillStyle = palette.cyan; ctx.font = "700 11px ui-monospace, Consolas, monospace"; ctx.textAlign = "center";
  ctx.fillText(`θ = ${(angle * 180 / Math.PI).toFixed(0)}°`, center.x + 58, center.y - 21); ctx.restore();
}

function drawProjectileScene(frame: SceneFrame) {
  const { ctx, width, height, simulation, presentation, overlays, palette } = frame;
  drawBackground(frame); drawGrid(frame);
  const baseline = height - 64;
  paintEnvironment(frame, baseline);
  const config = presentation.actors?.[0];
  if (!config) return;
  const xValues = readSeries(simulation, config.x);
  const yValues = readSeries(simulation, config.y);
  const [xMin, xMax] = extent([...xValues, 0], [0, 10]);
  const [, yMax] = extent([...yValues, 0], [0, 10]);
  const left = 90; const right = width - 68; const top = 55;
  const xAt = (value: number) => mapRange(value, xMin, xMax, left, right);
  const yAt = (value: number) => mapRange(Math.max(0, value), 0, Math.max(1, yMax), baseline, top);
  const state = actorState(frame, config);
  const actor: ActorFrame = { ...state, position: { x: xAt(state.world.x), y: yAt(state.world.y) } };
  const launch = { x: xAt(xValues[0] ?? 0), y: yAt(yValues[0] ?? 0) };
  const angle = Math.atan2(readSeries(simulation, config.vy)[0] ?? 0, readSeries(simulation, config.vx)[0] ?? 1);
  presentation.props?.forEach(prop => propRegistry[prop]?.(ctx, launch, angle, palette));
  drawProtractor(frame, launch, Math.max(0, angle));
  if (overlays.trajectory) {
    ctx.save(); ctx.strokeStyle = palette.cyan; ctx.lineWidth = 2.5; ctx.setLineDash([3, 7]);
    ctx.shadowColor = palette.cyan; ctx.shadowBlur = 10; ctx.beginPath();
    xValues.forEach((x, index) => { const px = xAt(x); const py = yAt(yValues[index] ?? 0); if (index === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py); });
    ctx.stroke(); ctx.restore();
    ctx.strokeStyle = "rgba(16,185,129,.55)"; ctx.setLineDash([4, 5]);
    ctx.beginPath(); ctx.moveTo(actor.position.x, actor.position.y); ctx.lineTo(actor.position.x, baseline); ctx.stroke(); ctx.setLineDash([]);
    drawBadge(ctx, `y = ${Math.max(0, state.world.y).toFixed(2)} m`, actor.position.x + 45, (actor.position.y + baseline) / 2, palette.green);
  }
  paintActor(frame, actor);
  if (overlays.velocity && Math.hypot(actor.velocity.x, actor.velocity.y) > .001) {
    const magnitude = Math.hypot(actor.velocity.x, actor.velocity.y);
    const scale = Math.min(7, 110 / Math.max(1, magnitude));
    drawVector(frame, actor.position, { x: actor.velocity.x * scale, y: -actor.velocity.y * scale }, palette.green, `v = ${magnitude.toFixed(2)} m/s`);
  }
  if (overlays.acceleration) drawVector(frame, actor.position, { x: 0, y: 48 }, palette.red, "g = 9.81 m/s²");
  drawRuler(frame, xMin, xMax, baseline, left, right);
}

function drawCollisionScene(frame: SceneFrame) {
  drawBackground(frame); drawGrid(frame);
  const baseline = frame.height * .54;
  paintEnvironment(frame, baseline);
  const configs = frame.presentation.actors ?? [];
  const allX = configs.flatMap(config => readSeries(frame.simulation, config.x));
  const [min, max] = extent(allX, [0, 8]);
  const left = 92; const right = frame.width - 92;
  const actors = configs.map(config => {
    const state = actorState(frame, config);
    const position = { x: mapRange(state.world.x, min, max, left, right), y: baseline + (config.lane ?? 0) * 56 - 17 };
    return { ...state, position } satisfies ActorFrame;
  });
  actors.forEach(actor => {
    paintActor(frame, actor);
    if (frame.overlays.velocity && Math.abs(actor.velocity.x) > .001) {
      const length = Math.sign(actor.velocity.x) * Math.min(110, Math.max(30, Math.abs(actor.velocity.x) * 8));
      drawVector(frame, { x: actor.position.x, y: actor.position.y - 48 }, { x: length, y: 0 }, actor.config.lane ? frame.palette.amber : frame.palette.cyan, `${actor.label ?? ""} · v=${actor.velocity.x.toFixed(2)}`);
    }
  });
  const collisionTime = frame.simulation.values?.collisionTime?.[0];
  const currentTime = frame.time ?? frame.simulation.time[frame.index] ?? 0;
  if (hasEffect(frame, "collision.flash") && Number.isFinite(collisionTime) && Math.abs(currentTime - collisionTime) < .12 && actors.length > 1) {
    const x = (actors[0].position.x + actors[1].position.x) / 2;
    const y = (actors[0].position.y + actors[1].position.y) / 2;
    frame.ctx.save(); frame.ctx.strokeStyle = frame.palette.amber; frame.ctx.lineWidth = 3; frame.ctx.shadowColor = frame.palette.amber; frame.ctx.shadowBlur = 18;
    for (let ray = 0; ray < 10; ray++) { const angle = ray * Math.PI / 5; frame.ctx.beginPath(); frame.ctx.moveTo(x + Math.cos(angle) * 13, y + Math.sin(angle) * 13); frame.ctx.lineTo(x + Math.cos(angle) * 28, y + Math.sin(angle) * 28); frame.ctx.stroke(); }
    frame.ctx.restore();
  }
  drawRuler(frame, min, max, baseline + 88, left, right);
}

function coilOffset(index: number, total: number) {
  if (index === total) return 0;
  return index % 2 ? -15 : 15;
}

function drawSpringScene(frame: SceneFrame) {
  const { ctx, width, height, simulation, presentation, overlays, palette } = frame;
  drawBackground(frame); drawGrid(frame);
  const baseline = height * .67;
  paintEnvironment(frame, baseline);
  const config = presentation.actors?.[0];
  if (!config) return;
  const xValues = readSeries(simulation, config.x);
  const [min, max] = extent(xValues, [-1, 1]);
  const left = 190; const right = width - 90;
  const state = actorState(frame, config);
  const position = { x: mapRange(state.world.x, min, max, left + 130, right), y: baseline - 29 };
  const actor: ActorFrame = { ...state, position };
  const anchor = { x: 92, y: position.y };
  ctx.save(); ctx.strokeStyle = palette.cyan; ctx.lineWidth = 4; ctx.lineJoin = "round"; ctx.shadowColor = palette.cyan; ctx.shadowBlur = 8;
  ctx.beginPath(); ctx.moveTo(anchor.x, anchor.y);
  const coils = 16;
  for (let i = 1; i <= coils; i++) {
    const x = anchor.x + (position.x - 30 - anchor.x) * i / coils;
    const y = anchor.y + coilOffset(i, coils);
    ctx.lineTo(x, y);
  }
  ctx.stroke(); ctx.restore();
  ctx.strokeStyle = "rgba(148,163,184,.6)"; ctx.setLineDash([5, 6]);
  const equilibriumX = mapRange(0, min, max, left + 130, right);
  ctx.beginPath(); ctx.moveTo(equilibriumX, 68); ctx.lineTo(equilibriumX, baseline + 33); ctx.stroke(); ctx.setLineDash([]);
  drawBadge(ctx, "x = 0", equilibriumX, 53, palette.muted);
  paintActor(frame, actor);
  if (overlays.velocity && Math.abs(actor.velocity.x) > .001) drawVector(frame, { x: position.x, y: position.y - 48 }, { x: Math.sign(actor.velocity.x) * Math.min(110, Math.max(25, Math.abs(actor.velocity.x) * 10)), y: 0 }, palette.green, `v=${actor.velocity.x.toFixed(2)} m/s`);
  if (overlays.acceleration && Math.abs(actor.acceleration.x) > .001) drawVector(frame, { x: position.x, y: position.y - 82 }, { x: Math.sign(actor.acceleration.x) * Math.min(105, Math.max(25, Math.abs(actor.acceleration.x) * 10)), y: 0 }, palette.red, `a=${actor.acceleration.x.toFixed(2)} m/s²`);
  drawRuler(frame, min, max, baseline + 42, left + 130, right);
}

function drawCircuitScene(frame: SceneFrame) {
  const { ctx, width, height, simulation, overlays, palette } = frame;
  drawBackground(frame); drawGrid(frame); paintEnvironment(frame, 0);
  const left = Math.max(105, width * .17); const right = Math.min(width - 105, width * .83);
  const top = Math.max(92, height * .28); const bottom = Math.min(height - 75, height * .72);
  const resistorLeft = width * .4; const resistorRight = width * .6;
  const voltage = sample(simulation.values?.voltage, simulation.time, frame.time, frame.index);
  const current = sample(simulation.values?.current, simulation.time, frame.time, frame.index);
  const maxVoltage = Math.max(.001, ...(simulation.values?.voltage ?? []).map(Math.abs));
  const fill = Math.min(1, Math.abs(voltage) / maxVoltage);
  ctx.save(); ctx.strokeStyle = "#5eead4"; ctx.lineWidth = 4; ctx.lineCap = "round"; ctx.lineJoin = "round";
  ctx.shadowColor = "rgba(45,212,191,.45)"; ctx.shadowBlur = 9;
  ctx.beginPath(); ctx.moveTo(left, top); ctx.lineTo(resistorLeft, top);
  for (let i = 0; i <= 8; i++) ctx.lineTo(resistorLeft + (resistorRight - resistorLeft) * i / 8, top + (i % 2 ? -15 : 15));
  ctx.lineTo(right, top); ctx.lineTo(right, bottom); ctx.lineTo(left, bottom); ctx.lineTo(left, top); ctx.stroke(); ctx.restore();
  ctx.strokeStyle = "#f8fafc"; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.moveTo(left - 28, height * .47); ctx.lineTo(left + 28, height * .47); ctx.moveTo(left - 16, height * .53); ctx.lineTo(left + 16, height * .53); ctx.stroke();
  ctx.fillStyle = palette.text; ctx.font = "700 12px ui-monospace, Consolas, monospace"; ctx.textAlign = "center";
  ctx.fillText("SOURCE", left, height * .59); ctx.fillText("R", width * .5, top - 28);
  ctx.strokeStyle = "#f8fafc"; ctx.lineWidth = 4;
  ctx.beginPath(); ctx.moveTo(right - 34, height * .47); ctx.lineTo(right + 34, height * .47); ctx.moveTo(right - 34, height * .53); ctx.lineTo(right + 34, height * .53); ctx.stroke();
  if (hasEffect(frame, "circuit.capacitor-glow")) {
    const glow = ctx.createLinearGradient(right - 30, height * .53, right + 30, height * .47);
    glow.addColorStop(0, "rgba(56,189,248,.12)"); glow.addColorStop(1, `rgba(56,189,248,${.15 + fill * .7})`);
    ctx.fillStyle = glow; ctx.shadowColor = palette.cyan; ctx.shadowBlur = 18 * fill;
    ctx.fillRect(right - 29, height * .53 - (height * .06 * fill), 58, height * .06 * fill); ctx.shadowBlur = 0;
  }
  ctx.fillStyle = palette.cyan; ctx.fillText(`Uc = ${voltage.toFixed(2)} V`, right, height * .62);
  if (overlays.velocity && hasEffect(frame, "circuit.current-flow") && Math.abs(current) > .000001) {
    const phase = ((frame.time ?? 0) * Math.max(.2, Math.abs(current) * 10)) % 1;
    for (let i = 0; i < 7; i++) {
      const ratio = (phase + i / 7) % 1;
      const x = left + ratio * (right - left);
      ctx.fillStyle = palette.amber; ctx.shadowColor = palette.amber; ctx.shadowBlur = 9;
      ctx.beginPath(); ctx.arc(x, top, 4, 0, Math.PI * 2); ctx.fill();
    }
    ctx.shadowBlur = 0; drawBadge(ctx, `I = ${current.toFixed(4)} A`, width * .5, top + 54, palette.amber);
  }
  if (overlays.trajectory && simulation.values?.voltage?.length) {
    const values = simulation.values.voltage;
    const graphLeft = width * .35; const graphRight = width * .65; const graphBottom = bottom - 18;
    ctx.strokeStyle = "rgba(148,163,184,.28)"; ctx.lineWidth = 1; ctx.strokeRect(graphLeft, graphBottom - 42, graphRight - graphLeft, 42);
    ctx.strokeStyle = palette.blue; ctx.lineWidth = 2; ctx.beginPath();
    values.forEach((value, index) => {
      const x = graphLeft + index / Math.max(1, values.length - 1) * (graphRight - graphLeft);
      const y = graphBottom - Math.abs(value) / maxVoltage * 37;
      if (index === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
    });
    ctx.stroke();
  }
}

export const sceneRegistry: Record<string, ScenePainter> = {
  motion_1d: drawMotionScene,
  projectile_2d: drawProjectileScene,
  collision_1d: drawCollisionScene,
  spring_1d: drawSpringScene,
  rc_circuit: drawCircuitScene,
};

export function renderScene(frame: SceneFrame): boolean {
  const painter = sceneRegistry[frame.simulation.visualization?.scene];
  if (!painter) return false;
  painter(frame);
  return true;
}
