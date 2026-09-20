package com.example.backend.physics.model;

/** SI constants shared by physics models; schema defaults remain authoritative. */
public final class PhysicalConstants {
    /** Project's existing schema default; explicit schema values remain authoritative. */
    public static final double STANDARD_GRAVITY = 9.81;
    public static final double STANDARD_ATMOSPHERIC_PRESSURE = 101_325.0;
    public static final double SPEED_OF_LIGHT = 299_792_458.0;
    public static final double PLANCK = 6.62607015e-34;
    public static final double ELEMENTARY_CHARGE = 1.602176634e-19;
    public static final double BOLTZMANN = 1.380649e-23;
    public static final double GRAVITATIONAL_CONSTANT = 6.67430e-11;

    private PhysicalConstants() {
    }
}
