package com.collectormarket.backend.catalog;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** Logs a title the parser/matcher couldn't resolve to {@code unmatched_listing} for triage. */
@Component
public class UnmatchedListingRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UnmatchedListingRecorder(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(String rawTitle, ParsedTitle parsed) {
        String extractedFieldsJson = objectMapper.writeValueAsString(toMap(parsed));
        jdbcTemplate.update("""
                INSERT INTO unmatched_listing (raw_title, extracted_fields)
                VALUES (?, ?::jsonb)
                """, rawTitle, extractedFieldsJson);
    }

    private Map<String, Object> toMap(ParsedTitle parsed) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("year", parsed.year());
        fields.put("playerName", parsed.playerName());
        fields.put("brand", parsed.brand());
        fields.put("setName", parsed.setName());
        fields.put("cardNumber", parsed.cardNumber());
        fields.put("gradeSource", parsed.gradeSource());
        fields.put("gradeValue", parsed.gradeValue());
        fields.put("variantDetected", parsed.variantDetected());
        return fields;
    }
}
