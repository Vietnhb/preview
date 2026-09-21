package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Matter-wave wavelength and first-order electron diffraction. */
public record DeBroglieDiffractionParameters(double particleMass, double speed,
                                              double latticeSpacing, int diffractionOrder) {
    public static DeBroglieDiffractionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double mass = PhysicsValues.require(specification, overrides, "particle_mass");
        double speed = PhysicsValues.require(specification, overrides, "particle_speed");
        double spacing = PhysicsValues.require(specification, overrides, "lattice_spacing");
        double order = PhysicsValues.require(specification, overrides, "diffraction_order");
        if (!(mass > 0) || !(speed > 0) || !(spacing > 0) || order < 1
                || order != Math.rint(order) || speed >= 299_792_458) {
            throw new IllegalArgumentException("Matter-wave parameters are invalid or relativistic");
        }
        return new DeBroglieDiffractionParameters(mass, speed, spacing, (int) order);
    }
    public double momentum() { return particleMass * speed; }
    public double wavelength() { return PhysicalConstants.PLANCK / momentum(); }
    public double kineticEnergy() { return 0.5 * particleMass * speed * speed; }
    public double sineDiffractionAngle() { return diffractionOrder * wavelength() / latticeSpacing; }
    public double diffractionAngle() {
        double sine = sineDiffractionAngle();
        // An order with nλ/d > 1 has no propagating Bragg solution.  Keep the
        // transport payload finite and expose the physical state through the
        // diffractionAllowed flag instead of serializing NaN to the client.
        if (sine > 1) return 0;
        return Math.asin(sine);
    }
    public boolean diffractionAllowed() { return sineDiffractionAngle() <= 1; }
}
