package com.collectormarket.backend.api;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.api.SearchQueryParser.ParsedQuery;
import com.collectormarket.backend.api.dto.SearchResultItem;

/**
 * Postgres-native card search (§8.1). Ranks by trigram similarity on {@code player_name} (backed by
 * the {@code idx_card_player_name_trgm} GIN index, V013) and additionally constrains on year and
 * card number when the query carried them. When the query has no name text (e.g. only a year),
 * similarity ranking is skipped and results order by year.
 */
@Component
public class SearchQueryStore {

    private static final RowMapper<SearchResultItem> RESULT_MAPPER = (rs, rowNum) -> new SearchResultItem(
            rs.getObject("id", UUID.class),
            rs.getString("player_name"),
            rs.getInt("year"),
            rs.getString("set_name"),
            rs.getString("card_number"),
            rs.getString("sport"),
            rs.getBoolean("is_rookie"),
            rs.getBigDecimal("latest_sale_price"),
            rs.getObject("latest_sale_date", LocalDate.class));

    private final JdbcTemplate jdbcTemplate;

    public SearchQueryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchResultItem> search(ParsedQuery query, Short sportId, int limit, int offset) {
        boolean ranked = query.playerTerm() != null;

        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.player_name, c.year, c.set_name, c.card_number, s.code AS sport,
                       c.is_rookie, ls.sale_price AS latest_sale_price, ls.sold_at::date AS latest_sale_date
                """);
        List<Object> args = new ArrayList<>();
        if (ranked) {
            sql.append(", similarity(c.player_name, ?) AS sim\n");
            args.add(query.playerTerm());
        }
        sql.append("""
                FROM card c
                JOIN sport s ON s.id = c.sport_id
                LEFT JOIN LATERAL (
                    SELECT sale_price, sold_at FROM price_snapshot ps
                    WHERE ps.card_id = c.id ORDER BY sold_at DESC LIMIT 1
                ) ls ON true
                WHERE 1=1
                """);
        appendFilters(sql, args, query, sportId);
        sql.append(ranked ? "ORDER BY sim DESC, c.year DESC\n" : "ORDER BY c.year DESC, c.player_name ASC\n");
        sql.append("LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), RESULT_MAPPER, args.toArray());
    }

    public long count(ParsedQuery query, Short sportId) {
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM card c WHERE 1=1\n");
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, query, sportId);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count != null ? count : 0L;
    }

    private void appendFilters(StringBuilder sql, List<Object> args, ParsedQuery query, Short sportId) {
        if (query.playerTerm() != null) {
            sql.append("  AND c.player_name % ?\n");
            args.add(query.playerTerm());
        }
        if (query.year() != null) {
            sql.append("  AND c.year = ?\n");
            args.add(query.year());
        }
        if (query.cardNumber() != null) {
            sql.append("  AND c.card_number = ?\n");
            args.add(query.cardNumber());
        }
        if (sportId != null) {
            sql.append("  AND c.sport_id = ?\n");
            args.add(sportId);
        }
    }
}
