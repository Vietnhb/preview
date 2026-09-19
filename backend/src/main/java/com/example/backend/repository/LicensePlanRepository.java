package com.example.backend.repository;

import com.example.backend.entity.LicensePlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LicensePlanRepository extends JpaRepository<LicensePlan, String> {
    List<LicensePlan> findByActiveTrueOrderByAnnualPriceVndAsc();
}
