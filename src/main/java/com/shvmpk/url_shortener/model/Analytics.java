package com.shvmpk.url_shortener.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.shvmpk.url_shortener.util.JsonListInstantConverter;
import com.shvmpk.url_shortener.util.JsonMapConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"shortCode"})
@EqualsAndHashCode(exclude = {"shortCode"})
public class Analytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_code_id", nullable = false)
    @JsonBackReference
    private ShortCode shortCode;

    private LocalDate accessDate;

    private Integer totalVisitCount;

    @Convert(converter = JsonMapConverter.class)
    private Map<String, Integer> browserVisitCounts;

    @Convert(converter = JsonMapConverter.class)
    private Map<String, Integer> deviceTypeVisitCounts;

    @Convert(converter = JsonMapConverter.class)
    private Map<String, Integer> osVisitCounts;

    @Convert(converter = JsonMapConverter.class)
    private Map<String, Instant> browserLastSeen;

    @Convert(converter = JsonMapConverter.class)
    private Map<String, Instant> deviceLastSeen;

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

    @Convert(converter = JsonListInstantConverter.class)
    private List<Instant> recentAccessTimes;

    private Integer clicksLast10Min;
    private Integer clicksLast1Hour;

    private Instant lastAccessTime;

    @Version
    private Long version;
}
