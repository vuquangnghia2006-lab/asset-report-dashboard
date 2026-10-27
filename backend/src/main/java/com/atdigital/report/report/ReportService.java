package com.atdigital.report.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Computes the three report dashboards on top of the tester PostgreSQL schema.
 *
 * Asset hierarchy (tester schema):
 *   asset_info (owner_id = bay.id, owner_type = 'BAY')
 *     <- bay (owner_id = voltage_level.id, owner_type = 'VOLTAGE_LEVEL')
 *     <- voltage_level (owner_id = substation.id)
 *     <- substation (owner_id = organisation.id)
 * Some assets point directly to a substation (owner_type = 'SUBSTATION').
 */
@Service
public class ReportService {

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Joins that enrich asset_info with bay / voltage level / substation / organisation / address. */
    private static final String HIERARCHY_JOINS = """
            LEFT JOIN bay b ON b.id = ai.owner_id AND ai.owner_type = 'BAY'
            LEFT JOIN voltage_level vl ON vl.id = b.owner_id AND b.owner_type = 'VOLTAGE_LEVEL'
            LEFT JOIN substation s ON s.id = CASE
                WHEN ai.owner_type = 'SUBSTATION' THEN ai.owner_id
                WHEN b.owner_type = 'SUBSTATION' THEN b.owner_id
                WHEN b.owner_type = 'VOLTAGE_LEVEL' THEN vl.owner_id
                END
            LEFT JOIN organisation o ON o.id = s.owner_id
            LEFT JOIN address_detail ad ON ad.id = s.address_detail_id
            """;

    private record Hierarchy(String joins, String where, Object[] params) {
    }

    private Hierarchy hierarchy(FilterContext f) {
        StringJoiner w = new StringJoiner(" AND ");
        List<Object> p = new ArrayList<>();
        if (f.orgId() != null) {
            w.add("o.id = ?");
            p.add(f.orgId());
        }
        if (f.city() != null && !f.city().isBlank()) {
            w.add("ad.city = ?");
            p.add(f.city());
        }
        if (f.ward() != null && !f.ward().isBlank()) {
            w.add("ad.ward_commune = ?");
            p.add(f.ward());
        }
        if (f.voltageLevel() != null && !f.voltageLevel().isBlank()) {
            w.add("vl.name = ?");
            p.add(f.voltageLevel());
        }
        if (f.manufacturerId() != null) {
            w.add("ai.manufacturer_id = ?");
            p.add(f.manufacturerId());
        }
        if (f.status() != null) {
            w.add("ai.status = ?");
            p.add(f.status());
        }
        String where = "WHERE 1=1" + (w.length() == 0 ? "" : " AND " + w);
        return new Hierarchy(HIERARCHY_JOINS, where, p.toArray());
    }

    private static final Map<String, String> CLASS_TABLE = new LinkedHashMap<>();

    static {
        CLASS_TABLE.put("Transformer", "transformer");
        CLASS_TABLE.put("Circuit Breaker", "circuit_breaker");
        CLASS_TABLE.put("Current Transformer (CT)", "current_transformer");
        CLASS_TABLE.put("Voltage Transformer (VT)", "voltage_transformer");
        CLASS_TABLE.put("Disconnector", "disconnector");
        CLASS_TABLE.put("Surge Arrester", "surge_arrester");
        CLASS_TABLE.put("Bushing", "bushing");
    }

    private String classFilter(FilterContext f) {
        if (f.assetClass() == null || f.assetClass().isBlank()) {
            return "";
        }
        String table = CLASS_TABLE.get(f.assetClass());
        if (table == null) {
            return "";
        }
        return " AND EXISTS (SELECT 1 FROM " + table + " c WHERE c.asset_info_id = ai.id)";
    }

    private static String statusLabel(Object status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (((Number) status).intValue()) {
            case 2 -> "In Operation";
            case 0 -> "Out of Operation";
            default -> "Unknown";
        };
    }

    private static double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0 : round1(100.0 * part / total);
    }

    // ---------------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------------

    public Dtos.FilterOptions filterOptions() {
        FilterContext none = new FilterContext(null, null, null, null, null, null, null);
        Hierarchy h = hierarchy(none);

        List<Dtos.Option> organisations = jdbc.query(
                "SELECT o.id, COALESCE(NULLIF(o.short_name, ''), o.name) AS label, count(*) AS c " +
                        "FROM asset_info ai " + h.joins() + " " + h.where() + " AND o.id IS NOT NULL " +
                        "GROUP BY o.id, o.short_name, o.name ORDER BY c DESC",
                h.params(), (rs, i) -> new Dtos.Option(String.valueOf(rs.getLong("id")),
                        rs.getString("label"), rs.getLong("c")));

        List<Dtos.Option> cities = jdbc.query(
                "SELECT ad.city AS label, count(*) AS c FROM asset_info ai " + h.joins() + " " + h.where() +
                        " AND ad.city IS NOT NULL AND ad.city <> '' GROUP BY ad.city ORDER BY c DESC",
                h.params(), (rs, i) -> new Dtos.Option(rs.getString("label"), rs.getString("label"), rs.getLong("c")));

        List<Dtos.Option> wards = jdbc.query(
                "SELECT ad.ward_commune AS label, count(*) AS c FROM asset_info ai " + h.joins() + " " + h.where() +
                        " AND ad.ward_commune IS NOT NULL AND ad.ward_commune <> '' GROUP BY ad.ward_commune ORDER BY c DESC",
                h.params(), (rs, i) -> new Dtos.Option(rs.getString("label"), rs.getString("label"), rs.getLong("c")));

        List<Dtos.Option> voltageLevels = jdbc.query(
                "SELECT vl.name AS label, count(*) AS c FROM asset_info ai " + h.joins() + " " + h.where() +
                        " AND vl.name IS NOT NULL AND vl.name <> '' GROUP BY vl.name ORDER BY c DESC",
                h.params(), (rs, i) -> new Dtos.Option(rs.getString("label"),
                        rs.getString("label"), rs.getLong("c")));

        List<Dtos.Option> manufacturers = jdbc.query(
                "SELECT m.id, m.name AS label, count(*) AS c FROM asset_info ai " +
                        "JOIN manufacturer m ON m.id = ai.manufacturer_id " + h.joins() + " " + h.where() +
                        " AND m.name IS NOT NULL GROUP BY m.id, m.name ORDER BY c DESC",
                h.params(), (rs, i) -> new Dtos.Option(String.valueOf(rs.getLong("id")),
                        rs.getString("label"), rs.getLong("c")));

        List<Dtos.Option> statuses = new ArrayList<>();
        jdbc.query("SELECT ai.status AS st, count(*) AS c FROM asset_info ai " + h.joins() + " " + h.where() +
                        " GROUP BY ai.status ORDER BY c DESC",
                h.params(), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        statuses.add(new Dtos.Option(String.valueOf(rs.getObject("st") == null ? -1 : rs.getLong("st")),
                                statusLabel(rs.getObject("st")), rs.getLong("c"))));

        List<Dtos.Option> assetClasses = new ArrayList<>();
        for (Map.Entry<String, String> e : CLASS_TABLE.entrySet()) {
            assetClasses.add(new Dtos.Option(e.getKey(), e.getKey(), countClass(e.getValue(), none)));
        }

        return new Dtos.FilterOptions(organisations, cities, wards, voltageLevels, manufacturers, statuses, assetClasses);
    }

    private long countClass(String table, FilterContext f) {
        Hierarchy h = hierarchy(f);
        Long v = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " c JOIN asset_info ai ON ai.id = c.asset_info_id " + h.joins() + " " + h.where(),
                Long.class, h.params());
        return v == null ? 0 : v;
    }

    // ---------------------------------------------------------------------
    // Dashboard 1 - Asset Overview
    // ---------------------------------------------------------------------

    public Dtos.AssetOverviewReport assetOverview(FilterContext f) {
        Hierarchy h = hierarchy(f);
        Object[] p = h.params();
        String cls = classFilter(f);

        long totalAssets = jdbc.queryForObject("SELECT count(*) FROM asset_info ai " + h.joins() + " " + h.where() + cls,
                Long.class, p);

        Long subst = jdbc.queryForObject("SELECT count(DISTINCT s.id) FROM asset_info ai " + h.joins() + " " + h.where() +
                " AND s.id IS NOT NULL", Long.class, p);
        long totalSubstations = subst == null ? 0 : subst;

        long totalTransformers = jdbc.queryForObject(
                "SELECT count(*) FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() + " " + h.where(),
                Long.class, p);

        double capacityMva = sumTransformerCapacityMva(h);
        List<Dtos.NameValue> byVoltage = transformerCapacityByVoltage(h);

        List<Dtos.OrgMix> mixByOrg = jdbc.query(
                "SELECT o.id, COALESCE(NULLIF(o.short_name, ''), o.name) AS label, count(*) AS c " +
                        "FROM asset_info ai " + h.joins() + " " + h.where() + " AND o.id IS NOT NULL" + cls +
                        " GROUP BY o.id, o.short_name, o.name ORDER BY c DESC",
                p, (rs, i) -> new Dtos.OrgMix(rs.getLong("id"), rs.getString("label"), rs.getDouble("c")));

        long mfgTotal = jdbc.queryForObject("SELECT count(*) FROM asset_info ai " + h.joins() + " " + h.where() + cls,
                Long.class, p);
        List<Dtos.ManufacturerStat> topMfgs = jdbc.query(
                "SELECT m.name, count(*) AS c FROM asset_info ai " +
                        "JOIN manufacturer m ON m.id = ai.manufacturer_id " + h.joins() + " " + h.where() + cls +
                        " GROUP BY m.id, m.name ORDER BY c DESC LIMIT 5",
                p, (rs, i) -> new Dtos.ManufacturerStat(rs.getString("name"), rs.getLong("c"),
                        pct(rs.getLong("c"), mfgTotal)));

        List<Dtos.MissingStat> completeness = jdbc.query(
                "SELECT count(*) AS total, count(ai.serial_no) AS serial_no, count(ai.manufacturer_id) AS mfg, " +
                        "count(ai.manufacturing_year) AS yr, count(ai.country_of_origin) AS country " +
                        "FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() + " " + h.where(),
                p, (rs, i) -> {
                    long total = rs.getLong("total");
                    return List.of(
                            missing("Manufacturer", rs.getLong("mfg"), total),
                            missing("Manufacturing Year", rs.getLong("yr"), total),
                            missing("Serial Number", rs.getLong("serial_no"), total),
                            missing("Country of Origin", rs.getLong("country"), total));
                }).stream().flatMap(List::stream).toList();

        return new Dtos.AssetOverviewReport(
                new Dtos.OverviewCards(totalAssets, totalSubstations, totalTransformers, round1(capacityMva)),
                byVoltage, mixByOrg, topMfgs, completeness);
    }

    private Dtos.MissingStat missing(String field, long present, long total) {
        long m = Math.max(0, total - present);
        return new Dtos.MissingStat(field, m, total, pct(m, total));
    }

    private double sumTransformerCapacityMva(Hierarchy h) {
        Double v = jdbc.queryForObject(
                "SELECT COALESCE(SUM(x.mva), 0) FROM (" +
                        "SELECT t.id, MAX(CASE WHEN pr.rated_power_unit = 'mVA' THEN pr.rated_power " +
                        "ELSE pr.rated_power / 1000.0 END) AS mva " +
                        "FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() +
                        " LEFT JOIN power_rating pr ON pr.transformer_id = t.id AND pr.rated_power IS NOT NULL " +
                        " " + h.where() + " GROUP BY t.id) x",
                Double.class, h.params());
        return v == null ? 0 : v;
    }

    private List<Dtos.NameValue> transformerCapacityByVoltage(Hierarchy h) {
        return jdbc.query(
                "SELECT CASE " +
                        " WHEN COALESCE(vl.base_voltage, 0) >= 500 THEN '500 kV' " +
                        " WHEN COALESCE(vl.base_voltage, 0) >= 220 THEN '220 kV' " +
                        " WHEN COALESCE(vl.base_voltage, 0) >= 110 THEN '110 kV' " +
                        " ELSE 'Distribution' END AS grp, " +
                        "COALESCE(SUM(x.mva), 0) AS mva " +
                        "FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() +
                        " LEFT JOIN (SELECT transformer_id, MAX(CASE WHEN rated_power_unit = 'mVA' " +
                        "THEN rated_power ELSE rated_power / 1000.0 END) AS mva " +
                        "FROM power_rating WHERE rated_power IS NOT NULL GROUP BY transformer_id) x ON x.transformer_id = t.id " +
                        " " + h.where() + " GROUP BY grp ORDER BY mva DESC",
                h.params(), (rs, i) -> new Dtos.NameValue(rs.getString("grp"), round1(rs.getDouble("mva"))));
    }

    // ---------------------------------------------------------------------
    // Dashboard 2 - Transformer Fleet
    // ---------------------------------------------------------------------

    public Dtos.TransformerFleetReport transformerFleet(FilterContext f) {
        Hierarchy h = hierarchy(f);
        Object[] p = h.params();

        long total = jdbc.queryForObject(
                "SELECT count(*) FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() + " " + h.where(),
                Long.class, p);

        double capacity = sumTransformerCapacityMva(h);

        Long withNameplate = jdbc.queryForObject(
                "SELECT count(DISTINCT t.id) FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " +
                        "JOIN asset_media am ON am.asset_id = ai.id AND am.media_type = 'NAMEPLATE' " + h.joins() + " " + h.where(),
                Long.class, p);
        double nameplatePct = total == 0 ? 0 : round1(100.0 * (withNameplate == null ? 0 : withNameplate) / total);

        Double avgYear = jdbc.queryForObject(
                "SELECT AVG(COALESCE(EXTRACT(YEAR FROM t.operation_date), EXTRACT(YEAR FROM ai.operation_date), " +
                        "ai.manufacturing_year)) AS yr FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " +
                        h.joins() + " " + h.where(),
                Double.class, p);
        Double avgAge = avgYear == null ? null : round1(LocalDate.now().getYear() - avgYear);

        List<Dtos.NameValue> byVoltage = transformerCapacityByVoltage(h);

        long mfgTotal = jdbc.queryForObject(
                "SELECT count(*) FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() + " " + h.where(),
                Long.class, p);
        List<Dtos.ManufacturerStat> topMfgs = jdbc.query(
                "SELECT m.name, count(*) AS c FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " +
                        "JOIN manufacturer m ON m.id = ai.manufacturer_id " + h.joins() + " " + h.where() +
                        " GROUP BY m.id, m.name ORDER BY c DESC LIMIT 5",
                p, (rs, i) -> new Dtos.ManufacturerStat(rs.getString("name"), rs.getLong("c"),
                        pct(rs.getLong("c"), mfgTotal)));

        List<Dtos.NameValue> ageDist = jdbc.query(
                "SELECT ai.manufacturing_year AS yr, count(*) AS c FROM transformer t " +
                        "JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() + " " + h.where() +
                        " AND ai.manufacturing_year IS NOT NULL GROUP BY ai.manufacturing_year ORDER BY yr",
                p, (rs, i) -> new Dtos.NameValue(String.valueOf(rs.getInt("yr")), rs.getDouble("c")));

        return new Dtos.TransformerFleetReport(
                new Dtos.FleetCards(total, round1(capacity), nameplatePct, avgAge),
                byVoltage, topMfgs, ageDist);
    }

    // ---------------------------------------------------------------------
    // Dashboard 2 - parallel operation screening
    // ---------------------------------------------------------------------

    private record TransformerData(long id, String name, Long substId, String substName, String vectorGroup,
                                   Set<Double> voltages, Set<Double> ukValues, List<String> tapTypes,
                                   Set<Double> tapVoltages) {
    }

    public Dtos.ParallelPairsReport parallelPairs(FilterContext f, double maxUkDiff, boolean sameSite,
                                                  boolean requireTapOverlap, double maxPowerRatio) {
        Hierarchy h = hierarchy(f);
        Map<Long, TransformerData> byId = new LinkedHashMap<>();

        jdbc.query(
                "SELECT t.id, COALESCE(NULLIF(ai.asset_name, ''), 'Transformer-' || t.id) AS name, " +
                        "s.id AS subst_id, s.name AS subst_name, t.vector_group " +
                        "FROM transformer t JOIN asset_info ai ON ai.id = t.asset_info_id " + h.joins() + " " + h.where() +
                        " AND (t.vector_group IS NOT NULL " +
                        " OR EXISTS (SELECT 1 FROM voltage_rating vr WHERE vr.transformer_id = t.id AND vr.voltage_l_l IS NOT NULL) " +
                        " OR EXISTS (SELECT 1 FROM short_circuit_impedance sci WHERE sci.transformer_id = t.id AND sci.impedance IS NOT NULL) " +
                        " OR EXISTS (SELECT 1 FROM tap_changer tc WHERE tc.transformer_id = t.id))",
                h.params(), rs -> {
                    long id = rs.getLong("id");
                    Long substId = rs.getObject("subst_id") == null ? null : rs.getLong("subst_id");
                    byId.put(id, new TransformerData(id, rs.getString("name"), substId, rs.getString("subst_name"),
                            rs.getString("vector_group"), new HashSet<>(), new HashSet<>(), new ArrayList<>(),
                            new HashSet<>()));
                });

        jdbc.query("SELECT transformer_id, voltage_l_l FROM voltage_rating WHERE voltage_l_l IS NOT NULL", rs -> {
            TransformerData d = byId.get(rs.getLong("transformer_id"));
            if (d != null) {
                d.voltages().add(rs.getDouble("voltage_l_l"));
            }
        });

        jdbc.query("SELECT DISTINCT transformer_id, impedance FROM short_circuit_impedance WHERE impedance IS NOT NULL", rs -> {
            TransformerData d = byId.get(rs.getLong("transformer_id"));
            if (d != null) {
                d.ukValues().add(rs.getDouble("impedance"));
            }
        });

        jdbc.query("SELECT transformer_id, type FROM tap_changer WHERE type IS NOT NULL", rs -> {
            TransformerData d = byId.get(rs.getLong("transformer_id"));
            if (d != null) {
                d.tapTypes().add(rs.getString("type"));
            }
        });

        jdbc.query("SELECT tc.transformer_id, tcv.voltage FROM tap_changer_voltage tcv " +
                "JOIN tap_changer tc ON tc.id = tcv.tap_changer_id WHERE tcv.voltage IS NOT NULL", rs -> {
            TransformerData d = byId.get(rs.getLong("transformer_id"));
            if (d != null) {
                d.tapVoltages().add(rs.getDouble("voltage"));
            }
        });

        Map<Long, Double> ratedPower = new HashMap<>();
        jdbc.query("SELECT transformer_id, rated_power, rated_power_unit FROM power_rating WHERE rated_power IS NOT NULL", rs -> {
            long tid = rs.getLong("transformer_id");
            double mva = "mVA".equalsIgnoreCase(rs.getString("rated_power_unit"))
                    ? rs.getDouble("rated_power") : rs.getDouble("rated_power") / 1000.0;
            ratedPower.merge(tid, mva, Math::max);
        });

        List<TransformerData> all = new ArrayList<>(byId.values());
        for (TransformerData d : all) {
            d.tapTypes().sort(String::compareToIgnoreCase);
        }

        List<Dtos.PairResult> results = new ArrayList<>();
        long seq = 0;
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                TransformerData a = all.get(i);
                TransformerData b = all.get(j);
                if (sameSite && !Objects.equals(a.substId(), b.substId())) {
                    continue;
                }
                Double rpa = ratedPower.get(a.id());
                Double rpb = ratedPower.get(b.id());
                if (maxPowerRatio > 0 && rpa != null && rpb != null) {
                    double ratio = Math.max(rpa, rpb) / Math.min(rpa, rpb);
                    if (ratio > maxPowerRatio) {
                        continue;
                    }
                }
                results.add(evaluatePair(seq++, a, b, maxUkDiff, requireTapOverlap));
            }
        }

        Comparator<Dtos.PairResult> byResult = (r1, r2) -> {
            int o = Integer.compare(rank(r1.result()), rank(r2.result()));
            if (o != 0) {
                return o;
            }
            Double d1 = r1.ukDiff() == null ? Double.MAX_VALUE : r1.ukDiff();
            Double d2 = r2.ukDiff() == null ? Double.MAX_VALUE : r2.ukDiff();
            return Double.compare(d1, d2);
        };
        results.sort(byResult);

        List<Dtos.PairResult> limited = results.size() > 500 ? results.subList(0, 500) : results;
        long compatible = results.stream().filter(r -> r.result().equals("Compatible")).count();
        long review = results.stream().filter(r -> r.result().equals("Review")).count();
        long notCompatible = results.stream().filter(r -> r.result().equals("Not compatible")).count();

        return new Dtos.ParallelPairsReport(results.size(), compatible, review, notCompatible,
                maxUkDiff, sameSite, limited);
    }

    private int rank(String result) {
        return switch (result) {
            case "Compatible" -> 0;
            case "Review" -> 1;
            default -> 2;
        };
    }

    private Dtos.PairResult evaluatePair(long id, TransformerData a, TransformerData b, double maxUkDiff,
                                         boolean requireTapOverlap) {
        StringBuilder detail = new StringBuilder();

        boolean ratioKnown = !a.voltages().isEmpty() && !b.voltages().isEmpty();
        boolean ratioMatch = ratioKnown && new HashSet<>(a.voltages()).equals(b.voltages());
        if (ratioKnown) {
            detail.append("Ratio: ").append(ratioMatch ? "OK" : "Mismatch").append("; ");
        } else {
            detail.append("Ratio: no data; ");
        }

        boolean vectorKnown = a.vectorGroup() != null && b.vectorGroup() != null;
        boolean vectorMatch = vectorKnown && a.vectorGroup().equalsIgnoreCase(b.vectorGroup());
        if (vectorKnown) {
            detail.append("Vector: ").append(vectorMatch ? "OK" : "Mismatch").append("; ");
        } else {
            detail.append("Vector: no data; ");
        }

        boolean tapKnown = !a.tapTypes().isEmpty() || !a.tapVoltages().isEmpty()
                || !b.tapTypes().isEmpty() || !b.tapVoltages().isEmpty();
        boolean tapMatch = a.tapTypes().equals(b.tapTypes());
        if (tapKnown && tapMatch && requireTapOverlap) {
            boolean aHas = !a.tapVoltages().isEmpty();
            boolean bHas = !b.tapVoltages().isEmpty();
            if (aHas && bHas) {
                Set<Double> common = new HashSet<>(a.tapVoltages());
                common.retainAll(b.tapVoltages());
                tapMatch = !common.isEmpty();
            } else if (aHas || bHas) {
                tapMatch = false;
            }
        }
        if (tapKnown) {
            detail.append("Tap: ").append(tapMatch ? "OK" : "Mismatch").append("; ");
        } else {
            detail.append("Tap: no data; ");
        }

        boolean coreMismatch = (ratioKnown && !ratioMatch) || (vectorKnown && !vectorMatch) || (tapKnown && !tapMatch);

        Double ukDiff = null;
        if (!a.ukValues().isEmpty() && !b.ukValues().isEmpty()) {
            double maxDiff = 0;
            for (double x : a.ukValues()) {
                for (double y : b.ukValues()) {
                    maxDiff = Math.max(maxDiff, Math.abs(x - y));
                }
            }
            ukDiff = round1(maxDiff);
            detail.append("Uk% diff: ").append(ukDiff).append("%");
        } else {
            detail.append("Uk%: no data");
        }

        String result;
        if (coreMismatch) {
            result = "Not compatible";
        } else if (ukDiff == null) {
            result = "Review";
        } else if (ukDiff <= maxUkDiff * 0.6) {
            result = "Compatible";
        } else if (ukDiff <= maxUkDiff) {
            result = "Review";
        } else {
            result = "Not compatible";
        }

        String ratioText = a.voltages().stream().sorted().map(ReportService::fmt)
                .reduce((x, y) -> x + "/" + y).orElse("N/A") + " kV";
        String vectorText = vectorKnown ? a.vectorGroup() : "N/A";
        String tapText = tapKnown ? String.join(",", a.tapTypes()) + " " + a.tapVoltages().size() + " taps" : "N/A";
        String siteA = a.substName() == null ? "-" : a.substName();
        String siteB = b.substName() == null ? "-" : b.substName();

        return new Dtos.PairResult(id, a.name(), b.name(), siteA, siteB, ratioText, vectorText, tapText,
                ukDiff, result, detail.toString().trim(),
                ratioKnown ? ratioMatch : null,
                vectorKnown ? vectorMatch : null,
                tapKnown ? tapMatch : null,
                ukDiff != null ? ukDiff <= maxUkDiff : null);
    }

    // ---------------------------------------------------------------------
    // Dashboard 3 - Asset Data Quality
    // ---------------------------------------------------------------------

    private record FieldSpec(String code, String category, String label, String expr) {
    }

    private record ClassResult(String cls, long total, Map<String, Long> present, long assetsWithMissing) {
    }

    private static List<FieldSpec> commonFields() {
        return List.of(
                new FieldSpec("serial_no", "Properties", "Serial No.", "count(ai.serial_no)"),
                new FieldSpec("manufacturer", "Properties", "Manufacturer", "count(ai.manufacturer_id)"),
                new FieldSpec("asset_id", "Properties", "Asset ID", "count(ai.appartus_id)"),
                new FieldSpec("operating_date", "Properties", "Operating date", "count(ai.operation_date)"),
                new FieldSpec("phase", "Properties", "Phase", "count(ai.phase)"),
                new FieldSpec("nameplate", "Nameplate", "Nameplate",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM asset_media am WHERE am.asset_id = ai.id AND am.media_type = 'NAMEPLATE') THEN 1 END)")
        );
    }

    private static final Map<String, List<FieldSpec>> CLASS_FIELDS = new LinkedHashMap<>();

    static {
        CLASS_FIELDS.put("Transformer", List.of(
                new FieldSpec("vector_group", "Basic Specification", "Vector Group", "count(t.vector_group)"),
                new FieldSpec("voltage_ratio", "Basic Specification", "Voltage Ratio",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM voltage_rating vr WHERE vr.transformer_id = t.id AND vr.voltage_l_l IS NOT NULL) THEN 1 END)"),
                new FieldSpec("rated_power", "Ratings", "Rated Power",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM power_rating pr WHERE pr.transformer_id = t.id AND pr.rated_power IS NOT NULL) THEN 1 END)"),
                new FieldSpec("uk", "Basic Specification", "Uk%",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM short_circuit_impedance sci WHERE sci.transformer_id = t.id AND sci.impedance IS NOT NULL) THEN 1 END)"),
                new FieldSpec("no_of_taps", "Basic Specification", "Number of Taps",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM tap_changer tch WHERE tch.transformer_id = t.id) THEN 1 END)"),
                new FieldSpec("tap_voltage", "Basic Specification", "Tap voltage",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM tap_changer tch2 JOIN tap_changer_voltage tcv ON tcv.tap_changer_id = tch2.id WHERE tch2.transformer_id = t.id AND tcv.voltage IS NOT NULL) THEN 1 END)"),
                new FieldSpec("description", "Others", "Description", "count(ai.description)"),
                new FieldSpec("rated_frequency", "Others", "Rated frequency", "count(t.rated_frequency)")
        ));
        CLASS_FIELDS.put("Circuit Breaker", List.of(
                new FieldSpec("rated_voltage_ll", "Ratings", "Rated voltage L-L",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM circuit_breaker_rating cbr WHERE cbr.circuit_breaker_id = cb.id AND cbr.rated_voltage_l_l IS NOT NULL) THEN 1 END)"),
                new FieldSpec("rated_current", "Ratings", "Rated current",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM circuit_breaker_rating cbr2 WHERE cbr2.circuit_breaker_id = cb.id AND cbr2.rated_current IS NOT NULL) THEN 1 END)"),
                new FieldSpec("rated_short_circuit_breaking_current", "Ratings", "Rated short-circuit breaking current",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM circuit_breaker_rating cbr3 WHERE cbr3.circuit_breaker_id = cb.id AND cbr3.rated_short_circuit_breaking_current IS NOT NULL) THEN 1 END)"),
                new FieldSpec("interrupting_duty_cycle", "Ratings", "Interrupting duty cycle",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM circuit_breaker_rating cbr4 WHERE cbr4.circuit_breaker_id = cb.id AND (cbr4.interrupting_duty_cycle IS NOT NULL OR cbr4.cim_interrupting_duty_cycle IS NOT NULL)) THEN 1 END)"),
                new FieldSpec("interrupting_medium", "Others", "Interrupting medium", "count(cb.interrupting_medium)"),
                new FieldSpec("number_of_phases", "Others", "Number of phases", "count(cb.number_of_phases)")
        ));
        CLASS_FIELDS.put("Current Transformer (CT)", List.of(
                new FieldSpec("standard", "Basic Specification", "Standard", "count(ct.standard)"),
                new FieldSpec("primary_windings", "Basic Specification", "Primary windings", "count(ct.primary_winding)"),
                new FieldSpec("tap_configuration", "Basic Specification", "Tap configuration",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM current_transformer_tap ctt WHERE ctt.current_transformer_id = ct.id AND ctt.ipn IS NOT NULL) THEN 1 END)"),
                new FieldSpec("number_core", "Others", "Number of cores", "count(ct.number_core)")
        ));
        CLASS_FIELDS.put("Voltage Transformer (VT)", List.of(
                new FieldSpec("standard", "Basic Specification", "Standard", "count(vt.standard)"),
                new FieldSpec("upr", "Basic Specification", "Upr", "count(COALESCE(vt.upr_value, vt.rated_votage_value))"),
                new FieldSpec("windings_configuration", "Basic Specification", "Windings configuration",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM voltage_transformer_configuration vc WHERE vc.voltage_transformer_id = vt.id AND vc.usr_value IS NOT NULL) THEN 1 END)"),
                new FieldSpec("rated_frequency", "Others", "Rated frequency", "count(vt.rated_frequency)")
        ));
        CLASS_FIELDS.put("Disconnector", List.of(
                new FieldSpec("rated_voltage", "Ratings", "Rated voltage", "count(d.rated_voltage)"),
                new FieldSpec("rated_current", "Ratings", "Rated current", "count(d.rated_current)"),
                new FieldSpec("rated_frequency", "Others", "Rated frequency", "count(d.rated_frequency)")
        ));
        CLASS_FIELDS.put("Surge Arrester", List.of(
                new FieldSpec("rated_voltage", "Ratings", "Rated voltage Ur",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM surge_arrester_rating sar WHERE sar.surge_arrester_id = sa.id AND sar.rated_voltage IS NOT NULL) THEN 1 END)"),
                new FieldSpec("uc", "Ratings", "Continuous operating voltage Uc",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM surge_arrester_rating sar2 WHERE sar2.surge_arrester_id = sa.id AND sar2.continous_operating_voltage IS NOT NULL) THEN 1 END)"),
                new FieldSpec("short_time_withstand_current", "Ratings", "Short time withstand current",
                        "count(CASE WHEN EXISTS (SELECT 1 FROM surge_arrester_rating sar3 WHERE sar3.surge_arrester_id = sa.id AND sar3.short_time_withstand_current IS NOT NULL) THEN 1 END)"),
                new FieldSpec("unit_count", "Others", "Unit count", "count(sa.unit_count)")
        ));
        CLASS_FIELDS.put("Bushing", List.of(
                new FieldSpec("insulation_type", "Basic Specification", "Insulation type", "count(bus.insul_type)"),
                new FieldSpec("max_voltage", "Ratings", "Max. voltage", "count(bus.max_system_voltage)"),
                new FieldSpec("rated_current", "Ratings", "Rated current", "count(bus.rated_current)"),
                new FieldSpec("insul_level_ll", "Ratings", "Insul. level L-L", "count(bus.insul_level_ll_l)"),
                new FieldSpec("cap_c1", "Ratings", "Cap (C1)", "count(bus.cap_c1)"),
                new FieldSpec("df_c1", "Ratings", "DF (C1)", "count(bus.df_c1)"),
                new FieldSpec("rated_frequency", "Others", "Rated frequency", "count(bus.rated_frequency)")
        ));
    }

    public Dtos.DataQualityReport dataQuality(FilterContext f) {
        List<String> classes = new ArrayList<>(CLASS_TABLE.keySet());
        if (f.assetClass() != null && !f.assetClass().isBlank()) {
            classes = classes.stream().filter(c -> c.equals(f.assetClass())).toList();
        }

        List<ClassResult> results = new ArrayList<>();
        long presentAll = 0;
        long missingAll = 0;
        long assetsWithMissingAll = 0;
        for (String cls : classes) {
            ClassResult cr = qualityClass(cls, f);
            results.add(cr);
            assetsWithMissingAll += cr.assetsWithMissing();
            long fieldCount = commonFields().size() + CLASS_FIELDS.getOrDefault(cls, List.of()).size();
            for (long v : cr.present().values()) {
                presentAll += v;
            }
            missingAll += (long) cr.total() * fieldCount - cr.present().values().stream().mapToLong(Long::longValue).sum();
        }

        double overall = (presentAll + missingAll) == 0 ? 0 : round1(100.0 * presentAll / (presentAll + missingAll));

        Map<String, long[]> cat = new LinkedHashMap<>();
        for (ClassResult cr : results) {
            for (FieldSpec sf : commonFields()) {
                long[] c = cat.computeIfAbsent(sf.category(), k -> new long[2]);
                c[0] += cr.present().getOrDefault(sf.code(), 0L);
                c[1] += cr.total();
            }
            for (FieldSpec sf : CLASS_FIELDS.getOrDefault(cr.cls(), List.of())) {
                long[] c = cat.computeIfAbsent(sf.category(), k -> new long[2]);
                c[0] += cr.present().getOrDefault(sf.code(), 0L);
                c[1] += cr.total();
            }
        }
        List<Dtos.NameValue> byCategory = cat.entrySet().stream()
                .map(e -> new Dtos.NameValue(e.getKey(), e.getValue()[1] == 0 ? 0
                        : round1(100.0 * e.getValue()[0] / e.getValue()[1])))
                .toList();

        List<Dtos.NameValue> statusDist = new ArrayList<>();
        Hierarchy hs = hierarchy(f);
        jdbc.query("SELECT ai.status AS st, count(*) AS c FROM asset_info ai " + hs.joins() + " " + hs.where() +
                        classFilter(f) + " GROUP BY ai.status ORDER BY c DESC",
                hs.params(), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        statusDist.add(new Dtos.NameValue(statusLabel(rs.getObject("st")), rs.getDouble("c"))));

        Long noNameplate = jdbc.queryForObject("SELECT count(*) FROM asset_info ai " + hs.joins() + " " + hs.where() +
                " AND NOT EXISTS (SELECT 1 FROM asset_media am WHERE am.asset_id = ai.id AND am.media_type = 'NAMEPLATE')" + classFilter(f),
                Long.class, hs.params());

        PhaseAgg phase = phaseAnalysis(f);
        long phaseMonitored = phase.monitored();
        double balancedPct = phase.groups() == 0 ? 0 : round1(100.0 * phase.balanced() / phase.groups());
        long mismatches = phase.groups() - phase.balanced();

        List<Dtos.MissingKeyField> missingFields = new ArrayList<>();
        for (ClassResult cr : results) {
            for (FieldSpec sf : commonFields()) {
                missingFields.add(toMissing(cr, sf));
            }
            for (FieldSpec sf : CLASS_FIELDS.getOrDefault(cr.cls(), List.of())) {
                missingFields.add(toMissing(cr, sf));
            }
        }

        Dtos.QualityCards cards = new Dtos.QualityCards(overall, assetsWithMissingAll,
                noNameplate == null ? 0 : noNameplate, phaseMonitored, balancedPct, mismatches);

        return new Dtos.DataQualityReport(cards, byCategory, statusDist, phase.rows(), missingFields);
    }

    private Dtos.MissingKeyField toMissing(ClassResult cr, FieldSpec sf) {
        long present = cr.present().getOrDefault(sf.code(), 0L);
        long missing = Math.max(0, cr.total() - present);
        return new Dtos.MissingKeyField(sf.label(), sf.category(), cr.cls(), missing, cr.total(),
                pct(missing, cr.total()));
    }

    private String aliasFor(String cls) {
        return switch (cls) {
            case "Circuit Breaker" -> "cb";
            case "Current Transformer (CT)" -> "ct";
            case "Voltage Transformer (VT)" -> "vt";
            case "Disconnector" -> "d";
            case "Surge Arrester" -> "sa";
            case "Bushing" -> "bus";
            default -> "t";
        };
    }

    private ClassResult qualityClass(String cls, FilterContext f) {
        String table = CLASS_TABLE.get(cls);
        String alias = aliasFor(cls);
        List<FieldSpec> common = commonFields();
        List<FieldSpec> specific = CLASS_FIELDS.getOrDefault(cls, List.of());

        List<String> missCond = new ArrayList<>();
        missCond.add("ai.serial_no IS NULL");
        missCond.add("ai.manufacturer_id IS NULL");
        missCond.add("ai.appartus_id IS NULL");
        missCond.add("ai.operation_date IS NULL");
        missCond.add("ai.phase IS NULL");
        missCond.add("NOT EXISTS (SELECT 1 FROM asset_media am WHERE am.asset_id = ai.id AND am.media_type = 'NAMEPLATE')");
        for (FieldSpec sf : specific) {
            missCond.add(negate(sf.expr()));
        }

        StringBuilder select = new StringBuilder("SELECT count(*) AS total");
        for (FieldSpec sf : common) {
            select.append(", ").append(sf.expr()).append(" AS f_").append(sf.code());
        }
        for (FieldSpec sf : specific) {
            select.append(", ").append(sf.expr()).append(" AS f_").append(sf.code());
        }
        select.append(", count(*) FILTER (WHERE ").append(String.join(" OR ", missCond)).append(") AS with_missing");

        Hierarchy h = hierarchy(f);
        String from = " FROM " + table + " " + alias +
                " JOIN asset_info ai ON ai.id = " + alias + ".asset_info_id " + h.joins() + " " + h.where();

        long[] t = {0};
        long[] withMissing = {0};
        Map<String, Long> present = new HashMap<>();
        jdbc.query(select + from, h.params(), (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
            t[0] = rs.getLong("total");
            for (FieldSpec sf : common) {
                present.put(sf.code(), rs.getLong("f_" + sf.code()));
            }
            for (FieldSpec sf : specific) {
                present.put(sf.code(), rs.getLong("f_" + sf.code()));
            }
            withMissing[0] = rs.getLong("with_missing");
        });

        return new ClassResult(cls, t[0], present, withMissing[0]);
    }

    /** Turn a count(expr) into a boolean predicate for "missing". */
    private String negate(String countExpr) {
        String inner = countExpr.substring(countExpr.indexOf('(') + 1, countExpr.lastIndexOf(')')).trim();
        return "(" + inner + ") IS NULL";
    }

    private record PhaseAgg(long monitored, long groups, long balanced, List<Dtos.PhaseBalanceRow> rows) {
    }

    private PhaseAgg phaseAnalysis(FilterContext f) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : CLASS_TABLE.entrySet()) {
            if (f.assetClass() != null && !f.assetClass().isBlank() && !f.assetClass().equals(e.getKey())) {
                continue;
            }
            String alias = aliasFor(e.getKey());
            String phaseExpr = (e.getKey().equals("Current Transformer (CT)") || e.getKey().equals("Voltage Transformer (VT)"))
                    ? "COALESCE(" + alias + ".phase, ai.phase)" : "ai.phase";
            parts.add("SELECT '" + e.getKey() + "' AS cls, " + phaseExpr + " AS phase, s.id AS subst_id " +
                    "FROM " + e.getValue() + " " + alias + " JOIN asset_info ai ON ai.id = " + alias + ".asset_info_id " +
                    HIERARCHY_JOINS + " " + whereNoClass(f));
        }
        if (parts.isEmpty()) {
            return new PhaseAgg(0, 0, 0, List.of());
        }
        String union = String.join(" UNION ALL ", parts);
        // Each UNION part repeats the same filter placeholders, so repeat the params per part.
        List<Object> one = phaseParams(f);
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            params.addAll(one);
        }
        Object[] paramsArr = params.toArray();

        Map<String, long[]> groupCounts = new LinkedHashMap<>();
        jdbc.query("SELECT cls, phase, subst_id, count(*) AS c FROM (" + union + ") x " +
                        " WHERE phase IN ('A','B','C') GROUP BY cls, phase, subst_id",
                paramsArr, rs -> {
                    String key = rs.getString("cls") + "|" + rs.getLong("subst_id");
                    long[] g = groupCounts.computeIfAbsent(key, k -> new long[3]);
                    g[switch (rs.getString("phase")) {
                        case "A" -> 0;
                        case "B" -> 1;
                        default -> 2;
                    }] += rs.getLong("c");
                });

        long monitored = 0;
        long balanced = 0;
        long groups = groupCounts.size();
        for (long[] g : groupCounts.values()) {
            monitored += g[0] + g[1] + g[2];
            if (g[0] == g[1] && g[1] == g[2] && g[0] > 0) {
                balanced++;
            }
        }

        Map<String, long[]> perClass = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : groupCounts.entrySet()) {
            String cls = e.getKey().substring(0, e.getKey().indexOf('|'));
            long[] arr = perClass.computeIfAbsent(cls, k -> new long[3]);
            arr[0] += e.getValue()[0];
            arr[1] += e.getValue()[1];
            arr[2] += e.getValue()[2];
        }

        List<Dtos.PhaseBalanceRow> rows = perClass.entrySet().stream()
                .map(e -> {
                    long a = e.getValue()[0], b = e.getValue()[1], c = e.getValue()[2];
                    String status;
                    if (a == b && b == c && a > 0) {
                        status = "Balanced";
                    } else if (a + b + c == 0) {
                        status = "No data";
                    } else {
                        status = "Mismatch";
                    }
                    return new Dtos.PhaseBalanceRow(e.getKey(), a, b, c, status);
                })
                .sorted(Comparator.comparing(Dtos.PhaseBalanceRow::assetClass))
                .toList();

        return new PhaseAgg(monitored, groups, balanced, rows);
    }

    private String whereNoClass(FilterContext f) {
        StringJoiner w = new StringJoiner(" AND ");
        if (f.orgId() != null) {
            w.add("o.id = ?");
        }
        if (f.city() != null && !f.city().isBlank()) {
            w.add("ad.city = ?");
        }
        if (f.ward() != null && !f.ward().isBlank()) {
            w.add("ad.ward_commune = ?");
        }
        if (f.voltageLevel() != null && !f.voltageLevel().isBlank()) {
            w.add("vl.name = ?");
        }
        if (f.manufacturerId() != null) {
            w.add("ai.manufacturer_id = ?");
        }
        if (f.status() != null) {
            w.add("ai.status = ?");
        }
        return "WHERE 1=1" + (w.length() == 0 ? "" : " AND " + w);
    }

    private List<Object> phaseParams(FilterContext f) {
        List<Object> p = new ArrayList<>();
        if (f.orgId() != null) {
            p.add(f.orgId());
        }
        if (f.city() != null && !f.city().isBlank()) {
            p.add(f.city());
        }
        if (f.ward() != null && !f.ward().isBlank()) {
            p.add(f.ward());
        }
        if (f.voltageLevel() != null && !f.voltageLevel().isBlank()) {
            p.add(f.voltageLevel());
        }
        if (f.manufacturerId() != null) {
            p.add(f.manufacturerId());
        }
        if (f.status() != null) {
            p.add(f.status());
        }
        return p;
    }
}
