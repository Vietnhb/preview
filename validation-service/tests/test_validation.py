from app.main import validate

def test_validation_passes_with_identical_series():
    result = validate({"x": [0, 1]}, {"x": [0, 1]}, 1e-3)
    assert result["passed"] is True

def test_validation_detects_mismatch():
    result = validate({"x": [0, 1]}, {"x": [0, 2]}, 1e-3)
    assert result["passed"] is False
