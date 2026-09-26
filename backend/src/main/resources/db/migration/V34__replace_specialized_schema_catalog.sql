-- New topic capability packs are registered by the versioned catalog bootstrap.
-- Keep old pack and solver rows for specifications that pinned those versions;
-- retirement removes them from routing while preserving replay and audit data.
UPDATE solver_versions SET lifecycle_status = 'RETIRED'
WHERE schema_id NOT IN (
    'adaptive_circuits',
    'adaptive_dynamics',
    'adaptive_electromagnetism',
    'adaptive_kinematics',
    'adaptive_modern_physics',
    'adaptive_optics',
    'adaptive_practical_and_data',
    'adaptive_thermal',
    'adaptive_waves'
);

UPDATE schema_versions SET lifecycle_status = 'RETIRED'
WHERE schema_id NOT IN (
    'adaptive_circuits',
    'adaptive_dynamics',
    'adaptive_electromagnetism',
    'adaptive_kinematics',
    'adaptive_modern_physics',
    'adaptive_optics',
    'adaptive_practical_and_data',
    'adaptive_thermal',
    'adaptive_waves'
)
AND lifecycle_status <> 'RETIRED';
