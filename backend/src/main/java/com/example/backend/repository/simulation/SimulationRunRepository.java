package com.example.backend.repository.simulation;

import com.example.backend.entity.simulation.SimulationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {
    Optional<SimulationRun> findFirstBySimulationIdOrderByCreatedAtDesc(UUID simulationId);
    Optional<SimulationRun> findFirstBySimulationIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            UUID simulationId, Instant timestamp);
    long countByValidationPassedFalse();

    interface LatestRunId {
        UUID getSimulationId();
        UUID getRunId();
    }

    @Query("""
            select r.simulation.id as simulationId, r.id as runId
            from SimulationRun r
            where r.simulation.id in :simulationIds
              and r.createdAt = (select max(latest.createdAt)
                                 from SimulationRun latest
                                 where latest.simulation.id = r.simulation.id)
            """)
    List<LatestRunId> findLatestRunIds(@Param("simulationIds") List<UUID> simulationIds);
}
