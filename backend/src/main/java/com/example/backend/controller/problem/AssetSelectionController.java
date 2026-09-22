package com.example.backend.controller.problem;

import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.simulation.assets.AssetSelectionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/specifications")
@RequiredArgsConstructor
public class AssetSelectionController {
    private final AssetSelectionService selections;

    @PostMapping("/{id}/assets/decision")
    public SpecificationResponse decide(@PathVariable UUID id, @RequestBody AssetSelectionService.Decision decision) {
        return selections.decide(id, decision);
    }
}
