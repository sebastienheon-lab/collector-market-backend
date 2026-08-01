package com.collectormarket.backend.ingestion;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.entities.ListingObservation;
import com.collectormarket.backend.repositories.ListingObservationRepository;
import com.collectormarket.backend.entities.PriceSnapshot;
import com.collectormarket.backend.repositories.PriceSnapshotRepository;

/**
 * §5.1.1 inferred-sale rule: a quantity_sold increase of N between consecutive fixed-price
 * observations of the same listing writes N {@code price_snapshot} rows at that observation's
 * ask price. A decrease, an unchanged value, or a listing's first-ever observation (nothing to
 * compare against) all mean "ignore" - including a listing that simply disappears, since that
 * never produces a new observation to trigger this detector in the first place.
 * <p>
 * Must run BEFORE the new observation is written to {@code listing_observation} - it looks up
 * "the latest existing row" as the prior observation, which only works while that row is still
 * the latest.
 */
@Component
public class InferredSaleDetector {

    private final ListingObservationRepository listingObservationRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;

    public InferredSaleDetector(ListingObservationRepository listingObservationRepository,
            PriceSnapshotRepository priceSnapshotRepository) {
        this.listingObservationRepository = listingObservationRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
    }

    public void detect(ObservationInput newObservation) {
        if (!"FIXED_PRICE".equals(newObservation.listingFormat()) || newObservation.quantitySold() == null) {
            return;
        }

        Integer priorQuantitySold = listingObservationRepository
                .findFirstByExternalListingIdOrderByObservedAtDesc(newObservation.externalListingId())
                .map(ListingObservation::getQuantitySold)
                .orElse(null);

        if (priorQuantitySold == null) {
            return;
        }

        int delta = newObservation.quantitySold() - priorQuantitySold;
        if (delta <= 0) {
            return;
        }

        for (int unit = 1; unit <= delta; unit++) {
            String syntheticExternalId = newObservation.externalListingId() + "#" + (priorQuantitySold + unit);
            priceSnapshotRepository.save(PriceSnapshot.inferredSale(
                    newObservation.cardId(), newObservation.askPrice(), newObservation.observedAt(),
                    newObservation.gradeSource(), newObservation.gradeValue(),
                    syntheticExternalId, newObservation.externalUrl(), newObservation.rawTitle()));
        }
    }
}
