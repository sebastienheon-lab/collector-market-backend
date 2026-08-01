package com.collectormarket.backend.repositories;

import com.collectormarket.backend.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AppSetting}, keyed by its {@code key}. */
public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
