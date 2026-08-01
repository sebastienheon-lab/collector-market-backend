package com.collectormarket.backend.ingestion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.collectormarket.backend.domain.CardRepository;
import com.collectormarket.backend.ebay.EbayBrowseClient;
import com.collectormarket.backend.services.AppSettingService;
import com.collectormarket.backend.ebay.EbayProperties;
import com.collectormarket.backend.observability.JobMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Graceful shutdown lands on a card boundary: when a shutdown is signalled while a poll tick is
 * running, the poller finishes the current card and does not begin the remaining ones.
 */
@ExtendWith(MockitoExtension.class)
class PollerGracefulShutdownTest {

    @Mock private CardTracker cardTracker;
    @Mock private AppSettingService appSettingService;
    @Mock private EbayBrowseClient ebayBrowseClient;
    @Mock private InferredSaleDetector inferredSaleDetector;
    @Mock private ListingObservationStore listingObservationStore;
    @Mock private CardRepository cardRepository;

    private final EbayProperties ebayProperties = new EbayProperties(
            "id", "secret", "https://token", "https://api", 5000, 5000, 10000,
            new EbayProperties.Retry(3, 10, 100), new EbayProperties.Category("261328"));

    private Poller poller() {
        return new Poller(cardTracker, appSettingService, ebayBrowseClient, ebayProperties,
                inferredSaleDetector, listingObservationStore, cardRepository,
                new JobMetrics(new SimpleMeterRegistry()));
    }

    private static TrackedCard tracked() {
        return new TrackedCard(UUID.randomUUID(), CardTier.SEED, PollCadence.DAILY, null, null);
    }

    @Test
    void stopsAtNextCardBoundaryWhenShutdownRequestedMidTick() {
        Poller poller = poller();
        when(appSettingService.getInt(anyString(), anyInt())).thenReturn(24);
        when(cardTracker.selectDueForPoll(anyInt()))
                .thenReturn(List.of(tracked(), tracked(), tracked())); // three due

        // The first card's lookup flips the shutdown flag, then fails; the loop must break at the
        // next boundary rather than starting card two.
        doAnswer(inv -> {
            poller.onContextClosed(null);
            throw new RuntimeException("card one interrupted");
        }).when(cardRepository).findById(any());

        poller.pollDueCards();

        // Only card one was reached; cards two and three were never started.
        verify(cardRepository, times(1)).findById(any());
        verify(ebayBrowseClient, never()).searchItems(anyString(), anyString());
        verify(cardTracker, never()).recordPolled(any());
    }

    @Test
    void pollsEveryCardWhenNotShuttingDown() {
        Poller poller = poller();
        when(appSettingService.getInt(anyString(), anyInt())).thenReturn(24);
        when(cardTracker.selectDueForPoll(anyInt())).thenReturn(List.of(tracked(), tracked()));
        // Both cards fail harmlessly at lookup, but with no shutdown the loop still reaches both.
        doThrow(new RuntimeException("no card info")).when(cardRepository).findById(any());

        poller.pollDueCards();

        verify(cardRepository, times(2)).findById(any());
        verify(cardTracker, never()).recordPolled(any());
    }
}
