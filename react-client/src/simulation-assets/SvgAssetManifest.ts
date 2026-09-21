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
import battery from "./svg/battery.svg?raw";
import resistor from "./svg/resistor.svg?raw";
import capacitor from "./svg/capacitor.svg?raw";
import lens from "./svg/lens.svg?raw";
import magnet from "./svg/magnet.svg?raw";
import speaker from "./svg/speaker.svg?raw";
import calorimeter from "./svg/calorimeter.svg?raw";
import atom from "./svg/atom.svg?raw";
import pulley from "./svg/pulley.svg?raw";
import lever from "./svg/lever.svg?raw";
import planetSystem from "./svg/planet-system.svg?raw";
import buoy from "./svg/buoy.svg?raw";
import pistonCylinder from "./svg/piston-cylinder.svg?raw";
import thermometer from "./svg/thermometer.svg?raw";
import mirror from "./svg/mirror.svg?raw";
import slit from "./svg/slit.svg?raw";
import diode from "./svg/diode.svg?raw";
import detector from "./svg/detector.svg?raw";
import photon from "./svg/photon.svg?raw";
import solarPanel from "./svg/solar-panel.svg?raw";
import measurementProbe from "./svg/measurement-probe.svg?raw";
import antenna from "./svg/antenna.svg?raw";

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
  {
    id: "circuit-battery",
    kind: "prop",
    tags: ["circuit", "battery", "source", "voltage", "mạch", "pin"],
    aliases: ["battery", "cell", "nguon", "pin.dien"],
    variants: [{ id: "battery", tags: ["battery", "cell"], aliases: ["circuit.battery"], markup: battery, viewBox: [0, 0, 64, 72], anchor: "center" }],
  },
  {
    id: "circuit-resistor",
    kind: "prop",
    tags: ["circuit", "resistor", "resistance", "mạch", "dien tro"],
    aliases: ["resistor", "dien.tro"],
    variants: [{ id: "resistor", tags: ["resistor"], aliases: ["circuit.resistor"], markup: resistor, viewBox: [0, 0, 120, 40], anchor: "center" }],
  },
  {
    id: "circuit-capacitor",
    kind: "prop",
    tags: ["circuit", "capacitor", "capacitance", "mạch", "tu dien"],
    aliases: ["capacitor", "tu.dien"],
    variants: [{ id: "capacitor", tags: ["capacitor"], aliases: ["circuit.capacitor"], markup: capacitor, viewBox: [0, 0, 88, 64], anchor: "center" }],
  },
  {
    id: "optical-lens",
    kind: "prop",
    tags: ["optics", "lens", "light", "quang", "thau kinh"],
    aliases: ["lens", "thau.kinh"],
    variants: [{ id: "lens", tags: ["lens", "convex"], aliases: ["optics.lens"], markup: lens, viewBox: [0, 0, 42, 140], anchor: "center" }],
  },
  {
    id: "magnet",
    kind: "prop",
    tags: ["magnet", "magnetic", "field", "nam cham", "tu truong"],
    aliases: ["nam.cham", "magnetic.magnet"],
    variants: [{ id: "magnet", tags: ["magnet", "field"], aliases: ["magnetic.magnet"], markup: magnet, viewBox: [0, 0, 100, 64], anchor: "center" }],
  },
  {
    id: "speaker",
    kind: "prop",
    tags: ["sound", "acoustic", "wave", "speaker", "am thanh", "song"],
    aliases: ["loa", "sound.source"],
    variants: [{ id: "speaker", tags: ["speaker", "sound"], aliases: ["wave.speaker"], markup: speaker, viewBox: [0, 0, 92, 72], anchor: "center" }],
  },
  {
    id: "calorimeter",
    kind: "prop",
    tags: ["thermal", "heat", "calorimeter", "nhiet", "binh nhiet luong"],
    aliases: ["calorimeter", "binh.nhiet.luong"],
    variants: [{ id: "calorimeter", tags: ["thermal", "calorimeter"], aliases: ["thermal.calorimeter"], markup: calorimeter, viewBox: [0, 0, 80, 100], anchor: "center" }],
  },
  {
    id: "atom",
    kind: "prop",
    tags: ["atom", "atomic", "nuclear", "radiation", "nguyen tu", "hat nhan"],
    aliases: ["nguyen.tu", "atomic.model"],
    variants: [{ id: "atom", tags: ["atom", "atomic"], aliases: ["modern.atom"], markup: atom, viewBox: [0, 0, 100, 100], anchor: "center" }],
  },
  {
    id: "mechanics-pulley",
    kind: "prop",
    tags: ["mechanics", "pulley", "force", "work", "energy", "roley", "rong roc"],
    aliases: ["pulley", "rong.rooc"],
    variants: [{ id: "pulley", tags: ["pulley", "force"], aliases: ["mechanics.pulley"], markup: pulley, viewBox: [0, 0, 96, 96], anchor: "center" }],
  },
  {
    id: "mechanics-lever",
    kind: "prop",
    tags: ["mechanics", "lever", "moment", "equilibrium", "don bay", "can bang"],
    aliases: ["lever", "don.bay"],
    variants: [{ id: "lever", tags: ["lever", "moment"], aliases: ["mechanics.lever"], markup: lever, viewBox: [0, 0, 140, 70], anchor: "center" }],
  },
  {
    id: "planet-system",
    kind: "prop",
    tags: ["gravity", "orbit", "astronomy", "planet", "eclipse", "hanh tinh", "quy dao"],
    aliases: ["planet", "he mat troi", "astronomy.system"],
    variants: [{ id: "planet-system", tags: ["planet", "orbit"], aliases: ["mechanics.planet-system"], markup: planetSystem, viewBox: [0, 0, 140, 96], anchor: "center" }],
  },
  {
    id: "buoyant-object",
    kind: "prop",
    tags: ["hydrostatics", "buoyancy", "fluid", "buoyant", "luc day", "noi"],
    aliases: ["buoy", "hydrostatic", "luc.day.noi"],
    variants: [{ id: "buoy", tags: ["buoyancy", "fluid"], aliases: ["mechanics.buoy"], markup: buoy, viewBox: [0, 0, 90, 110], anchor: "center" }],
  },
  {
    id: "piston-cylinder",
    kind: "prop",
    tags: ["thermal", "gas", "piston", "cylinder", "thermodynamics", "khi", "xi lanh"],
    aliases: ["piston", "piston-cylinder", "pittong"],
    variants: [{ id: "piston-cylinder", tags: ["piston", "gas"], aliases: ["thermal.piston"], markup: pistonCylinder, viewBox: [0, 0, 92, 130], anchor: "center" }],
  },
  {
    id: "thermometer",
    kind: "prop",
    tags: ["thermal", "temperature", "thermometer", "nhiet do", "do nhiet"],
    aliases: ["thermometer", "nhiet.ke"],
    variants: [{ id: "thermometer", tags: ["temperature", "thermometer"], aliases: ["thermal.thermometer"], markup: thermometer, viewBox: [0, 0, 42, 120], anchor: "center" }],
  },
  {
    id: "optical-mirror",
    kind: "prop",
    tags: ["optics", "mirror", "reflection", "guong", "phan xa"],
    aliases: ["mirror", "guong.phang"],
    variants: [{ id: "mirror", tags: ["mirror", "reflection"], aliases: ["optics.mirror"], markup: mirror, viewBox: [0, 0, 30, 140], anchor: "center" }],
  },
  {
    id: "diffraction-slit",
    kind: "prop",
    tags: ["optics", "diffraction", "interference", "slit", "khe", "giao thoa"],
    aliases: ["slit", "diffraction.slit"],
    variants: [{ id: "slit", tags: ["slit", "diffraction"], aliases: ["optics.slit"], markup: slit, viewBox: [0, 0, 70, 130], anchor: "center" }],
  },
  {
    id: "circuit-diode",
    kind: "prop",
    tags: ["circuit", "diode", "semiconductor", "mạch", "diot"],
    aliases: ["diode", "diot"],
    variants: [{ id: "diode", tags: ["diode", "semiconductor"], aliases: ["circuit.diode"], markup: diode, viewBox: [0, 0, 100, 64], anchor: "center" }],
  },
  {
    id: "radiation-detector",
    kind: "prop",
    tags: ["radiation", "detector", "xray", "ct", "mri", "sensor", "dau do", "cam bien"],
    aliases: ["detector", "dau.do.buc.xa"],
    variants: [{ id: "detector", tags: ["detector", "sensor"], aliases: ["modern.detector"], markup: detector, viewBox: [0, 0, 100, 90], anchor: "center" }],
  },
  {
    id: "photon",
    kind: "prop",
    tags: ["photon", "light", "photoelectric", "atomic", "luong tu"],
    aliases: ["photon", "luong.tu.anh.sang"],
    variants: [{ id: "photon", tags: ["photon", "light"], aliases: ["modern.photon"], markup: photon, viewBox: [0, 0, 64, 64], anchor: "center" }],
  },
  {
    id: "solar-panel",
    kind: "prop",
    tags: ["energy", "renewable", "solar", "environment", "nang luong", "mat troi"],
    aliases: ["solar", "pin.mat.troi"],
    variants: [{ id: "solar-panel", tags: ["solar", "renewable"], aliases: ["energy.solar-panel"], markup: solarPanel, viewBox: [0, 0, 120, 82], anchor: "center" }],
  },
  {
    id: "measurement-probe",
    kind: "prop",
    tags: ["measurement", "probe", "sensor", "ruler", "do luong", "dau do"],
    aliases: ["probe", "sensor", "dau.do"],
    variants: [{ id: "measurement-probe", tags: ["probe", "measurement"], aliases: ["measurement.probe"], markup: measurementProbe, viewBox: [0, 0, 110, 64], anchor: "center" }],
  },
  {
    id: "radio-antenna",
    kind: "prop",
    tags: ["radio", "antenna", "signal", "communication", "anten", "song vo tuyen"],
    aliases: ["antenna", "anten"],
    variants: [{ id: "antenna", tags: ["antenna", "radio"], aliases: ["wave.antenna"], markup: antenna, viewBox: [0, 0, 100, 100], anchor: "center" }],
  },
];
