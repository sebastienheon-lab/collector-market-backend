package com.collectormarket.backend.dto;

import java.util.List;

/**
 * A cursor-paginated slice of the {@code sales} series (§8.2). {@code nextCursor} is null on the
 * last page; otherwise pass it back as the {@code cursor} query param to fetch the next slice.
 */
public record SalesPage(
        List<Sale> items,
        String nextCursor) {
}
