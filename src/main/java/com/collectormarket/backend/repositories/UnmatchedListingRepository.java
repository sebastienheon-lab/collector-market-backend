package com.collectormarket.backend.repositories;

import com.collectormarket.backend.domain.*;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the {@link UnmatchedListing} triage queue. */
public interface UnmatchedListingRepository extends JpaRepository<UnmatchedListing, UUID> {

    long countByRawTitle(String rawTitle);
}
