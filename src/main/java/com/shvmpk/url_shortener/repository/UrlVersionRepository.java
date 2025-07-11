package com.shvmpk.url_shortener.repository;

import com.shvmpk.url_shortener.model.ShortCode;
import com.shvmpk.url_shortener.model.UrlVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UrlVersionRepository extends JpaRepository<UrlVersion, Long> {
    List<UrlVersion> findByShortCodeOrderByVersionNumberDesc(ShortCode shortCode);

    UrlVersion findByShortCodeIdAndVersionNumber(Long shortCodeId, Integer versionNumber);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM UrlVersion v WHERE v.shortCode.id = :shortCodeId")
    Integer findLatestVersionNumber(@Param("shortCodeId") Long shortCodeId);

    Optional<UrlVersion> findByShortCodeAndVersionNumber(ShortCode shortCode, Integer versionNumber);

    void deleteByShortCodeAndVersionNumber(ShortCode shortCode, Integer versionNumber);
}
