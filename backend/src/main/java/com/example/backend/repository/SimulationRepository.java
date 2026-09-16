package com.example.backend.repository;

import com.example.backend.entity.Simulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRepository extends JpaRepository<Simulation, UUID> {
    List<Simulation> findByOwnerIdOrderByCreatedAtDesc(Integer ownerId);
    Optional<Simulation> findByIdAndOwnerId(UUID id, Integer ownerId);

    interface Summary {
        UUID getSimulationId();
        UUID getSpecificationId();
        String getSchemaId();
        String getStatus();
        java.time.Instant getCreatedAt();
    }

    @Query("""
            select s.id as simulationId, s.specification.id as specificationId,
                   s.schemaId as schemaId, s.status as status, s.createdAt as createdAt
            from Simulation s
            where s.owner.id = :ownerId
            order by s.createdAt desc
            """)
    List<Summary> findSummariesByOwnerId(@Param("ownerId") Integer ownerId);
}
