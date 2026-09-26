-- Retire the non-curriculum adaptive optics route while keeping pinned versions replayable.
UPDATE solver_versions SET lifecycle_status = 'RETIRED' WHERE schema_id = 'adaptive_optics';
UPDATE schema_versions SET lifecycle_status = 'RETIRED' WHERE schema_id = 'adaptive_optics';

-- Retire optics curriculum nodes without deleting lessons referenced by saved work.
UPDATE topics SET enabled = false WHERE lower(slug) = 'optics';
UPDATE content_modules SET active = false WHERE topic_id IN (SELECT id FROM topics WHERE lower(slug) = 'optics');
UPDATE grade_levels SET active = false WHERE module_id IN (SELECT m.id FROM content_modules m JOIN topics t ON t.id = m.topic_id WHERE lower(t.slug) = 'optics');
UPDATE lessons SET active = false WHERE level_id IN (SELECT gl.id FROM grade_levels gl JOIN content_modules m ON m.id = gl.module_id JOIN topics t ON t.id = m.topic_id WHERE lower(t.slug) = 'optics');
