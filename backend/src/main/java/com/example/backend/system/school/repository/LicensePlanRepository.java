package com.example.backend.system.school.repository;

import com.example.backend.system.school.model.entity.LicensePlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicensePlanRepository extends JpaRepository<LicensePlan, String> {
    List<LicensePlan> findByActiveTrueOrderByAnnualPriceVndAsc();
}
