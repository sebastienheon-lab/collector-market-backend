package com.collectormarket.backend.ebay;

import java.math.BigDecimal;
import java.util.List;

import com.collectormarket.backend.ebay.dto.ItemDetail;
import com.collectormarket.backend.ebay.dto.ItemSearchResult;
import com.collectormarket.backend.ebay.dto.ItemSummary;
import com.collectormarket.backend.ebay.dto.ListingFormat;
import com.collectormarket.backend.ebay.internal.EbayEstimatedAvailabilityRaw;
import com.collectormarket.backend.ebay.internal.EbayItemDetailRaw;
import com.collectormarket.backend.ebay.internal.EbayItemSummaryRaw;
import com.collectormarket.backend.ebay.internal.EbayPrice;
import com.collectormarket.backend.ebay.internal.EbaySearchResponse;

/** Maps eBay's raw JSON shape onto app-facing DTOs - services never see the eBay shape directly. */
final class EbayItemMapper {

    private EbayItemMapper() {
    }

    static ItemSearchResult toItemSearchResult(EbaySearchResponse raw) {
        List<ItemSummary> items = raw.itemSummaries() == null
                ? List.of()
                : raw.itemSummaries().stream().map(EbayItemMapper::toItemSummary).toList();
        return new ItemSearchResult(
                items,
                raw.total() != null ? raw.total() : 0L,
                raw.limit() != null ? raw.limit() : 0,
                raw.offset() != null ? raw.offset() : 0);
    }

    static ItemSummary toItemSummary(EbayItemSummaryRaw raw) {
        return new ItemSummary(
                raw.itemId(),
                raw.title(),
                parsePrice(raw.price()),
                raw.price() != null ? raw.price().currency() : null,
                raw.condition(),
                raw.itemWebUrl(),
                raw.seller() != null ? raw.seller().username() : null,
                toListingFormat(raw.buyingOptions()));
    }

    static ItemDetail toItemDetail(EbayItemDetailRaw raw) {
        EbayEstimatedAvailabilityRaw availability = raw.estimatedAvailabilities() == null
                || raw.estimatedAvailabilities().isEmpty()
                        ? null
                        : raw.estimatedAvailabilities().get(0);
        return new ItemDetail(
                raw.itemId(),
                raw.title(),
                parsePrice(raw.price()),
                raw.price() != null ? raw.price().currency() : null,
                raw.condition(),
                raw.itemWebUrl(),
                raw.seller() != null ? raw.seller().username() : null,
                toListingFormat(raw.buyingOptions()),
                availability != null ? availability.estimatedAvailableQuantity() : null,
                availability != null ? availability.estimatedSoldQuantity() : null);
    }

    private static BigDecimal parsePrice(EbayPrice price) {
        return price != null && price.value() != null ? new BigDecimal(price.value()) : null;
    }

    private static ListingFormat toListingFormat(List<String> buyingOptions) {
        if (buyingOptions == null || buyingOptions.isEmpty()) {
            return ListingFormat.OTHER;
        }
        if (buyingOptions.contains("FIXED_PRICE")) {
            return ListingFormat.FIXED_PRICE;
        }
        if (buyingOptions.contains("AUCTION")) {
            return ListingFormat.AUCTION;
        }
        return ListingFormat.OTHER;
    }
}
