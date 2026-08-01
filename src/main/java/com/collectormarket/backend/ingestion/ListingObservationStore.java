package com.collectormarket.backend.ingestion;

import org.springframework.stereotype.Component;

import com.collectormarket.backend.domain.ListingObservationRepository;

@Component
public class ListingObservationStore {

    private final ListingObservationRepository listingObservationRepository;

    public ListingObservationStore(ListingObservationRepository listingObservationRepository) {
        this.listingObservationRepository = listingObservationRepository;
    }

    public void insert(ObservationInput observation) {
        listingObservationRepository.insertIfAbsent(
                observation.cardId(), observation.externalListingId(), observation.observedAt(),
                observation.askPrice(), observation.listingFormat(), observation.quantitySold(),
                observation.gradeSource(), observation.gradeValue(),
                observation.externalUrl(), observation.rawTitle());
    }
}
