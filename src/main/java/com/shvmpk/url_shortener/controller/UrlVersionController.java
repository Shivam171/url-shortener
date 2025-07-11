package com.shvmpk.url_shortener.controller;

import com.shvmpk.url_shortener.dto.UrlVersionResponse;
import com.shvmpk.url_shortener.model.ShortCode;
import com.shvmpk.url_shortener.repository.UrlVersionRepository;
import com.shvmpk.url_shortener.service.UrlService;
import com.shvmpk.url_shortener.service.UrlVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/versions")
@CrossOrigin("*")
@Tag(name = "URL Versions Management", description = "Operations with URL versions")
public class UrlVersionController {
    private final UrlService urlService;
    private final UrlVersionService urlVersionService;
    private final UrlVersionRepository urlVersionRepository;

    @Operation(summary = "Fetch all versions")
    @GetMapping("/{shortCodeOrAlias}")
    public ResponseEntity<List<UrlVersionResponse>> getAllVersions(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.getVersionsByShortCodeOrAlias(mapping));
    }

    @Operation(summary = "Rollback to version")
    @PostMapping("/{shortCodeOrAlias}/rollback-to/{versionNumber}")
    public ResponseEntity<UrlVersionResponse> rollbackToVersion(
            @PathVariable String shortCodeOrAlias,
            @PathVariable Integer versionNumber
    ) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return ResponseEntity.ok(urlVersionService.rollbackToVersion(mapping, versionNumber));
    }

    @Operation(summary = "Fetch current version")
    @GetMapping("/{shortCodeOrAlias}/current")
    public ResponseEntity<UrlVersionResponse> getCurrentVersion(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        UrlVersionResponse version = urlVersionService.getCurrentVersion(mapping);
        return ResponseEntity.ok(version);
    }

    @Operation(summary = "Compare versions")
    @GetMapping("/{shortCodeOrAlias}/compare")
    public ResponseEntity<Map<String, Map<String, Object>>> compareVersions(
            @PathVariable String shortCodeOrAlias,
            @RequestParam Integer from,
            @RequestParam Integer to
    ) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        
        Map<String, Map<String, Object>> diff = urlVersionService.compareVersions(mapping, from, to);
        return ResponseEntity.ok(diff);
    }

    @Operation(summary = "Delete specific version")
    @DeleteMapping("/{shortCodeOrAlias}/version/{versionNumber}")
    public String deleteSpecificVersion(@PathVariable String shortCodeOrAlias, @PathVariable Integer versionNumber) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        return urlVersionService.deleteSpecificVersionByShortCodeOrAlias (mapping, versionNumber);
    }
}