package com.atdigital.report.report;

public record FilterContext(
        Long orgId,
        String city,
        String ward,
        String voltageLevel,
        Long manufacturerId,
        String assetClass,
        Integer status
) {
}
