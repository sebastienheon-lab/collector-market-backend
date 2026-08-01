package com.collectormarket.backend.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link PriceSnapshot} (SOLD / INFERRED_SALE transactions). */
public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, UUID> {

    /** Snapshots whose external_id starts with the given prefix (inferred-sale synthetic ids). */
    List<PriceSnapshot> findByExternalIdStartingWith(String externalIdPrefix);

    /**
     * §5.4 retention: drop the raw-listing linkback fields on snapshots older than the cutoff,
     * touching only rows that still carry any linkback. Returns the row count.
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE PriceSnapshot ps
            SET ps.externalUrl = NULL, ps.rawTitle = NULL, ps.externalId = NULL
            WHERE ps.soldAt < :cutoff
              AND (ps.externalUrl IS NOT NULL OR ps.rawTitle IS NOT NULL OR ps.externalId IS NOT NULL)
            """)
    int nullifyLinkbacksSoldBefore(@Param("cutoff") Instant cutoff);
}
