package com.collectormarket.backend.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.collectormarket.backend.catalog.GradeInfo;
import com.collectormarket.backend.catalog.GradeNormalizer;
import com.collectormarket.backend.ebay.EbayBrowseClient;
import com.collectormarket.backend.ebay.EbayCallBudgetExceededException;
import com.collectormarket.backend.ebay.EbayProperties;
import com.collectormarket.backend.ebay.dto.ItemDetail;
import com.collectormarket.backend.ebay.dto.ItemSearchResult;
import com.collectormarket.backend.ebay.dto.ItemSummary;
import com.collectormarket.backend.ebay.dto.ListingFormat;

/**
 * §5.1.1 active-listing poller. Assumes single-instance deployment; introduce ShedLock or
 * similar coordination if scaling horizontally.
 * <p>
 * Runs an hourly tick, but that tick is just a check-in frequency - which cards are actually
 * "due" is governed entirely by {@link CardTracker#selectDueForPoll} reading
 * {@code poller.daily_cadence_hours} from app_setting. A hardcoded outer cron can't honor a
 * runtime-editable setting; a frequent tick plus a per-card due check can, and settings changes
 * still propagate within {@link AppSettingService}'s 60s cache TTL instead of needing a restart.
 * <p>
 * Fixed-price listings get a follow-up {@code getItem} call for quantity-sold tracking; auctions
 * don't (§5.1.1 - the listing disappears at end and last observed bid underestimates the final
 * price, so auction inference is out of scope for MVP).
 */
@Component
public class Poller {

    private static final Logger log = LoggerFactory.getLogger(Poller.class);
    private static final String DAILY_CADENCE_SETTING = "poller.daily_cadence_hours";
    private static final int DEFAULT_DAILY_CADENCE_HOURS = 24;

    private final CardTracker cardTracker;
    private final AppSettingService appSettingService;
    private final EbayBrowseClient ebayBrowseClient;
    private final EbayProperties ebayProperties;
    private final InferredSaleDetector inferredSaleDetector;
    private final ListingObservationStore listingObservationStore;
    private final JdbcTemplate jdbcTemplate;

    public Poller(
            CardTracker cardTracker,
            AppSettingService appSettingService,
            EbayBrowseClient ebayBrowseClient,
            EbayProperties ebayProperties,
            InferredSaleDetector inferredSaleDetector,
            ListingObservationStore listingObservationStore,
            JdbcTemplate jdbcTemplate) {
        this.cardTracker = cardTracker;
        this.appSettingService = appSettingService;
        this.ebayBrowseClient = ebayBrowseClient;
        this.ebayProperties = ebayProperties;
        this.inferredSaleDetector = inferredSaleDetector;
        this.listingObservationStore = listingObservationStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void pollDueCards() {
        int dailyCadenceHours = appSettingService.getInt(DAILY_CADENCE_SETTING, DEFAULT_DAILY_CADENCE_HOURS);
        List<TrackedCard> due = cardTracker.selectDueForPoll(dailyCadenceHours);
        log.info("poller_tick dueCards={}", due.size());

        for (TrackedCard tracked : due) {
            try {
                pollCard(tracked.cardId());
                cardTracker.recordPolled(tracked.cardId());
            } catch (EbayCallBudgetExceededException e) {
                log.warn("poller_stopping reason=daily_budget_exceeded");
                break;
            } catch (Exception e) {
                log.warn("poller_card_failed cardId={} error={}", tracked.cardId(), e.toString());
            }
        }
    }

    private void pollCard(UUID cardId) {
        CardInfo cardInfo = lookupCardInfo(cardId);
        String query = buildSearchQuery(cardInfo);
        String categoryId = ebayProperties.category().tradingCardSingles();

        ItemSearchResult results = ebayBrowseClient.searchItems(query, categoryId).block();
        if (results == null) {
            return;
        }

        for (ItemSummary item : results.items()) {
            try {
                processItem(cardId, item);
            } catch (Exception e) {
                log.warn("poller_item_failed cardId={} itemId={} error={}", cardId, item.itemId(), e.toString());
            }
        }
    }

    private void processItem(UUID cardId, ItemSummary item) {
        GradeInfo grade = GradeNormalizer.extract(item.title());
        BigDecimal askPrice;
        Integer quantitySold;

        if (item.listingFormat() == ListingFormat.FIXED_PRICE) {
            ItemDetail detail = ebayBrowseClient.getItem(item.itemId()).block();
            askPrice = detail != null ? detail.price() : item.price();
            quantitySold = detail != null ? detail.estimatedSoldQuantity() : null;
        } else {
            askPrice = item.price();
            quantitySold = null;
        }

        ObservationInput observation = new ObservationInput(
                cardId, item.itemId(), Instant.now(), askPrice, item.listingFormat().name(), quantitySold,
                grade.gradeSource(), grade.gradeValue(), item.itemWebUrl(), item.title());

        // Must detect before inserting - the detector looks up "the current latest row" as prior.
        inferredSaleDetector.detect(observation);
        listingObservationStore.insert(observation);
    }

    private CardInfo lookupCardInfo(UUID cardId) {
        return jdbcTemplate.queryForObject(
                "SELECT player_name, year, brand, set_name FROM card WHERE id = ?",
                (rs, rowNum) -> new CardInfo(
                        rs.getString("player_name"), rs.getInt("year"), rs.getString("brand"), rs.getString("set_name")),
                cardId);
    }

    private String buildSearchQuery(CardInfo info) {
        StringBuilder query = new StringBuilder().append(info.year()).append(' ');
        if (info.brand() != null) {
            query.append(info.brand()).append(' ');
        }
        if (info.setName() != null) {
            query.append(info.setName()).append(' ');
        }
        query.append(info.playerName());
        return query.toString();
    }

    private record CardInfo(String playerName, Integer year, String brand, String setName) {
    }
}
