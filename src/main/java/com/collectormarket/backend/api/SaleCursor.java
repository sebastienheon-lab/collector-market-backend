package com.collectormarket.backend.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.collectormarket.backend.api.error.InvalidCursorException;

/**
 * Opaque keyset cursor for the price-history {@code sales} array. Encodes the {@code (sold_at, id)}
 * of the last returned sale so the next page can resume with a stable {@code (sold_at, id) < (?, ?)}
 * comparison under {@code ORDER BY sold_at DESC, id DESC} - keyset paging, not OFFSET, so it stays
 * correct as new sales accrue. The wire form is base64url of {@code "<instant>|<uuid>"}; callers
 * treat it as opaque.
 */
public record SaleCursor(Instant soldAt, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode() {
        String raw = soldAt.toString() + '|' + id;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a cursor produced by {@link #encode()}; throws {@link InvalidCursorException} (400) on any malformation. */
    public static SaleCursor decode(String encoded) {
        try {
            String raw = new String(DECODER.decode(encoded), StandardCharsets.UTF_8);
            int sep = raw.lastIndexOf('|');
            if (sep < 0) {
                throw new InvalidCursorException();
            }
            Instant soldAt = Instant.parse(raw.substring(0, sep));
            UUID id = UUID.fromString(raw.substring(sep + 1));
            return new SaleCursor(soldAt, id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException();
        }
    }
}
