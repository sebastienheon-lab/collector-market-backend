package com.collectormarket.backend.api;

import java.util.UUID;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.collectormarket.backend.api.dto.CardDetail;
import com.collectormarket.backend.api.dto.GradeBreakdownResponse;
import com.collectormarket.backend.api.dto.MarketView;
import com.collectormarket.backend.api.dto.PriceHistoryResponse;
import com.collectormarket.backend.api.dto.SearchResponse;
import com.collectormarket.backend.services.CardService;
import com.collectormarket.backend.services.GradeBreakdownService;
import com.collectormarket.backend.services.MarketService;
import com.collectormarket.backend.services.PriceHistoryService;
import com.collectormarket.backend.services.SearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Phase 1 read API (planning §8). All endpoints are under {@code /api/v1/cards}. Controllers stay
 * thin: bind + delegate to a service + return a DTO; validation failures and domain errors surface
 * as RFC 7807 problem responses via {@link com.collectormarket.backend.api.error.ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/cards")
@Validated
public class CardsController {

    private final SearchService searchService;
    private final CardService cardService;
    private final MarketService marketService;
    private final PriceHistoryService priceHistoryService;
    private final GradeBreakdownService gradeBreakdownService;

    public CardsController(
            SearchService searchService,
            CardService cardService,
            MarketService marketService,
            PriceHistoryService priceHistoryService,
            GradeBreakdownService gradeBreakdownService) {
        this.searchService = searchService;
        this.cardService = cardService;
        this.marketService = marketService;
        this.priceHistoryService = priceHistoryService;
        this.gradeBreakdownService = gradeBreakdownService;
    }

    @Operation(summary = "Search cards by player name (trigram), with optional year/card-number/sport filters")
    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String sport,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchService.search(q, sport, page, size);
    }

    @Operation(summary = "Card catalog detail")
    @GetMapping("/{cardId}")
    public CardDetail card(@PathVariable UUID cardId) {
        return cardService.getCard(cardId);
    }

    @Operation(summary = "Current market view (floor, median ask, active listings) for a grade")
    @GetMapping("/{cardId}/market")
    public MarketView market(
            @PathVariable UUID cardId,
            @RequestParam(defaultValue = "ALL") Grade grade) {
        return marketService.market(cardId, grade);
    }

    @Operation(summary = "Price history: separate sales and floor series, events, and empty-state related cards")
    @GetMapping("/{cardId}/prices")
    public PriceHistoryResponse prices(
            @PathVariable UUID cardId,
            @RequestParam(defaultValue = "90")
            @Schema(allowableValues = {"30", "90", "180", "365"}) int days,
            @RequestParam(defaultValue = "ALL") Grade grade,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return priceHistoryService.prices(cardId, grade, days, cursor, size);
    }

    @Operation(summary = "Average sale price per grade (grade premium spread)")
    @GetMapping("/{cardId}/grades")
    public GradeBreakdownResponse grades(
            @PathVariable UUID cardId,
            @RequestParam(defaultValue = "90")
            @Schema(allowableValues = {"30", "90", "180", "365"}) int days) {
        return gradeBreakdownService.breakdown(cardId, days);
    }
}
