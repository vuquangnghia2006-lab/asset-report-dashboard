package com.atdigital.report.report;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    private FilterContext ctx(Long orgId, String city, String ward, String voltageLevel,
                              Long manufacturerId, String assetClass, Integer status) {
        return new FilterContext(orgId, city, ward, voltageLevel, manufacturerId, assetClass, status);
    }

    @GetMapping("/filters")
    public Dtos.FilterOptions filters() {
        return service.filterOptions();
    }

    @GetMapping("/asset-overview")
    public Dtos.AssetOverviewReport assetOverview(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) Integer status) {
        return service.assetOverview(ctx(orgId, city, ward, voltageLevel, manufacturerId, assetClass, status));
    }

    @GetMapping("/transformer-fleet")
    public Dtos.TransformerFleetReport transformerFleet(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) Integer status) {
        return service.transformerFleet(ctx(orgId, city, ward, voltageLevel, manufacturerId, null, status));
    }

    @GetMapping("/transformer-fleet/parallel-pairs")
    public Dtos.ParallelPairsReport parallelPairs(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "10") double maxUkDiff,
            @RequestParam(defaultValue = "true") boolean sameSite,
            @RequestParam(defaultValue = "true") boolean requireTapOverlap,
            @RequestParam(defaultValue = "0") double maxPowerRatio) {
        return service.parallelPairs(ctx(orgId, city, ward, voltageLevel, manufacturerId, null, status),
                maxUkDiff, sameSite, requireTapOverlap, maxPowerRatio);
    }

    @GetMapping("/data-quality")
    public Dtos.DataQualityReport dataQuality(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String ward,
            @RequestParam(required = false) String voltageLevel,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) String assetClass,
            @RequestParam(required = false) Integer status) {
        return service.dataQuality(ctx(orgId, city, ward, voltageLevel, manufacturerId, assetClass, status));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}
