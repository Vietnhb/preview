import type { AssetKind, SelectableAssetDefinition, SelectableAssetVariant } from "./AssetSelector";
import cartBlue from "./svg/cart-blue.svg?raw";
import cartOrange from "./svg/cart-orange.svg?raw";
import sportBlue from "./svg/sport-blue.svg?raw";
import sportRed from "./svg/sport-red.svg?raw";
import blockAmber from "./svg/block-amber.svg?raw";
import blockCyan from "./svg/block-cyan.svg?raw";
import projectileEnergy from "./svg/projectile-energy.svg?raw";
import projectileFire from "./svg/projectile-fire.svg?raw";
import launchTower from "./svg/launch-tower.svg?raw";
import cannon from "./svg/cannon.svg?raw";
import springCoil from "./svg/spring-coil.svg?raw";

export type SvgAssetVariant = SelectableAssetVariant & {
  markup: string;
  viewBox: readonly [number, number, number, number];
  anchor?: "center" | "topRight" | "right";
};

export type SvgAssetDefinition = Omit<SelectableAssetDefinition, "variants"> & {
  kind: AssetKind;
  variants: readonly SvgAssetVariant[];
};

const actor = (id: string, tags: readonly string[], markup: string, viewBox: readonly [number, number, number, number], aliases: readonly string[] = []): SvgAssetVariant => ({ id, tags, aliases, markup, viewBox });

export const svgAssetManifest: readonly SvgAssetDefinition[] = [
  {
    id: "cart",
    kind: "actor",
    tags: ["object", "vehicle", "dynamics", "collision"],
    aliases: ["mass-cart", "rail-cart"],
    variants: [
      actor("cart-blue", ["cart", "blue", "cool"], cartBlue, [0, 0, 96, 64], ["object.cart.blue"]),
      actor("cart-orange", ["cart", "orange", "warm"], cartOrange, [0, 0, 96, 64], ["object.cart.orange"]),
    ],
  },
  {
    id: "sport-car",
    kind: "actor",
    tags: ["vehicle", "car", "motion", "kinematics"],
    aliases: ["sport", "automobile"],
    variants: [
      actor("sport-blue", ["blue", "cool"], sportBlue, [0, 0, 112, 64], ["vehicle.sport.blue"]),
      actor("sport-red", ["red", "warm"], sportRed, [0, 0, 112, 64], ["vehicle.sport.red"]),
    ],
  },
  {
    id: "mass-block",
    kind: "actor",
    tags: ["object", "mass", "block", "spring", "mechanics"],
    aliases: ["body", "weight"],
    variants: [
      actor("block-amber", ["amber", "yellow", "warm"], blockAmber, [0, 0, 64, 64], ["object.block.amber"]),
      actor("block-cyan", ["cyan", "blue", "cool"], blockCyan, [0, 0, 64, 64], ["object.block.cyan"]),
    ],
  },
  {
    id: "projectile",
    kind: "actor",
    tags: ["object", "projectile", "ball", "wave", "optics"],
    aliases: ["orb", "particle", "energy"],
    variants: [
      actor("projectile-energy", ["energy", "blue", "cool"], projectileEnergy, [0, 0, 36, 36], ["projectile.energy", "ball"]),
      actor("projectile-fire", ["fire", "thermal", "orange", "warm"], projectileFire, [0, 0, 36, 36], ["projectile.fire"]),
    ],
  },
  {
    id: "launch-tower",
    kind: "prop",
    tags: ["structure", "launcher", "projectile", "range"],
    aliases: ["tower", "launch-tower"],
    variants: [
      { id: "launch-tower", tags: ["tower", "structure"], aliases: ["structure.launch-tower"], markup: launchTower, viewBox: [0, 0, 48, 150], anchor: "topRight" },
    ],
  },
  {
    id: "cannon",
    kind: "prop",
    tags: ["launcher", "projectile", "range"],
    aliases: ["launcher", "cannon"],
    variants: [
      { id: "cannon", tags: ["cannon", "barrel"], aliases: ["launcher.cannon"], markup: cannon, viewBox: [0, 0, 96, 28], anchor: "center" },
    ],
  },
  {
    id: "spring-coil",
    kind: "prop",
    tags: ["spring", "coil", "oscillation", "elastic"],
    aliases: ["spring.coil"],
    variants: [
      { id: "spring-coil", tags: ["spring", "coil"], aliases: ["spring.coil"], markup: springCoil, viewBox: [0, 0, 120, 28], anchor: "right" },
    ],
  },
];
