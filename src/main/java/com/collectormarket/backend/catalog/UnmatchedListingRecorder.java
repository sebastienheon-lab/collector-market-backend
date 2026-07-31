package com.collectormarket.backend.catalog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.domain.UnmatchedListing;
import com.collectormarket.backend.domain.UnmatchedListingRepository;

import tools.jackson.databind.ObjectMapper;

/** Logs a title the parser/matcher couldn't resolve to {@code unmatched_listing} for triage. */
@Component
public class UnmatchedListingRecorder {

    private final UnmatchedListingRepository unmatchedListingRepository;
    private final ObjectMapper objectMapper;

    public UnmatchedListingRecorder(UnmatchedListingRepository unmatchedListingRepository,
            ObjectMapper objectMapper) {
        this.unmatchedListingRepository = unmatchedListingRepository;
        this.objectMapper = objectMapper;
    }

    public void record(String rawTitle, ParsedTitle parsed) {
        String extractedFields = objectMapper.writeValueAsString(toMap(parsed));
        unmatchedListingRepository.save(new UnmatchedListing(rawTitle, Instant.now(), extractedFields));
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
