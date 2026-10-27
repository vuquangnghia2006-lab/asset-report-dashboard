package com.atdigital.report.report;

import java.util.List;

public final class Dtos {

    private Dtos() {
    }

    public record Option(String value, String label, long count) {
    }

    public record FilterOptions(
            List<Option> organisations,
            List<Option> cities,
            List<Option> wards,
            List<Option> voltageLevels,
            List<Option> manufacturers,
            List<Option> statuses,
            List<Option> assetClasses
    ) {
    }

    public record OverviewCards(long totalAssets, long totalSubstations, long totalTransformers,
                                double transformerCapacityMva) {
    }

    public record ManufacturerStat(String name, long count, double pct) {
    }

    public record MissingStat(String field, long missing, long total, double missingPct) {
    }

    public record NameValue(String label, double value) {
    }

    public record OrgMix(long id, String label, double value) {
    }

    public record AssetOverviewReport(
            OverviewCards cards,
            List<NameValue> capacityByVoltage,
            List<OrgMix> assetMixByOrg,
            List<ManufacturerStat> topManufacturers,
            List<MissingStat> dataCompleteness
    ) {
    }

    public record FleetCards(long totalTransformers, double totalRatedCapacityMva, double nameplatePct,
                             Double avgFleetAge) {
    }

    public record TransformerFleetReport(
            FleetCards cards,
            List<NameValue> capacityByVoltage,
            List<ManufacturerStat> topManufacturers,
            List<NameValue> ageDistribution
    ) {
    }

    public record PairResult(long id, String nameA, String nameB, String siteA, String siteB, String ratio,
                             String vectorGroup, String tapInfo, Double ukDiff, String result, String detail,
                             Boolean ratioOk, Boolean vectorOk, Boolean tapOk, Boolean ukOk) {
    }

    public record ParallelPairsReport(long totalPairs, long compatible, long review, long notCompatible,
                                      double maxUkDiff, boolean sameSite, List<PairResult> pairs) {
    }

    public record QualityCards(double overallCompletenessPct, long assetsWithMissingFields, long missingNameplates,
                               long phaseMonitoredAssets, double balancedGroupsPct, long phaseMismatches) {
    }

    public record PhaseBalanceRow(String assetClass, long phaseA, long phaseB, long phaseC, String status) {
    }

    public record MissingKeyField(String field, String category, String assetClass, long missing, long total,
                                  double missingPct) {
    }

    public record DataQualityReport(
            QualityCards cards,
            List<NameValue> completenessByCategory,
            List<NameValue> statusDistribution,
            List<PhaseBalanceRow> phaseBalance,
            List<MissingKeyField> missingKeyFields
    ) {
    }
}
