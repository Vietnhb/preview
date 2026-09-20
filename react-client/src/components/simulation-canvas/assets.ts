import type { ActorFrame, CanvasPalette, Point } from "./model";

export type AssetPainter = (
  ctx: CanvasRenderingContext2D,
  actor: ActorFrame,
  palette: CanvasPalette,
) => void;
export type PropPainter = (
  ctx: CanvasRenderingContext2D,
  anchor: Point,
  angle: number,
  palette: CanvasPalette,
) => void;

function roundedPath(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
) {
  ctx.beginPath();
  ctx.roundRect(x, y, width, height, radius);
}

function drawCart(
  ctx: CanvasRenderingContext2D,
  actor: ActorFrame,
  palette: CanvasPalette,
  orange: boolean,
) {
  const { x, y } = actor.position;
  const scale = actor.scale ?? 1;
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(scale, scale);
  const body = ctx.createLinearGradient(-38, -24, 40, 18);
  if (orange) {
    body.addColorStop(0, "#fdba74");
    body.addColorStop(0.45, "#f97316");
    body.addColorStop(1, "#9a3412");
  } else {
    body.addColorStop(0, "#67e8f9");
    body.addColorStop(0.42, "#2563eb");
    body.addColorStop(1, "#1e3a8a");
  }
  roundedPath(ctx, -40, -21, 80, 37, 9);
  ctx.fillStyle = body;
  ctx.fill();
  ctx.strokeStyle = orange ? "#fed7aa" : "#bfdbfe";
  ctx.lineWidth = 1.5;
  ctx.stroke();
  ctx.fillStyle = "rgba(9,13,22,.84)";
  ctx.beginPath();
  ctx.moveTo(-21, -21);
  ctx.lineTo(-8, -35);
  ctx.lineTo(17, -35);
  ctx.lineTo(29, -21);
  ctx.closePath();
  ctx.fill();
  ctx.strokeStyle = "rgba(255,255,255,.38)";
  ctx.beginPath();
  ctx.moveTo(-5, -32);
  ctx.lineTo(7, -23);
  ctx.stroke();
  for (const wheelX of [-24, 24]) {
    ctx.fillStyle = "#080b12";
    ctx.beginPath();
    ctx.arc(wheelX, 17, 11, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#64748b";
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.fillStyle = "#cbd5e1";
    ctx.beginPath();
    ctx.arc(wheelX, 17, 6.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#334155";
    ctx.lineWidth = 1.3;
    for (let spoke = 0; spoke < 4; spoke++) {
      const angle = actor.distance / 4 + (spoke * Math.PI) / 2;
      ctx.beginPath();
      ctx.moveTo(wheelX, 17);
      ctx.lineTo(wheelX + Math.cos(angle) * 6, 17 + Math.sin(angle) * 6);
      ctx.stroke();
    }
  }
  if (actor.label) {
    ctx.fillStyle = palette.text;
    ctx.font = "700 11px Inter, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(actor.label, 0, 4);
  }
  ctx.restore();
}

function drawSportCar(ctx: CanvasRenderingContext2D, actor: ActorFrame) {
  const { x, y } = actor.position;
  const scale = actor.scale ?? 1;
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(scale, scale);
  const body = ctx.createLinearGradient(-48, -27, 48, 20);
  body.addColorStop(0, "#67e8f9");
  body.addColorStop(0.35, "#3b82f6");
  body.addColorStop(0.7, "#1d4ed8");
  body.addColorStop(1, "#172554");
  ctx.beginPath();
  ctx.moveTo(-49, 12);
  ctx.lineTo(-46, -7);
  ctx.lineTo(-28, -12);
  ctx.lineTo(-13, -29);
  ctx.lineTo(16, -29);
  ctx.lineTo(31, -12);
  ctx.lineTo(47, -5);
  ctx.lineTo(49, 14);
  ctx.closePath();
  ctx.fillStyle = body;
  ctx.fill();
  ctx.strokeStyle = "#bae6fd";
  ctx.lineWidth = 1.6;
  ctx.stroke();
  const glass = ctx.createLinearGradient(-14, -28, 24, -10);
  glass.addColorStop(0, "rgba(224,242,254,.72)");
  glass.addColorStop(0.28, "rgba(30,64,175,.62)");
  glass.addColorStop(1, "rgba(8,15,35,.9)");
  ctx.beginPath();
  ctx.moveTo(-10, -27);
  ctx.lineTo(14, -27);
  ctx.lineTo(26, -12);
  ctx.lineTo(-22, -12);
  ctx.closePath();
  ctx.fillStyle = glass;
  ctx.fill();
  ctx.fillStyle = "#fef08a";
  ctx.fillRect(45, -2, 5, 9);
  ctx.fillStyle = "#fb7185";
  ctx.fillRect(-50, -1, 5, 8);
  for (const wheelX of [-27, 28]) {
    ctx.fillStyle = "#05070c";
    ctx.beginPath();
    ctx.arc(wheelX, 16, 12, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#64748b";
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.fillStyle = "#dbeafe";
    ctx.beginPath();
    ctx.arc(wheelX, 16, 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#334155";
    ctx.lineWidth = 1.4;
    for (let spoke = 0; spoke < 5; spoke++) {
      const angle = actor.distance / 4 + spoke * Math.PI * 0.4;
      ctx.beginPath();
      ctx.moveTo(wheelX, 16);
      ctx.lineTo(wheelX + Math.cos(angle) * 6.5, 16 + Math.sin(angle) * 6.5);
      ctx.stroke();
    }
  }
  ctx.restore();
}

function drawBlock(ctx: CanvasRenderingContext2D, actor: ActorFrame) {
  const { x, y } = actor.position;
  ctx.save();
  ctx.translate(x, y);
  if (actor.rotation) ctx.rotate(actor.rotation);
  const gradient = ctx.createLinearGradient(-26, -26, 28, 28);
  gradient.addColorStop(0, "#fef3c7");
  gradient.addColorStop(0.35, "#fbbf24");
  gradient.addColorStop(1, "#b45309");
  roundedPath(ctx, -28, -28, 56, 56, 8);
  ctx.fillStyle = gradient;
  ctx.fill();
  ctx.strokeStyle = "#fef9c3";
  ctx.lineWidth = 1.7;
  ctx.stroke();
  ctx.fillStyle = "rgba(255,255,255,.38)";
  roundedPath(ctx, -20, -19, 39, 7, 3);
  ctx.fill();
  if (actor.label) {
    ctx.fillStyle = "#422006";
    ctx.font = "800 14px Inter, sans-serif";
    ctx.textAlign = "center";
    ctx.fillText(actor.label, 0, 6);
  }
  ctx.restore();
}

function drawProjectile(ctx: CanvasRenderingContext2D, actor: ActorFrame) {
  const { x, y } = actor.position;
  ctx.save();
  const orb = ctx.createRadialGradient(x - 4, y - 5, 2, x, y, 15);
  orb.addColorStop(0, "#ffffff");
  orb.addColorStop(0.28, "#a5f3fc");
  orb.addColorStop(0.65, "#0ea5e9");
  orb.addColorStop(1, "#1d4ed8");
  ctx.fillStyle = orb;
  ctx.beginPath();
  ctx.arc(x, y, 12, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "rgba(255,255,255,.9)";
  ctx.lineWidth = 1.6;
  ctx.stroke();
  ctx.restore();
}

export const assetRegistry: Record<string, AssetPainter> = {
  "object.cart.blue": (ctx, actor, palette) =>
    drawCart(ctx, actor, palette, false),
  "object.cart.orange": (ctx, actor, palette) =>
    drawCart(ctx, actor, palette, true),
  "vehicle.sport.blue": drawSportCar,
  "object.block.amber": drawBlock,
  "projectile.energy": drawProjectile,
  // Stable capability names exposed to AI-generated specs.
  cart: (ctx, actor, palette) => drawCart(ctx, actor, palette, false),
  ball: drawProjectile,
  block: drawBlock,
};

function drawTower(
  ctx: CanvasRenderingContext2D,
  anchor: Point,
  _angle: number,
  palette: CanvasPalette,
) {
  const height = Math.min(150, Math.max(70, anchor.y - 45));
  const left = anchor.x - 42;
  const top = anchor.y;
  const bottom = top + height;
  const gradient = ctx.createLinearGradient(left, top, anchor.x, top);
  gradient.addColorStop(0, "#1e293b");
  gradient.addColorStop(0.5, "#64748b");
  gradient.addColorStop(1, "#172033");
  ctx.fillStyle = gradient;
  ctx.fillRect(left, top, 42, height);
  ctx.strokeStyle = palette.muted;
  ctx.lineWidth = 1.3;
  ctx.strokeRect(left, top, 42, height);
  ctx.strokeStyle = "rgba(255,255,255,.16)";
  for (let y = top; y < bottom - 18; y += 22) {
    ctx.beginPath();
    ctx.moveTo(left, y);
    ctx.lineTo(anchor.x, y + 22);
    ctx.moveTo(anchor.x, y);
    ctx.lineTo(left, y + 22);
    ctx.stroke();
  }
}

function drawCannon(
  ctx: CanvasRenderingContext2D,
  anchor: Point,
  angle: number,
) {
  ctx.save();
  ctx.translate(anchor.x, anchor.y);
  ctx.rotate(-angle);
  const barrel = ctx.createLinearGradient(0, -7, 45, 7);
  barrel.addColorStop(0, "#334155");
  barrel.addColorStop(0.5, "#e2e8f0");
  barrel.addColorStop(1, "#475569");
  roundedPath(ctx, -2, -7, 45, 14, 4);
  ctx.fillStyle = barrel;
  ctx.fill();
  ctx.strokeStyle = "#f8fafc";
  ctx.stroke();
  ctx.restore();
  ctx.fillStyle = "#0f172a";
  ctx.beginPath();
  ctx.arc(anchor.x, anchor.y, 10, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = "#94a3b8";
  ctx.stroke();
}

export const propRegistry: Record<string, PropPainter> = {
  "structure.launch-tower": drawTower,
  "launcher.cannon": drawCannon,
};
