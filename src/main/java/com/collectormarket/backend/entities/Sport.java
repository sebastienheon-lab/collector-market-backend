package com.collectormarket.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA mapping of the {@code sport} lookup table (§7.2). Flyway owns the schema; {@code ddl-auto:
 * validate} checks this mapping against it at boot. {@code id} is a manually-assigned SMALLINT
 * (seeded by V001), so no {@code @GeneratedValue}.
 * <p>
 * Lombok is limited to {@code @Getter}/{@code @Setter} + a protected no-arg constructor -
 * deliberately NOT {@code @Data}/{@code @ToString}/{@code @EqualsAndHashCode}, which are unsafe on
 * entities (they span associations and break entity-identity semantics).
 */
@Entity
@Table(name = "sport")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sport {

    @Id
    private Short id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "icon_key")
    private String iconKey;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
