package com.collectormarket.backend.api;

import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.collectormarket.backend.api.config.CacheConfig;
import com.collectormarket.backend.api.dto.CardDetail;
import com.collectormarket.backend.api.error.CardNotFoundException;
import com.collectormarket.backend.domain.Card;
import com.collectormarket.backend.domain.CardRepository;

/**
 * Card-detail lookups (§8.4) and the shared existence guard used by the other endpoints.
 * <p>
 * Reads through {@link CardRepository} (Spring Data). The whole persistence layer is now JPA -
 * simple reads/CRUD as JPQL/derived queries, and Postgres-specific analytics/search as native
 * {@code @Query} on repositories (no JdbcTemplate anywhere).
 */
@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    // Catalog rows are effectively immutable; a missing card throws, so 404s aren't cached.
    // requireExists (below) stays uncached - it guards the live market/prices endpoints.
    @Cacheable(cacheNames = CacheConfig.CARD_DETAIL, key = "#cardId")
    public CardDetail getCard(UUID cardId) {
        Card card = cardRepository.findWithSportById(cardId)
                .orElseThrow(() -> new CardNotFoundException(cardId));
        return toDetail(card);
    }

    /** Throws {@link CardNotFoundException} (404) if no such card - the guard for market/prices/grades. */
    public void requireExists(UUID cardId) {
        if (!cardRepository.existsById(cardId)) {
            throw new CardNotFoundException(cardId);
        }
    }

    private CardDetail toDetail(Card card) {
        return new CardDetail(
                card.getId(),
                card.getPlayerName(),
                card.getYear().intValue(),
                card.getBrand(),
                card.getSetName(),
                card.getCardNumber(),
                card.getSport().getCode(),
                card.isRookie(),
                card.getParallel(),
                card.getPrintRun());
    }
}
