-- The post-extraction ambiguity flow is retired: an unclear problem is now clarified with the
-- teacher inside the simulation request, so nothing writes these tables any more.
--   reviewer_decisions : answers reviewers gave in the old question queue
--   ambiguity_cases    : the old question queue itself
--   source_assets      : uploaded problem images of the old extraction flow (images are now sent
--                        straight to the AI provider and not stored)
-- The rows they still hold are leftovers of that flow and are removed with the tables.
DROP TABLE IF EXISTS reviewer_decisions;
DROP TABLE IF EXISTS ambiguity_cases;
DROP TABLE IF EXISTS source_assets;
