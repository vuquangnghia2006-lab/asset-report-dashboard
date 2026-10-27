package com.atdigital.report.mine;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mine")
public class MineController {

    private final MineService service;

    public MineController(MineService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public MineDtos.MineOverviewReport overview() {
        return service.mineOverview();
    }

    @GetMapping("/equipment")
    public MineDtos.EquipmentReport equipment() {
        return service.equipmentReport();
    }

    @GetMapping("/production")
    public MineDtos.ProductionReport production() {
        return service.productionReport();
    }

    @GetMapping("/safety")
    public MineDtos.SafetyReport safety() {
        return service.safetyReport();
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
