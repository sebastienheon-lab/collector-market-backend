package com.collectormarket.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code price_snapshot} (§7.3) - individual transactions (SOLD / INFERRED_SALE).
 * Schema owned by Flyway (V003); {@code ddl-auto: validate} checks this mapping. The {@code card_id}
 * FK is mapped as a raw {@link UUID} (not a {@code @ManyToOne}) to keep the set-based ingestion and
 * analytical read paths association-free. {@code created_at} is DB-defaulted and left unmapped.
 * {@code id} uses application-side UUID generation so JPA inserts don't need {@code RETURNING}.
 */
@Entity
@Table(name = "price_snapshot")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "sale_price", nullable = false)
    private BigDecimal salePrice;

    @Column(name = "sold_at", nullable = false)
    private Instant soldAt;

    @Column(name = "price_type", nullable = false)
    private String priceType;

    @Column(nullable = false)
    private String source;

    @Column(name = "grade_source")
    private String gradeSource;

    @Column(name = "grade_value")
    private String gradeValue;

    @Column(nullable = false)
    private String platform;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "raw_title")
    private String rawTitle;

    /**
     * Builds an inferred-sale snapshot (§5.1.1): a quantity-sold delta on a fixed-price eBay listing,
     * priced at the observed ask. Fixes {@code price_type}/{@code source}/{@code platform} to the
     * inferred-sale constants; the id is generated on insert.
     */
    public static PriceSnapshot inferredSale(UUID cardId, BigDecimal salePrice, Instant soldAt,
            String gradeSource, String gradeValue, String externalId, String externalUrl, String rawTitle) {
        PriceSnapshot snapshot = new PriceSnapshot();
        snapshot.cardId = cardId;
        snapshot.salePrice = salePrice;
        snapshot.soldAt = soldAt;
        snapshot.priceType = "INFERRED_SALE";
        snapshot.source = "EBAY_BROWSE_INFERRED";
        snapshot.platform = "EBAY";
        snapshot.gradeSource = gradeSource;
        snapshot.gradeValue = gradeValue;
        snapshot.externalId = externalId;
        snapshot.externalUrl = externalUrl;
        snapshot.rawTitle = rawTitle;
        return snapshot;
    }
}
