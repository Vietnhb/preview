from app.solver import solve

def test_kinematics_closed_form():
    result = solve("kinematics_projectile", {"velocity": 10, "angle": 0}, 1, 0.5)
    assert result["positions"][-1]["x"] == 10

def test_circuit_rc_values():
    result = solve("circuits_rc_charging", {"voltage": 12, "resistance": 6, "capacitance": 1}, 1, 0.5)
    assert result["values"]["voltage"][0] == 0
