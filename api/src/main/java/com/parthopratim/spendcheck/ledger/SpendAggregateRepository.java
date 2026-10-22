package com.parthopratim.spendcheck.ledger;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL-side aggregation of confirmed spending; the budget engine works from these rows. */
@Repository
public class SpendAggregateRepository {

    /**
     * Grouped by the merchant as typed, so the display spelling of each lower(merchant) can be picked from the same
     * rows; {@link #spending} folds the spellings back together.
     */
    private static final String SQL = """
            SELECT (t.spent_at AT TIME ZONE :tz)::date AS day,
                   t.category_id, c.kind, t.need_or_want, lower(t.merchant) AS merchant_key, t.merchant,
                   count(*) AS cnt, sum(t.amount_paise) AS total,
                   max(t.spent_at) AS last_spent, max(t.created_at) AS last_created
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.status = 'CONFIRMED'
              AND t.spent_at >= :from AND t.spent_at < :to
            GROUP BY 1, 2, 3, 4, 6
            ORDER BY 1, 2
            """;

    /** Display spelling per merchant key: the most used, ties going to the latest spend, then the latest created. */
    private static final Comparator<Spelling> LABEL_ORDER = Comparator.comparingLong(Spelling::count)
            .thenComparing(Spelling::lastSpent).thenComparing(Spelling::lastCreated);

    /**
     * Spending rows plus the display spelling of each merchant key (see {@link SpendAggregate#merchantKey()}).
     */
    public record Spending(List<SpendAggregate> rows, Map<String, String> merchantLabels) {}

    private record Key(LocalDate day, long categoryId, CategoryKind kind, NeedOrWant needOrWant, String merchantKey) {}

    private record Spelling(String merchant, long count, OffsetDateTime lastSpent, OffsetDateTime lastCreated) {}

    private final NamedParameterJdbcTemplate jdbc;

    public SpendAggregateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Confirmed spending of the user dated {@code from..to} (inclusive) in {@code zone}. */
    public List<SpendAggregate> between(long userId, LocalDate from, LocalDate to, ZoneId zone) {
        return spending(userId, from, to, zone).rows();
    }

    /** {@link #between} plus each merchant key's display spelling, from the same single query. */
    public Spending spending(long userId, LocalDate from, LocalDate to, ZoneId zone) {
        Map<Key, long[]> sums = new LinkedHashMap<>();   // keeps the SQL order
        Map<String, Map<String, Spelling>> spellings = new HashMap<>();
        jdbc.query(SQL, params(userId, from, to, zone), rs -> {
            String tag = rs.getString("need_or_want");
            String merchantKey = rs.getString("merchant_key");
            long count = rs.getLong("cnt");
            Key key = new Key(rs.getObject("day", LocalDate.class), rs.getLong("category_id"),
                    CategoryKind.valueOf(rs.getString("kind")), tag == null ? null : NeedOrWant.valueOf(tag),
                    merchantKey);
            long[] sum = sums.computeIfAbsent(key, k -> new long[2]);
            sum[0] += count;
            sum[1] += rs.getLong("total");
            if (merchantKey != null) {
                Spelling row = new Spelling(rs.getString("merchant"), count,
                        rs.getObject("last_spent", OffsetDateTime.class),
                        rs.getObject("last_created", OffsetDateTime.class));
                spellings.computeIfAbsent(merchantKey, k -> new HashMap<>()).merge(row.merchant(), row,
                        (a, b) -> new Spelling(a.merchant(), a.count() + b.count(), max(a.lastSpent(), b.lastSpent()),
                                max(a.lastCreated(), b.lastCreated())));
            }
        });
        List<SpendAggregate> rows = sums.entrySet().stream().map(e -> {
            Key k = e.getKey();
            return new SpendAggregate(k.day(), k.categoryId(), k.kind(), k.needOrWant(), k.merchantKey(),
                    e.getValue()[0], e.getValue()[1]);
        }).toList();
        Map<String, String> labels = new HashMap<>();
        spellings.forEach((key, bySpelling) -> labels.put(key,
                bySpelling.values().stream().max(LABEL_ORDER).orElseThrow().merchant()));
        return new Spending(rows, labels);
    }

    private static OffsetDateTime max(OffsetDateTime a, OffsetDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static MapSqlParameterSource params(long userId, LocalDate from, LocalDate to, ZoneId zone) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tz", zone.getId())
                .addValue("from", OffsetDateTime.ofInstant(from.atStartOfDay(zone).toInstant(), ZoneOffset.UTC))
                .addValue("to", OffsetDateTime.ofInstant(to.plusDays(1).atStartOfDay(zone).toInstant(),
                        ZoneOffset.UTC));
    }
}
