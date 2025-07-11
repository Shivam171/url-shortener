package com.shvmpk.url_shortener.service;


import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.shvmpk.url_shortener.dto.AnalyticsEvent;
import com.shvmpk.url_shortener.kafka.producer.KafkaAnalyticsProducer;
import com.shvmpk.url_shortener.model.ShortCode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import net.sf.uadetector.ReadableUserAgent;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final KafkaAnalyticsProducer kafkaAnalyticsProducer;
    private DatabaseReader geoDBReader;

    @PostConstruct
    public void init() throws Exception {
        initGeoDb();
        System.out.println("AnalyticsService initialized");
        System.out.println("KafkaAnalyticsProducer injected? " + (kafkaAnalyticsProducer != null));
    }

    private void initGeoDb() throws IOException {
        File database = new File("src/main/resources/GeoLite2-City.mmdb");
        geoDBReader = new DatabaseReader.Builder(database).build();
        System.out.println("========= GeoIP database loaded =========");
    }

    public void saveAnalyticsAsync(ShortCode mapping, String ip, HttpServletRequest request, ReadableUserAgent agent, String os) {
        String country = "Unknown", city = "Unknown", region = "Unknown", continent = "Unknown";
        Double latitude = null, longitude = null;

        try {
            InetAddress ipAddress = InetAddress.getByName(ip);
            CityResponse cityResponse = geoDBReader.city(ipAddress);

            country = cityResponse.getCountry().getName();
            city = cityResponse.getCity().getName();
            region = cityResponse.getMostSpecificSubdivision().getName();
            continent = cityResponse.getContinent().getName();

            if (cityResponse.getLocation() != null) {
                latitude = cityResponse.getLocation().getLatitude();
                longitude = cityResponse.getLocation().getLongitude();
            }
        } catch (Exception ignored) {}

        String deviceType = agent.getDeviceCategory().getName();
        String browser = agent.getName();
        boolean isBot = "Robot".equalsIgnoreCase(deviceType);
        String userAgent = request.getHeader("User-Agent");

        Instant now = Instant.now();
        LocalDate accessDate = LocalDate.now();

        Map<String, Integer> browserVisitCounts = Map.of(browser, 1);
        Map<String, Integer> deviceTypeVisitCounts = Map.of(deviceType, 1);
        Map<String, Integer> osVisitCounts = Map.of(os, 1);

        Map<String, Instant> browserLastSeen = Map.of(browser, now);
        Map<String, Instant> deviceLastSeen = Map.of(deviceType, now);

        String referer = request.getHeader("referer");
        String utmSource = request.getParameter("utm_source");
        String utmMedium = request.getParameter("utm_medium");
        String utmCampaign = request.getParameter("utm_campaign");
        String utmTerm = request.getParameter("utm_term");

        AnalyticsEvent event = AnalyticsEvent.builder()
                .shortCode(mapping.getShortCode())
                .accessDate(accessDate)
                .totalVisitCount(1)
                .browserVisitCounts(browserVisitCounts)
                .deviceTypeVisitCounts(deviceTypeVisitCounts)
                .osVisitCounts(osVisitCounts)
                .browserLastSeen(browserLastSeen)
                .deviceLastSeen(deviceLastSeen)
                .os(os)
                .deviceType(deviceType)
                .browser(browser)
                .country(country)
                .city(city)
                .region(region)
                .continent(continent)
                .latitude(latitude)
                .longitude(longitude)
                .referer(referer)
                .utmSource(utmSource)
                .utmMedium(utmMedium)
                .utmCampaign(utmCampaign)
                .utmTerm(utmTerm)
                .isBot(isBot)
                .userAgent(userAgent)
                .lastAccessTime(now)
                .build();

        kafkaAnalyticsProducer.sendAnalyticsEvent(event);
    }
}
