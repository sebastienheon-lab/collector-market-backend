package com.collectormarket.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of {@code app_setting} (§7.12) - runtime-tunable operational knobs, keyed by
 * {@code key}. Schema + seed rows owned by Flyway (V009). Read-only in practice (values are edited
 * out-of-band); {@code updated_at} is DB-defaulted and left unmapped.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppSetting {

    @Id
    @Column(name = "key")
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_by")
    private String updatedBy;
}
