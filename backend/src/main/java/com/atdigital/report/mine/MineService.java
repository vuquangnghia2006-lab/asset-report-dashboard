package com.atdigital.report.mine;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class MineService {

    private final JdbcTemplate jdbc;

    public MineService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public boolean tablesExist() {
        try {
            Long count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'mine'",
                    Long.class);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public MineDtos.MineOverviewReport mineOverview() {
        if (!tablesExist()) {
            return demoOverview();
        }

        long totalMines = jdbc.queryForObject("SELECT count(*) FROM mine", Long.class);
        long activeMines = jdbc.queryForObject(
                "SELECT count(*) FROM mine WHERE status = 'ACTIVE'", Long.class);
        long totalWorkers = jdbc.queryForObject(
                "SELECT COALESCE(SUM(worker_count), 0) FROM mine WHERE status = 'ACTIVE'", Long.class);
        long totalEquipment = jdbc.queryForObject(
                "SELECT count(*) FROM mine_equipment WHERE status != 'RETIRED'", Long.class);

        Double totalProd = jdbc.queryForObject(
                "SELECT COALESCE(SUM(extracted_tonnes), 0) FROM mine_production " +
                        "WHERE production_date >= date_trunc('month', CURRENT_DATE)", Double.class);

        Long incidents = jdbc.queryForObject(
                "SELECT count(*) FROM mine_safety_incident " +
                        "WHERE incident_date >= date_trunc('month', CURRENT_DATE)", Long.class);

        List<MineDtos.NameValue> prodByMonth = jdbc.query(
                "SELECT to_char(production_date, 'YYYY-MM') AS label, SUM(extracted_tonnes) AS value " +
                        "FROM mine_production WHERE production_date >= CURRENT_DATE - INTERVAL '12 months' " +
                        "GROUP BY label ORDER BY label",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), round1(rs.getDouble("value"))));

        List<MineDtos.NameValue> prodByType = jdbc.query(
                "SELECT material_type AS label, SUM(extracted_tonnes) AS value FROM mine_production " +
                        "WHERE production_date >= CURRENT_DATE - INTERVAL '12 months' " +
                        "GROUP BY material_type ORDER BY value DESC",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), round1(rs.getDouble("value"))));

        List<MineDtos.NameValue> minesByStatus = jdbc.query(
                "SELECT status AS label, count(*) AS value FROM mine GROUP BY status",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), rs.getDouble("value")));

        List<MineDtos.MineSummary> topMines = jdbc.query(
                "SELECT m.id, m.name, m.location, m.type, m.status, m.worker_count, " +
                        "COALESCE(SUM(p.extracted_tonnes), 0) AS prod " +
                        "FROM mine m LEFT JOIN mine_production p ON p.mine_id = m.id " +
                        "AND p.production_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "GROUP BY m.id ORDER BY prod DESC LIMIT 5",
                (rs, i) -> new MineDtos.MineSummary(
                        rs.getLong("id"), rs.getString("name"), rs.getString("location"),
                        rs.getString("type"), rs.getString("status"),
                        rs.getLong("worker_count"), round1(rs.getDouble("prod")),
                        new MineDtos.DateRange("", "")));

        return new MineDtos.MineOverviewReport(
                new MineDtos.OverviewCards(totalMines, activeMines, totalWorkers, totalEquipment,
                        totalProd == null ? 0 : round1(totalProd), incidents == null ? 0 : incidents),
                prodByMonth, prodByType, minesByStatus, topMines);
    }

    public MineDtos.EquipmentReport equipmentReport() {
        if (!tablesExist()) {
            return demoEquipment();
        }

        long total = jdbc.queryForObject("SELECT count(*) FROM mine_equipment", Long.class);
        long operational = jdbc.queryForObject(
                "SELECT count(*) FROM mine_equipment WHERE status = 'OPERATIONAL'", Long.class);
        long maintenance = jdbc.queryForObject(
                "SELECT count(*) FROM mine_equipment WHERE status = 'MAINTENANCE'", Long.class);
        long idle = jdbc.queryForObject(
                "SELECT count(*) FROM mine_equipment WHERE status = 'IDLE'", Long.class);

        List<MineDtos.NameValue> byType = jdbc.query(
                "SELECT type AS label, count(*) AS value FROM mine_equipment GROUP BY type ORDER BY value DESC",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), rs.getDouble("value")));

        List<MineDtos.NameValue> utilByType = jdbc.query(
                "SELECT type AS label, AVG(utilization_pct) AS value FROM mine_equipment " +
                        "WHERE status = 'OPERATIONAL' GROUP BY type",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), round1(rs.getDouble("value"))));

        List<MineDtos.EquipmentItem> items = jdbc.query(
                "SELECT e.id, e.name, e.type, m.name AS mine_name, e.status, " +
                        "e.utilization_pct, e.operating_hours, e.last_maintenance::text " +
                        "FROM mine_equipment e JOIN mine m ON m.id = e.mine_id ORDER BY e.name",
                (rs, i) -> new MineDtos.EquipmentItem(
                        rs.getLong("id"), rs.getString("name"), rs.getString("type"),
                        rs.getString("mine_name"), rs.getString("status"),
                        round1(rs.getDouble("utilization_pct")),
                        rs.getLong("operating_hours"),
                        rs.getString("last_maintenance")));

        return new MineDtos.EquipmentReport(total, operational, maintenance, idle, byType, utilByType, items);
    }

    public MineDtos.ProductionReport productionReport() {
        if (!tablesExist()) {
            return demoProduction();
        }

        Double extracted = jdbc.queryForObject(
                "SELECT COALESCE(SUM(extracted_tonnes), 0) FROM mine_production " +
                        "WHERE production_date >= CURRENT_DATE - INTERVAL '30 days'", Double.class);
        Double processed = jdbc.queryForObject(
                "SELECT COALESCE(SUM(processed_tonnes), 0) FROM mine_production " +
                        "WHERE production_date >= CURRENT_DATE - INTERVAL '30 days'", Double.class);
        Double shipped = jdbc.queryForObject(
                "SELECT COALESCE(SUM(shipped_tonnes), 0) FROM mine_production " +
                        "WHERE production_date >= CURRENT_DATE - INTERVAL '30 days'", Double.class);

        double efficiency = (extracted == null || extracted == 0) ? 0 :
                round1(100.0 * (processed == null ? 0 : processed) / extracted);

        List<MineDtos.NameValue> daily = jdbc.query(
                "SELECT to_char(production_date, 'MM-DD') AS label, SUM(extracted_tonnes) AS value " +
                        "FROM mine_production WHERE production_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "GROUP BY production_date ORDER BY production_date",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), round1(rs.getDouble("value"))));

        List<MineDtos.NameValue> byMaterial = jdbc.query(
                "SELECT material_type AS label, SUM(extracted_tonnes) AS value FROM mine_production " +
                        "WHERE production_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "GROUP BY material_type ORDER BY value DESC",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), round1(rs.getDouble("value"))));

        List<MineDtos.NameValue> byMine = jdbc.query(
                "SELECT m.name AS label, SUM(p.extracted_tonnes) AS value FROM mine_production p " +
                        "JOIN mine m ON m.id = p.mine_id WHERE p.production_date >= CURRENT_DATE - INTERVAL '30 days' " +
                        "GROUP BY m.name ORDER BY value DESC",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), round1(rs.getDouble("value"))));

        List<MineDtos.ProductionRecord> records = jdbc.query(
                "SELECT p.id, p.production_date::text AS date, m.name AS mine_name, p.material_type, " +
                        "p.extracted_tonnes, p.processed_tonnes, p.shipped_tonnes, p.shift " +
                        "FROM mine_production p JOIN mine m ON m.id = p.mine_id " +
                        "ORDER BY p.production_date DESC LIMIT 20",
                (rs, i) -> new MineDtos.ProductionRecord(
                        rs.getLong("id"), rs.getString("date"), rs.getString("mine_name"),
                        rs.getString("material_type"), round1(rs.getDouble("extracted_tonnes")),
                        round1(rs.getDouble("processed_tonnes")), round1(rs.getDouble("shipped_tonnes")),
                        rs.getString("shift")));

        return new MineDtos.ProductionReport(
                round1(extracted == null ? 0 : extracted),
                round1(processed == null ? 0 : processed),
                round1(shipped == null ? 0 : shipped),
                efficiency, daily, byMaterial, byMine, records);
    }

    public MineDtos.SafetyReport safetyReport() {
        if (!tablesExist()) {
            return demoSafety();
        }

        long total = jdbc.queryForObject("SELECT count(*) FROM mine_safety_incident", Long.class);
        long critical = jdbc.queryForObject(
                "SELECT count(*) FROM mine_safety_incident WHERE severity = 'CRITICAL'", Long.class);
        long major = jdbc.queryForObject(
                "SELECT count(*) FROM mine_safety_incident WHERE severity = 'MAJOR'", Long.class);
        long minor = jdbc.queryForObject(
                "SELECT count(*) FROM mine_safety_incident WHERE severity = 'MINOR'", Long.class);
        long resolved = jdbc.queryForObject(
                "SELECT count(*) FROM mine_safety_incident WHERE status = 'RESOLVED'", Long.class);

        double resRate = total == 0 ? 0 : round1(100.0 * resolved / total);

        List<MineDtos.NameValue> byMonth = jdbc.query(
                "SELECT to_char(incident_date, 'YYYY-MM') AS label, count(*) AS value " +
                        "FROM mine_safety_incident WHERE incident_date >= CURRENT_DATE - INTERVAL '12 months' " +
                        "GROUP BY label ORDER BY label",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), rs.getDouble("value")));

        List<MineDtos.NameValue> bySeverity = jdbc.query(
                "SELECT severity AS label, count(*) AS value FROM mine_safety_incident GROUP BY severity",
                (rs, i) -> new MineDtos.NameValue(rs.getString("label"), rs.getDouble("value")));

        List<MineDtos.SafetyIncident> incidents = jdbc.query(
                "SELECT si.id, si.incident_date::text AS date, m.name AS mine_name, " +
                        "si.severity, si.description, si.status, si.reported_by " +
                        "FROM mine_safety_incident si JOIN mine m ON m.id = si.mine_id " +
                        "ORDER BY si.incident_date DESC LIMIT 20",
                (rs, i) -> new MineDtos.SafetyIncident(
                        rs.getLong("id"), rs.getString("date"), rs.getString("mine_name"),
                        rs.getString("severity"), rs.getString("description"),
                        rs.getString("status"), rs.getString("reported_by")));

        return new MineDtos.SafetyReport(total, critical, major, minor, resolved, resRate,
                byMonth, bySeverity, incidents);
    }

    private String fmtDate(LocalDate d) {
        return d == null ? "" : d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    // ----------------------------------------------------------------
    // Demo data when mining tables don't exist in the database
    // ----------------------------------------------------------------

    private MineDtos.MineOverviewReport demoOverview() {
        List<MineDtos.NameValue> prodByMonth = List.of(
                new MineDtos.NameValue("2026-01", 12500),
                new MineDtos.NameValue("2026-02", 11800),
                new MineDtos.NameValue("2026-03", 13200),
                new MineDtos.NameValue("2026-04", 14100),
                new MineDtos.NameValue("2026-05", 13600),
                new MineDtos.NameValue("2026-06", 15200),
                new MineDtos.NameValue("2026-07", 14800),
                new MineDtos.NameValue("2026-08", 16100));

        List<MineDtos.NameValue> prodByType = List.of(
                new MineDtos.NameValue("Da granite", 45200),
                new MineDtos.NameValue("Da pha le", 28600),
                new MineDtos.NameValue("Cat xay dung", 18900),
                new MineDtos.NameValue("Da lit", 12400),
                new MineDtos.NameValue("Soi", 6800));

        List<MineDtos.NameValue> minesByStatus = List.of(
                new MineDtos.NameValue("ACTIVE", 8),
                new MineDtos.NameValue("MAINTENANCE", 2),
                new MineDtos.NameValue("INACTIVE", 3));

        List<MineDtos.MineSummary> topMines = List.of(
                new MineDtos.MineSummary(1, "Mo Da Nang - Quang Nam", "Quang Nam", "Da granite", "ACTIVE", 156, 4200, new MineDtos.DateRange("2019-03-15", "")),
                new MineDtos.MineSummary(2, "Mo Pha Le Binh Dinh", "Binh Dinh", "Da pha le", "ACTIVE", 98, 3100, new MineDtos.DateRange("2020-06-01", "")),
                new MineDtos.MineSummary(3, "Mo Cat Da Nang", "Da Nang", "Cat xay dung", "ACTIVE", 67, 2800, new MineDtos.DateRange("2021-01-10", "")),
                new MineDtos.MineSummary(4, "Mo Da Lit Quang Tri", "Quang Tri", "Da lit", "ACTIVE", 45, 2200, new MineDtos.DateRange("2022-05-20", "")),
                new MineDtos.MineSummary(5, "Mo Soi Nghe An", "Nghe An", "Soi", "MAINTENANCE", 32, 1800, new MineDtos.DateRange("2023-02-14", "")));

        return new MineDtos.MineOverviewReport(
                new MineDtos.OverviewCards(13, 8, 812, 67, 16100, 5),
                prodByMonth, prodByType, minesByStatus, topMines);
    }

    private MineDtos.EquipmentReport demoEquipment() {
        List<MineDtos.NameValue> byType = List.of(
                new MineDtos.NameValue("May cuốc", 18),
                new MineDtos.NameValue("Xe tai", 22),
                new MineDtos.NameValue("May đap", 8),
                new MineDtos.NameValue("May phay", 6),
                new MineDtos.NameValue("May nghien", 5),
                new MineDtos.NameValue("Truc bom", 8));

        List<MineDtos.NameValue> utilByType = List.of(
                new MineDtos.NameValue("May cuốc", 78),
                new MineDtos.NameValue("Xe tai", 85),
                new MineDtos.NameValue("May đap", 62),
                new MineDtos.NameValue("May phay", 71),
                new MineDtos.NameValue("May nghien", 89),
                new MineDtos.NameValue("Truc bom", 74));

        List<MineDtos.EquipmentItem> items = List.of(
                new MineDtos.EquipmentItem(1, "EX-3200", "May cuốc", "Mo Da Nang", "OPERATIONAL", 82, 12400, "2026-07-15"),
                new MineDtos.EquipmentItem(2, "CAT-785", "Xe tai", "Mo Da Nang", "OPERATIONAL", 91, 15600, "2026-08-01"),
                new MineDtos.EquipmentItem(3, "PC-2000", "May cuốc", "Mo Pha Le", "MAINTENANCE", 0, 9800, "2026-06-20"),
                new MineDtos.EquipmentItem(4, "Komatsu-D61", "May dap", "Mo Cat Da Nang", "OPERATIONAL", 68, 8200, "2026-07-28"),
                new MineDtos.EquipmentItem(5, "HP-500", "May nghien", "Mo Da Nang", "OPERATIONAL", 92, 11200, "2026-08-05"),
                new MineDtos.EquipmentItem(6, "Liebherr-LTM", "Truc bom", "Mo Da Lit", "IDLE", 0, 5400, "2026-05-10"),
                new MineDtos.EquipmentItem(7, "CAT-D10", "May dap", "Mo Pha Le", "OPERATIONAL", 75, 7600, "2026-07-22"),
                new MineDtos.EquipmentItem(8, "VOLVO-A40G", "Xe tai", "Mo Soi", "OPERATIONAL", 88, 13100, "2026-08-03"));

        return new MineDtos.EquipmentReport(67, 48, 12, 7, byType, utilByType, items);
    }

    private MineDtos.ProductionReport demoProduction() {
        List<MineDtos.NameValue> daily = new ArrayList<>();
        for (int i = 30; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            double val = 400 + Math.random() * 300;
            daily.add(new MineDtos.NameValue(
                    d.format(DateTimeFormatter.ofPattern("MM-dd")),
                    round1(val)));
        }

        List<MineDtos.NameValue> byMaterial = List.of(
                new MineDtos.NameValue("Da granite", 5800),
                new MineDtos.NameValue("Da pha le", 3400),
                new MineDtos.NameValue("Cat xay dung", 2900),
                new MineDtos.NameValue("Da lit", 2100),
                new MineDtos.NameValue("Soi", 1200));

        List<MineDtos.NameValue> byMine = List.of(
                new MineDtos.NameValue("Mo Da Nang", 4200),
                new MineDtos.NameValue("Mo Pha Le", 3100),
                new MineDtos.NameValue("Mo Cat Da Nang", 2800),
                new MineDtos.NameValue("Mo Da Lit", 2200),
                new MineDtos.NameValue("Mo Soi", 1800));

        List<MineDtos.ProductionRecord> records = List.of(
                new MineDtos.ProductionRecord(1, "2026-08-17", "Mo Da Nang", "Da granite", 520, 480, 450, "Ca sang"),
                new MineDtos.ProductionRecord(2, "2026-08-17", "Mo Pha Le", "Da pha le", 380, 350, 320, "Ca sang"),
                new MineDtos.ProductionRecord(3, "2026-08-16", "Mo Cat Da Nang", "Cat xay dung", 290, 270, 260, "Ca chieu"),
                new MineDtos.ProductionRecord(4, "2026-08-16", "Mo Da Lit", "Da lit", 220, 200, 180, "Ca sang"),
                new MineDtos.ProductionRecord(5, "2026-08-15", "Mo Soi", "Soi", 180, 170, 150, "Ca dem"));

        return new MineDtos.ProductionReport(15400, 14100, 12800, 91.6,
                daily, byMaterial, byMine, records);
    }

    private MineDtos.SafetyReport demoSafety() {
        List<MineDtos.NameValue> byMonth = List.of(
                new MineDtos.NameValue("2026-01", 2),
                new MineDtos.NameValue("2026-02", 1),
                new MineDtos.NameValue("2026-03", 3),
                new MineDtos.NameValue("2026-04", 1),
                new MineDtos.NameValue("2026-05", 2),
                new MineDtos.NameValue("2026-06", 0),
                new MineDtos.NameValue("2026-07", 4),
                new MineDtos.NameValue("2026-08", 2));

        List<MineDtos.NameValue> bySeverity = List.of(
                new MineDtos.NameValue("CRITICAL", 1),
                new MineDtos.NameValue("MAJOR", 4),
                new MineDtos.NameValue("MINOR", 10));

        List<MineDtos.SafetyIncident> incidents = List.of(
                new MineDtos.SafetyIncident(1, "2026-08-10", "Mo Da Nang", "MAJOR", "Roi da tu do cao, khong co thuong vong", "RESOLVED", "Nguyen Van A"),
                new MineDtos.SafetyIncident(2, "2026-08-05", "Mo Pha Le", "MINOR", "Hong he thong bao ho lam viec", "RESOLVED", "Tran Thi B"),
                new MineDtos.SafetyIncident(3, "2026-07-28", "Mo Cat Da Nang", "CRITICAL", "Ha sap o khu vuc khai thac A3", "IN_PROGRESS", "Le Van C"),
                new MineDtos.SafetyIncident(4, "2026-07-20", "Mo Da Lit", "MINOR", "Tieeng on vuot qua cho phep", "RESOLVED", "Pham Thi D"));

        return new MineDtos.SafetyReport(15, 1, 4, 10, 12, 80.0,
                byMonth, bySeverity, incidents);
    }
}
