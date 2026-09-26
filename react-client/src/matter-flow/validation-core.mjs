/** Generic structural checks shared by the browser worker and the Node evidence runner. */
export function validateEngineResolution(Matter, createEngine, params, spec, width, height) {
  const flags = [];
  const metrics = {};
  const coarse = createEngine(params);
  const fine = createEngine(params);
  fine.positionIterations = Math.max(12, fine.positionIterations || 0);
  fine.velocityIterations = Math.max(10, fine.velocityIterations || 0);

  const readBodies = (engine) => {
    const bodies = Matter.Composite.allBodies(engine.world);
    if (bodies.length > 120 || Matter.Composite.allConstraints(engine.world).length > 150)
      throw Error("Simulation exceeds the structural limit.");
    for (const body of bodies) {
      if (![body.position.x, body.position.y, body.velocity.x, body.velocity.y, body.angle].every(Number.isFinite))
        throw Error("Simulation produced a non-finite state.");
    }
    return bodies;
  };
  const momentum = (bodies) => bodies.reduce((sum, body) => ({
    x: sum.x + (body.isStatic ? 0 : body.mass * body.velocity.x),
    y: sum.y + (body.isStatic ? 0 : body.mass * body.velocity.y),
    scale: sum.scale + (body.isStatic ? 0 : body.mass * Math.hypot(body.velocity.x, body.velocity.y)),
  }), { x: 0, y: 0, scale: 0 });
  const kineticEnergy = (bodies) => bodies.reduce((sum, body) => sum + (body.isStatic ? 0 :
    0.5 * body.mass * (body.velocity.x ** 2 + body.velocity.y ** 2)
    + 0.5 * body.inertia * body.angularVelocity ** 2), 0);

  // Matter expands body.bounds along velocity for broad-phase collision checks.
  // Use current vertices here so an already passed body is not mistaken for a
  // future collision, and use relative motion so two moving bodies can meet.
  const geometry = (body) => {
    const bounds = { min: { x: Infinity, y: Infinity }, max: { x: -Infinity, y: -Infinity } };
    for (const vertex of body.vertices) {
      bounds.min.x = Math.min(bounds.min.x, vertex.x);
      bounds.min.y = Math.min(bounds.min.y, vertex.y);
      bounds.max.x = Math.max(bounds.max.x, vertex.x);
      bounds.max.y = Math.max(bounds.max.y, vertex.y);
    }
    return { body, bounds,
      halfWidth: (bounds.max.x - bounds.min.x) / 2,
      halfHeight: (bounds.max.y - bounds.min.y) / 2 };
  };
  const crossesExpandedBounds = (moving, other, relativeVelocity) => {
    const currentlyOverlapping = ["x", "y"].every((axis) =>
      moving.bounds.min[axis] <= other.bounds.max[axis]
      && moving.bounds.max[axis] >= other.bounds.min[axis]);
    if (currentlyOverlapping) return false;
    let enter = 0;
    let exit = 1;
    for (const axis of ["x", "y"]) {
      const start = moving.body.position[axis];
      const delta = relativeVelocity[axis];
      const margin = axis === "x" ? moving.halfWidth : moving.halfHeight;
      const lower = other.bounds.min[axis] - margin;
      const upper = other.bounds.max[axis] + margin;
      if (Math.abs(delta) < 1e-12) {
        if (start < lower || start > upper) return false;
        continue;
      }
      const first = (lower - start) / delta;
      const second = (upper - start) / delta;
      enter = Math.max(enter, Math.min(first, second));
      exit = Math.min(exit, Math.max(first, second));
      if (enter > exit) return false;
    }
    return true;
  };
  let maxSweptTravelToFeatureRatio = 0;
  const inspectSweptCollisions = (bodies) => {
    const shapes = bodies.map(geometry);
    for (let i = 0; i < shapes.length; i += 1) {
      const body = shapes[i];
      for (let j = i + 1; j < shapes.length; j += 1) {
        const other = shapes[j];
        if (body.body.isStatic && other.body.isStatic) continue;
        const relativeVelocity = { x: body.body.velocity.x - other.body.velocity.x,
          y: body.body.velocity.y - other.body.velocity.y };
        const travel = Math.hypot(relativeVelocity.x, relativeVelocity.y);
        if (travel < 0.5) continue;
        const bodyFeature = Math.min(body.halfWidth * 2, body.halfHeight * 2);
        const otherFeature = Math.min(other.halfWidth * 2, other.halfHeight * 2);
        const ratio = travel / Math.max(1, Math.min(bodyFeature, otherFeature));
        if (!crossesExpandedBounds(body, other, relativeVelocity)) continue;
        maxSweptTravelToFeatureRatio = Math.max(maxSweptTravelToFeatureRatio, ratio);
      }
    }
  };

  const initialBodies = readBodies(coarse);
  inspectSweptCollisions(initialBodies);
  const expectedContacts = (Array.isArray(spec?.expectedContacts) ? spec.expectedContacts : [])
    .map((entry) => Array.isArray(entry) ? entry : [entry?.bodyA, entry?.bodyB])
    .filter((pair) => pair.length === 2 && pair.every((id) => typeof id === "string" && id.length > 0));
  const pairKey = (a, b) => [a, b].sort().join("\u0000");
  const observedContacts = new Set();
  if (expectedContacts.length) {
    Matter.Events.on(coarse, "collisionStart", (event) => {
      for (const pair of event.pairs) {
        const a = pair.bodyA.plugin?.physliveId;
        const b = pair.bodyB.plugin?.physliveId;
        if (typeof a === "string" && typeof b === "string") observedContacts.add(pairKey(a, b));
      }
    });
  }
  const gravity = coarse.gravity || coarse.world.gravity;
  const forceFree = spec?.externalForces === false && spec?.friction === false
    && Math.abs(gravity.x) < 1e-12 && Math.abs(gravity.y) < 1e-12
    && initialBodies.every((body) => !body.isStatic && body.friction === 0 && body.frictionAir === 0);
  const energyEligible = forceFree && spec.conservativeInteractions === true
    && initialBodies.every((body) => body.restitution >= 0.999)
    && Matter.Composite.allConstraints(coarse.world).length === 0;
  const initialMomentum = forceFree ? momentum(initialBodies) : null;
  const initialEnergy = energyEligible ? kineticEnergy(initialBodies) : null;
  let maxDeviation = 0;
  let maxMomentumDrift = 0;
  let maxEnergyDrift = 0;

  const requestedSeconds = spec?.durationSeconds;
  const finalFrame = Number.isFinite(requestedSeconds)
    ? Math.min(2400, Math.max(1, Math.ceil(requestedSeconds * 60))) : 120;
  const checkpoints = [...new Set([30, 60, 120, finalFrame]
    .map((frame) => Math.min(frame, finalFrame)))].sort((a, b) => a - b);
  let previous = 0;
  for (const checkpoint of checkpoints) {
    for (let step = previous; step < checkpoint; step += 1) {
      inspectSweptCollisions(readBodies(coarse));
      Matter.Engine.update(coarse, 1000 / 60);
      Matter.Engine.update(fine, 1000 / 120);
      Matter.Engine.update(fine, 1000 / 120);
    }
    previous = checkpoint;
    const coarseBodies = readBodies(coarse);
    const fineBodies = readBodies(fine);
    if (coarseBodies.length !== fineBodies.length) throw Error("Resolution runs produced different body counts.");
    for (let index = 0; index < coarseBodies.length; index += 1) {
      const a = coarseBodies[index];
      const b = fineBodies[index];
      maxDeviation = Math.max(maxDeviation, Math.hypot(a.position.x - b.position.x, a.position.y - b.position.y));
    }
    if (initialMomentum) {
      const current = momentum(coarseBodies);
      maxMomentumDrift = Math.max(maxMomentumDrift,
        Math.hypot(current.x - initialMomentum.x, current.y - initialMomentum.y) / Math.max(Number.EPSILON, initialMomentum.scale));
    }
    if (initialEnergy !== null) {
      maxEnergyDrift = Math.max(maxEnergyDrift,
        Math.abs(kineticEnergy(coarseBodies) - initialEnergy) / Math.max(Number.EPSILON, initialEnergy));
    }
  }
  metrics.maxPositionDeviationPx = maxDeviation;
  metrics.maxSweptTravelToFeatureRatio = maxSweptTravelToFeatureRatio;
  metrics.expectedContactCount = expectedContacts.length;
  metrics.observedExpectedContactCount = expectedContacts.filter(([a, b]) =>
    observedContacts.has(pairKey(a, b))).length;
  if (initialMomentum) metrics.maxRelativeMomentumDrift = maxMomentumDrift;
  if (initialEnergy !== null) metrics.maxRelativeEnergyDrift = maxEnergyDrift;
  if (maxDeviation > Math.max(5, Math.min(width, height) * 0.02))
    flags.push("The simulation changes noticeably at a finer timestep.");
  if (maxSweptTravelToFeatureRatio > 1)
    flags.push("A fast body may cross another body's collision feature in one physics step; contact may be missed.");
  for (const [a, b] of expectedContacts) {
    if (!observedContacts.has(pairKey(a, b)))
      flags.push(`Expected contact between ${a} and ${b} was not observed within the requested duration.`);
  }
  if (initialMomentum && maxMomentumDrift > 0.05)
    flags.push("Momentum drift exceeds the expected tolerance.");
  if (initialEnergy !== null && maxEnergyDrift > 0.08)
    flags.push("Energy drift exceeds the expected tolerance.");
  return { status: flags.length ? "FLAGGED" : "OK", flags, metrics };
}
