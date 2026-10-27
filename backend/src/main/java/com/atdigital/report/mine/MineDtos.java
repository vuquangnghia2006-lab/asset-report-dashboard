package com.atdigital.report.mine;

import java.util.List;

public final class MineDtos {

    private MineDtos() {
    }

    public record OverviewCards(
            long totalMines,
            long activeMines,
            long totalWorkers,
            long totalEquipment,
            double totalProductionTonnes,
            double safetyIncidents
    ) {
    }

    public record MineSummary(
            long id,
            String name,
            String location,
            String type,
            String status,
            long workers,
            double productionTonnes,
            DateRange operationPeriod
    ) {
    }

    public record DateRange(String from, String to) {
    }

    public record MineOverviewReport(
            OverviewCards cards,
            List<NameValue> productionByMonth,
            List<NameValue> productionByType,
            List<NameValue> minesByStatus,
            List<MineSummary> topMines
    ) {
    }

    public record NameValue(String label, double value) {
    }

    public record EquipmentItem(
            long id,
            String name,
            String type,
            String mineName,
            String status,
            double utilizationPct,
            long operatingHours,
            String lastMaintenance
    ) {
    }

    public record EquipmentReport(
            long totalEquipment,
            long operationalCount,
            long maintenanceCount,
            long idleCount,
            List<NameValue> equipmentByType,
            List<NameValue> utilizationByType,
            List<EquipmentItem> equipmentList
    ) {
    }

    public record ProductionRecord(
            long id,
            String date,
            String mineName,
            String materialType,
            double extractedTonnes,
            double processedTonnes,
            double shippedTonnes,
            String shift
    ) {
    }

    public record ProductionReport(
            double totalExtracted,
            double totalProcessed,
            double totalShipped,
            double processingEfficiency,
            List<NameValue> dailyProduction,
            List<NameValue> productionByMaterial,
            List<NameValue> productionByMine,
            List<ProductionRecord> recentRecords
    ) {
    }

    public record SafetyIncident(
            long id,
            String date,
            String mineName,
            String severity,
            String description,
            String status,
            String reportedBy
    ) {
    }

    public record SafetyReport(
            long totalIncidents,
            long criticalCount,
            long majorCount,
            long minorCount,
            long resolvedCount,
            double resolutionRate,
            List<NameValue> incidentsByMonth,
            List<NameValue> incidentsBySeverity,
            List<SafetyIncident> recentIncidents
    ) {
    }
}
