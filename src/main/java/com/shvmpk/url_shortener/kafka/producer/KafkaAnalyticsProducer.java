package com.shvmpk.url_shortener.kafka.producer;

import com.shvmpk.url_shortener.dto.AnalyticsEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaAnalyticsProducer {
    private final KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;

    public void sendAnalyticsEvent(AnalyticsEvent event) {
        kafkaTemplate.send("analytics-events", event);
    }

    @PostConstruct
    public void logInit() {
        System.out.println("KafkaAnalyticsProducer initialized");
        System.out.println("KafkaTemplate injected? " + (kafkaTemplate != null));
    }
}
