-- Jev performs schema classification directly against the approved catalog.
-- Keep V8/V10/V15 in Flyway history, but remove their derived storage safely.
DROP TABLE IF EXISTS schema_search_embedding_views;
DROP TABLE IF EXISTS schema_search_index_generations;
DROP TABLE IF EXISTS schema_search_embeddings;
