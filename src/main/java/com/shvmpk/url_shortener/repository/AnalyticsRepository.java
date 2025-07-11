package com.shvmpk.url_shortener.repository;

import com.shvmpk.url_shortener.model.Analytics;
import com.shvmpk.url_shortener.model.ShortCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalyticsRepository extends JpaRepository<Analytics, Long> {
    List<Analytics> findByShortCode_ShortCodeIgnoreCase(String shortCode);
    Optional<Analytics> findByShortCodeAndAccessDate (ShortCode shortCode, LocalDate accessDate);
}
