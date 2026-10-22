package com.parthopratim.spendcheck.ledger;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL-side aggregation of confirmed spending; the budget engine works from these rows. */
@Repository
public class SpendAggregateRepository {

    private static final String SQL = """
            SELECT (t.spent_at AT TIME ZONE :tz)::date AS day,
                   t.category_id, c.kind, t.need_or_want, lower(t.merchant) AS merchant_key,
                   count(*) AS cnt, sum(t.amount_paise) AS total
            FROM transactions t
            JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.status = 'CONFIRMED'
              AND t.spent_at >= :from AND t.spent_at < :to
            GROUP BY 1, 2, 3, 4, 5
            ORDER BY 1, 2
            """;

    /** Per lower(merchant): the spelling used most often, ties going to the most recent spend. */
    private static final String LABELS_SQL = """
            SELECT DISTINCT ON (lower(merchant)) lower(merchant) AS merchant_key, merchant
            FROM (SELECT t.merchant, count(*) AS cnt, max(t.spent_at) AS last_spent, max(t.created_at) AS last_created
                  FROM transactions t
                  WHERE t.user_id = :userId AND t.status = 'CONFIRMED' AND t.merchant IS NOT NULL
                    AND t.spent_at >= :from AND t.spent_at < :to
                  GROUP BY t.merchant) s
            ORDER BY lower(merchant), cnt DESC, last_spent DESC, last_created DESC
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public SpendAggregateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Confirmed spending of the user dated {@code from..to} (inclusive) in {@code zone}. */
    public List<SpendAggregate> between(long userId, LocalDate from, LocalDate to, ZoneId zone) {
        return jdbc.query(SQL, params(userId, from, to, zone), (rs, i) -> {
            String tag = rs.getString("need_or_want");
            return new SpendAggregate(
                    rs.getObject("day", LocalDate.class),
                    rs.getLong("category_id"),
                    CategoryKind.valueOf(rs.getString("kind")),
                    tag == null ? null : NeedOrWant.valueOf(tag),
                    rs.getString("merchant_key"),
                    rs.getLong("cnt"),
                    rs.getLong("total"));
        });
    }

    /** Display spelling of each merchant key (see {@link SpendAggregate#merchantKey()}) dated {@code from..to}. */
    public Map<String, String> merchantLabels(long userId, LocalDate from, LocalDate to, ZoneId zone) {
        Map<String, String> out = new HashMap<>();
        jdbc.query(LABELS_SQL, params(userId, from, to, zone),
                rs -> { out.put(rs.getString("merchant_key"), rs.getString("merchant")); });
        return out;
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
