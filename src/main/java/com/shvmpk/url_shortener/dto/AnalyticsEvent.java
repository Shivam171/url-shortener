package com.shvmpk.url_shortener.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsEvent implements Serializable {
    private String shortCode;
    private LocalDate accessDate;

    private Integer totalVisitCount;

    private Map<String, Integer> browserVisitCounts;
    private Map<String, Integer> deviceTypeVisitCounts;
    private Map<String, Integer> osVisitCounts;

    private Map<String, Instant> browserLastSeen;
    private Map<String, Instant> deviceLastSeen;

    private String os;
    private String deviceType;
    private String browser;

    private String country;
    private String city;
    private String region;
    private String continent;
    private Double latitude;
    private Double longitude;

    private String referer;
    private String utmSource;
    private String utmMedium;
    private String utmCampaign;
    private String utmTerm;

    private Boolean isBot;
    private String userAgent;

    private List<Instant> recentAccessTimes;

    private Integer clicksLast10Min;
    private Integer clicksLast1Hour;

    private Instant lastAccessTime;
}