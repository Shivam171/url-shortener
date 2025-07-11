package com.shvmpk.url_shortener.controller;

import com.shvmpk.url_shortener.model.Analytics;
import com.shvmpk.url_shortener.repository.AnalyticsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/analytics")
@CrossOrigin("*")
@Tag(name = "Analytics Management", description = "Endpoints for analytics")
public class AnalyticsController {
    private final AnalyticsRepository analyticsRepository;

    // GET /analytics?page=0&size=50 — returns all analytics
    @Operation(summary = "Fetch all analytics")
    @GetMapping
    public Page<Analytics> getAllAnalytics(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return analyticsRepository.findAll(PageRequest.of(page, size));
    }

    // GET /analytics/{shortCode} — analytics by shortCode
    @Operation(summary = "Fetch analytics by shortCode")
    @GetMapping("/{shortCode}")
    public List<Analytics> getAnalyticsByShortCode(@PathVariable String shortCode) {
        return analyticsRepository.findByShortCode_ShortCodeIgnoreCase(shortCode);
    }
}
