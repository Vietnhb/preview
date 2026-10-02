-- One physics catalogue only: remove every topic pack and solver binding that does not belong to
-- the current curriculum-aligned library (the 21 "thpt_*" topics compiled from resources/schemas/topics).
-- This deletes the superseded "adaptive_*" packs, the earlier specialised schemas and the retired
-- optics pack, which the reviewer screen kept listing under "Ngừng dùng".
-- Saved simulations keep their own stored result and stay openable; they only reference these
-- rows by name, so an export of an old simulation no longer embeds the removed topic definition.
-- Versions and drafts of the current topics, whatever their status, are kept.

CREATE TEMP TABLE schema_keep (schema_id text PRIMARY KEY);
INSERT INTO schema_keep (schema_id) VALUES
    ('thpt_kinematics'),
    ('thpt_dynamics'),
    ('thpt_work_energy_power'),
    ('thpt_momentum'),
    ('thpt_circular_motion'),
    ('thpt_solid_deformation'),
    ('thpt_earth_and_sky'),
    ('thpt_gravitational_field'),
    ('thpt_oscillations'),
    ('thpt_waves'),
    ('thpt_radio_communication'),
    ('thpt_electric_field'),
    ('thpt_electric_current'),
    ('thpt_electronics'),
    ('thpt_thermal_physics'),
    ('thpt_ideal_gas'),
    ('thpt_magnetic_field'),
    ('thpt_alternating_current'),
    ('thpt_nuclear_physics'),
    ('thpt_medical_physics'),
    ('thpt_quantum_physics');

DELETE FROM solver_versions s WHERE NOT EXISTS (SELECT 1 FROM schema_keep k WHERE lower(k.schema_id) = lower(s.schema_id));
DELETE FROM schema_versions s WHERE NOT EXISTS (SELECT 1 FROM schema_keep k WHERE lower(k.schema_id) = lower(s.schema_id));

DROP TABLE schema_keep;
