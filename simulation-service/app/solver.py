import math
from typing import Any


def solve(schema_id: str, parameters: dict[str, float], duration: float = 5.0, step: float = 0.05) -> dict[str, Any]:
    schema = schema_id.lower()
    times = [round(index * step, 6) for index in range(int(duration / step) + 1)]
    positions: list[dict[str, float]] = []
    velocities: list[dict[str, float]] = []
    accelerations: list[dict[str, float]] = []
    values: dict[str, list[float]] = {}

    if "collision" in schema:
        m1 = max(parameters.get("mass_1", parameters.get("m1", 1.0)), 1e-9)
        m2 = max(parameters.get("mass_2", parameters.get("m2", 1.0)), 1e-9)
        x1_0 = parameters.get("x1_0", parameters.get("x1", 0.0))
        x2_0 = parameters.get("x2_0", parameters.get("x2", 4.0))
        v1 = parameters.get("velocity_1", parameters.get("v1", 2.0))
        v2 = parameters.get("velocity_2", parameters.get("v2", -1.0))

        if "collision_at" in parameters:
            collision_at = parameters["collision_at"]
        elif "collision_time" in parameters:
            collision_at = parameters["collision_time"]
        elif x1_0 < x2_0 and v1 > v2:
            collision_at = (x2_0 - x1_0) / (v1 - v2)
        elif x1_0 > x2_0 and v2 > v1:
            collision_at = (x1_0 - x2_0) / (v2 - v1)
        else:
            collision_at = -1.0

        will_collide = collision_at > 0
        after_v1 = ((m1 - m2) * v1 + 2 * m2 * v2) / (m1 + m2) if will_collide else v1
        after_v2 = ((m2 - m1) * v2 + 2 * m1 * v1) / (m1 + m2) if will_collide else v2
        x1_coll = x1_0 + v1 * collision_at if will_collide else 0.0
        x2_coll = x2_0 + v2 * collision_at if will_collide else 0.0

        x1_series = []
        x2_series = []
        v1_series = []
        v2_series = []
        for t in times:
            p1 = (x1_0 + v1 * t) if (not will_collide or t <= collision_at) else (x1_coll + after_v1 * (t - collision_at))
            p2 = (x2_0 + v2 * t) if (not will_collide or t <= collision_at) else (x2_coll + after_v2 * (t - collision_at))
            cur_v1 = v1 if (not will_collide or t < collision_at) else after_v1
            cur_v2 = v2 if (not will_collide or t < collision_at) else after_v2
            x1_series.append(p1)
            x2_series.append(p2)
            v1_series.append(cur_v1)
            v2_series.append(cur_v2)

        positions = [{"x1": p1, "x2": p2} for p1, p2 in zip(x1_series, x2_series)]
        velocities = [{"v1": v1s, "v2": v2s} for v1s, v2s in zip(v1_series, v2_series)]
        values = {
            "x1": x1_series,
            "x2": x2_series,
            "v1": v1_series,
            "v2": v2_series,
            "collisionTime": [collision_at if will_collide else -1.0],
            "collisionX": [x1_coll if will_collide else -1.0],
        }
        return {"time": times, "positions": positions, "velocities": velocities, "accelerations": accelerations, "values": values, "parameters": parameters}

    if "circuit" in schema or "rc" in schema:
        voltage = parameters.get("voltage", parameters.get("V", parameters.get("u0", 12.0)))
        resistance = parameters.get("resistance", parameters.get("R", parameters.get("r", 6.0)))
        capacitance = parameters.get("capacitance", parameters.get("C", parameters.get("c", 1.0)))
        tau = max(resistance * capacitance, 1e-9)
        current = [voltage / resistance for _ in times]
        capacitor_voltage = [voltage * (1 - math.exp(-time / tau)) for time in times]
        values = {"voltage": capacitor_voltage, "current": current, "power": [voltage * item for item in current]}
        return {"time": times, "positions": positions, "velocities": velocities, "accelerations": accelerations, "values": values, "parameters": parameters}

    if "dynamic" in schema or "force" in schema:
        mass = max(parameters.get("mass", parameters.get("m", 1.0)), 1e-9)
        force = parameters.get("force", parameters.get("F", parameters.get("f", 10.0)))
        acceleration = force / mass
        velocities = [{"x": acceleration * time} for time in times]
        positions = [{"x": 0.5 * acceleration * time * time} for time in times]
        accelerations = [{"x": acceleration} for _ in times]
        values = {"netForce": [force for _ in times], "kineticEnergy": [0.5 * mass * (acceleration * time) ** 2 for time in times]}
        return {"time": times, "positions": positions, "velocities": velocities, "accelerations": accelerations, "values": values, "parameters": parameters}

    velocity = parameters.get("velocity", parameters.get("v0", parameters.get("initial_velocity", 5.0)))
    acceleration = parameters.get("acceleration", parameters.get("a", 0.0))
    gravity = parameters.get("gravity", parameters.get("g", 9.81))
    angle = math.radians(parameters.get("angle", parameters.get("launch_angle", parameters.get("theta", 0.0))))
    vx = velocity * math.cos(angle)
    vy = velocity * math.sin(angle)
    projectile = "projectile" in schema or abs(angle) > 1e-9
    y0 = parameters.get("y0", parameters.get("initial_height", parameters.get("height", 0.0)))

    pos_x = [vx * time for time in times]
    pos_y = [max(0.0, y0 + vy * time - 0.5 * gravity * time * time) if projectile else 0.0 for time in times]
    positions = [{"x": px, "y": py} for px, py in zip(pos_x, pos_y)]
    velocities = [{"x": vx + acceleration * time, "y": vy - gravity * time if projectile else 0.0} for time in times]
    accelerations = [{"x": acceleration, "y": -gravity if projectile else 0.0} for _ in times]
    values = {
        "x": pos_x,
        "y": pos_y,
        "speed": [math.sqrt(item["x"] ** 2 + item["y"] ** 2) for item in velocities],
        "distance": pos_x,
    }
    return {"time": times, "positions": positions, "velocities": velocities, "accelerations": accelerations, "values": values, "parameters": parameters}
