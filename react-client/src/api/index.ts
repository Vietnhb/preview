/**
 * Barrel export for all API modules
 * Provides backward compatibility and centralized API access
 */

// Export axios client
export { default as axiosClient } from "./axios";

// Export auth API
export * from "./authApi";

// Export user API
export * from "./userApi";

// Export problem API
export * from "./problemApi";

// Export simulation API
export * from "./simulationApi";

// Export library API
export * from "./libraryApi";

// Export assignment API
export * from "./assignmentApi";

// Export curriculum API
export * from "./curriculumApi";

// Export admin API
export * from "./adminApi";

// Export reviewer API
export * from "./reviewerApi";

/**
 * Legacy compatibility: Re-export everything from physliveApi
 * This ensures existing imports from 'api/physliveApi' continue to work
 */
export * from "./problemApi";
export * from "./simulationApi";
export * from "./libraryApi";
export * from "./assignmentApi";
export * from "./curriculumApi";
export * from "./adminApi";
