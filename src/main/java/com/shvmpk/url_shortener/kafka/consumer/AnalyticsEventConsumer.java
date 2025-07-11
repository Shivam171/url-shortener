package com.shvmpk.url_shortener.kafka.consumer;

import com.shvmpk.url_shortener.dto.AnalyticsEvent;
import com.shvmpk.url_shortener.model.Analytics;
import com.shvmpk.url_shortener.repository.AnalyticsRepository;
import com.shvmpk.url_shortener.repository.UrlRepository;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@AllArgsConstructor
public class AnalyticsEventConsumer {

    private final AnalyticsRepository analyticsRepository;
    private final UrlRepository urlRepository;

    @KafkaListener(topics = "analytics-events", groupId = "analytics-group")
    public void consume(AnalyticsEvent event) {
        urlRepository.findByShortCodeIgnoreCase(event.getShortCode()).ifPresent(shortCode -> {
            Optional<Analytics> optional = analyticsRepository
                    .findByShortCodeAndAccessDate(shortCode, event.getAccessDate());

            Analytics analytics = optional.orElseGet(() -> Analytics.builder()
                    .shortCode(shortCode)
                    .accessDate(event.getAccessDate())
                    .browserVisitCounts(new HashMap<>())
                    .deviceTypeVisitCounts(new HashMap<>())
                    .osVisitCounts(new HashMap<>())
                    .browserLastSeen(new HashMap<>())
                    .deviceLastSeen(new HashMap<>())
                    .totalVisitCount(0)
                    .clicksLast10Min(0)
                    .clicksLast1Hour(0)
                    .build());

            // Merge counts
            mergeMapCount(analytics.getBrowserVisitCounts(), event.getBrowserVisitCounts());
            mergeMapCount(analytics.getDeviceTypeVisitCounts(), event.getDeviceTypeVisitCounts());
            mergeMapCount(analytics.getOsVisitCounts(), event.getOsVisitCounts());

            // Merge last seen
            mergeLastSeen(analytics.getBrowserLastSeen(), event.getBrowserLastSeen());
            mergeLastSeen(analytics.getDeviceLastSeen(), event.getDeviceLastSeen());

            // Merge numbers
            analytics.setTotalVisitCount(analytics.getTotalVisitCount() + event.getTotalVisitCount());

            // Add current access time
            Instant now = Instant.now();
            List<Instant> recent = analytics.getRecentAccessTimes();
            if (recent == null) {
                recent = new ArrayList<>();
            }

            // Add current access time
            recent.add(now);

            // Remove entries older than 1 hour
            recent.removeIf(ts -> ts.isBefore(now.minus(Duration.ofHours(1))));

            // Save updated list
            analytics.setRecentAccessTimes(recent);

            // Update counters
            analytics.setClicksLast10Min((int) recent.stream()
                    .filter(ts -> ts.isAfter(now.minus(Duration.ofMinutes(10))))
                    .count());

            analytics.setClicksLast1Hour(recent.size());

            // Merge metadata
            analytics.setCountry(event.getCountry());
            analytics.setCity(event.getCity());
            analytics.setRegion(event.getRegion());
            analytics.setContinent(event.getContinent());
            analytics.setLatitude(event.getLatitude());
            analytics.setLongitude(event.getLongitude());
            analytics.setReferer(event.getReferer());
            analytics.setUtmSource(event.getUtmSource());
            analytics.setUtmMedium(event.getUtmMedium());
            analytics.setUtmCampaign(event.getUtmCampaign());
            analytics.setUtmTerm(event.getUtmTerm());
            analytics.setIsBot(event.getIsBot());
            analytics.setUserAgent(event.getUserAgent());
            analytics.setLastAccessTime(event.getLastAccessTime());

            analyticsRepository.save(analytics);
        });
    }

    private void mergeMapCount(Map<String, Integer> existing, Map<String, Integer> incoming) {
        if (incoming == null) return;
        incoming.forEach((key, value) ->
                existing.merge(key, value, Integer::sum)
        );
    }

    private void mergeLastSeen(Map<String, Instant> existing, Map<String, Instant> incoming) {
        if (incoming == null) return;
        incoming.forEach((key, value) ->
                existing.merge(key, value, (oldVal, newVal) ->
                        newVal.isAfter(oldVal) ? newVal : oldVal)
        );
    }
}