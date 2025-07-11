package com.shvmpk.url_shortener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.shvmpk.url_shortener.config.AppProperties;
import com.shvmpk.url_shortener.dto.ShortUrlRequest;
import com.shvmpk.url_shortener.dto.ShortUrlResponse;
import com.shvmpk.url_shortener.dto.ShortUrlUpdateRequest;
import com.shvmpk.url_shortener.dto.ShortUrlUpdateResponse;
import com.shvmpk.url_shortener.exception.*;
import com.shvmpk.url_shortener.model.ShortCode;
import com.shvmpk.url_shortener.model.UrlVersion;
import com.shvmpk.url_shortener.repository.UrlRepository;
import com.shvmpk.url_shortener.repository.UrlVersionRepository;
import com.shvmpk.url_shortener.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.uadetector.ReadableUserAgent;
import net.sf.uadetector.UserAgentStringParser;
import net.sf.uadetector.service.UADetectorServiceFactory;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private final AppProperties appProperties;
    private final UrlRepository urlRepository;
    private final UrlVersionRepository urlVersionRepository;
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    private final RedisTemplate<String, String> redisTemplate;
    private final ShortUrlGenerator shortUrlGenerator;

    private final UserAgentStringParser parser = UADetectorServiceFactory.getResourceModuleParser();
    private final BloomFilter<String> bloomFilter = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 10000);

    @PostConstruct
    public void init() throws Exception {
        initBloomFilter();
    }

    private void initBloomFilter() {
        List<ShortCode> existing = urlRepository.findAll();
        for (ShortCode mapping : existing) {
            // Add short code and alias to bloom filter for quick lookup
            bloomFilter.put(mapping.getShortCode());
            if (mapping.getAlias() != null) {
                bloomFilter.put(mapping.getAlias());
            }
        }
        System.out.println("========= Bloom filter pre-filled with " + existing.size() + " entries =========");
    }

    // Resolve ShortUrl or Alias
    public ShortCode resolveShortCodeOrAlias(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Short code or alias must not be empty");
        }
        return urlRepository.findByShortCodeIgnoreCase(input)
                .or(() -> urlRepository.findByAliasIgnoreCase(input))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Short code or alias not found: " + input));
    }

    // Logic to generate short URL
    public ShortUrlResponse shortenUrl(ShortUrlRequest request) {
        // Step 1: Trim input URL to remove accidental whitespaces
        String inputUrl = request.getLongUrl().trim();
        log.info("Trimmed URL: {}", inputUrl);

        // Step 2: Validate URL format (syntax only
        if(!UrlUtils.isValid(inputUrl)) {
            throw new InvalidUrlException("Invalid URL format");
        }
        log.info("URL format validation passed");

        // Step 3: Check reachability
        if(!UrlUtils.isReachable(inputUrl)) {
            throw new InvalidUrlException("Unreachable URL");
        }
        log.info("URL reachability validation passed");

        // Step 4: Normalize
        log.info("Checking URL normalization");
        String normalizedUrl = UrlUtils.normalize(inputUrl);
        log.info("Normalized URL: {}", normalizedUrl);

        // Step 5: Alias validation
        log.info("Validating alias....");
        String customAlias = request.getAlias();
        boolean isCustomAliasProvided = customAlias != null && !customAlias.isEmpty();
        if (isCustomAliasProvided) {
            customAlias = customAlias.trim();
            if (customAlias.length() > 20)
                throw new InvalidAliasException("Alias too long");
            try {
                if (urlRepository.existsByAliasIgnoreCase(customAlias)) {
                    throw new UrlConflictException("Alias already exists");
                }
                log.info("Custom alias check passed");
            } catch (Exception e) {
                log.error("Error checking custom alias existence", e);
                throw new RuntimeException("Database error during alias validation", e);
            }
        }

        log.info("Processing password protection....");
        // Step 6: Password protection
        String passwordHash = null;
        String autoGeneratedPassword = null;
        Boolean isProtected = request.getIsProtected();
        Boolean isAuto = request.getIsPasswordAutoGenerated();

        if (Boolean.TRUE.equals(isProtected)) {
            if (Boolean.TRUE.equals(isAuto)) {
                autoGeneratedPassword = PasswordUtil.generateRandomPassword(8);
                passwordHash = PasswordUtil.hashPassword(autoGeneratedPassword);
            } else {
                String rawPassword = request.getPassword();
                if (rawPassword == null || rawPassword.trim().isEmpty()) {
                    throw new InvalidPasswordException("Password required for protection");
                }
                passwordHash = PasswordUtil.hashPassword(rawPassword);
            }
        }

        log.info("isProtected: {}", isProtected);
        log.info("isAuto: {}", isAuto);

        // Step 7: Expiration
        log.info("Processing expiration....");
        Integer maxClicks = request.getMaxClicks();
        String expiresAtStr = request.getExpiresAt();
        boolean isClickBased = maxClicks != null && maxClicks > 0;

        if (isClickBased && expiresAtStr != null && !expiresAtStr.trim().isEmpty()) {
            throw new InvalidExpirationException("Choose either click-based or time-based expiration");
        }

        LocalDateTime expiry = null;
        if (!isClickBased) {
            expiry = (expiresAtStr != null && !expiresAtStr.trim().isEmpty())
                    ? LocalDateTime.parse(expiresAtStr)
                    : LocalDateTime.now().plusDays(1);
        }

        log.info("isClickBased: {}", isClickBased);
        log.info("expiry: {}", expiry);

        // --------------------------
        // 🧠 CACHING AND LOOKUPS
        // --------------------------

        log.info("Processing caching and lookups....");
        String hash = DigestUtils.md5DigestAsHex(normalizedUrl.getBytes());
        String redisHashKey = "longUrl:" + hash;

        // Step 8: Redis → shortCode (always store canonical shortCode in cache)
        String cachedShortKey = redisTemplate.opsForValue().get(redisHashKey);
        if (cachedShortKey != null) {
            // Check if full object exists using shortKey
            String cachedJson = redisTemplate.opsForValue().get("shortKey:" + cachedShortKey);
            if (cachedJson != null) {
                try {
                    ShortCode cachedObject = objectMapper.readValue(cachedJson, ShortCode.class);
                    return buildResponseFromEntity(cachedObject);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize ShortUrl from Redis for key: {}", cachedShortKey, e);
                }
            }
        }

        log.info("redisHashKey: {}", redisHashKey);
        log.info("cachedShortKey: {}", cachedShortKey);

        // Step 9: DB lookup
        Optional<ShortCode> existing = urlRepository.findFirstByOriginalUrlIgnoreCase(normalizedUrl);
        if (existing.isPresent()) {
            ShortCode found = existing.get();
            String canonicalShortKey = found.getShortCode();
            log.info("Found existing ShortCode: {}", canonicalShortKey);

            // Cache using canonical shortKey
            redisTemplate.opsForValue().set(redisHashKey, canonicalShortKey, Duration.ofDays(1));
            try {
                String json = objectMapper.writeValueAsString(found);
                redisTemplate.opsForValue().set("shortKey:" + canonicalShortKey, json, Duration.ofDays(1));
                log.info("Saved ShortUrl to Redis for key: {}", canonicalShortKey);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize ShortUrl to JSON", e);
            }

            return buildResponseFromEntity(found);
        }

        // Step 10: Generate new shortKey
        String generatedShortCode = shortUrlGenerator.generate();
        log.info("generatedShortCode: {}", generatedShortCode);

        // Bloom filter + existence check for both shortCode and alias
        if (bloomFilter.mightContain(generatedShortCode)) {
            if (urlRepository.existsByShortCodeIgnoreCase(generatedShortCode)) {
                throw new UrlConflictException("Short code collision");
            }
        }

        if (isCustomAliasProvided && bloomFilter.mightContain(customAlias)) {
            if (urlRepository.existsByAliasIgnoreCase(customAlias)) {
                throw new UrlConflictException("Alias collision");
            }
        }

        try{
            // Step 11: Create the main ShortUrl entity first (without currentVersion)
            ShortCode mapping = ShortCode.builder()
                    .originalUrl(normalizedUrl)
                    .shortCode(generatedShortCode)
                    .alias(isCustomAliasProvided ? customAlias : null)
                    .expiresAt(expiry)
                    .isProtected(Boolean.TRUE.equals(isProtected))
                    .isPasswordAutoGenerated(isAuto)
                    .password(passwordHash)
                    .maxClicks(maxClicks)
                    .uniqueVisitorCount(0)
                    .isClickBased(isClickBased)
                    .build();

            log.info("ShortUrl mapping: {}", mapping);

            // Save the ShortUrl first to get the generated ID
            ShortCode savedMapping = urlRepository.save(mapping);

            // Create the initial version
            UrlVersion initialVersion = UrlVersion.builder()
                    .versionNumber(1)
                    .originalUrl(normalizedUrl)
                    .alias(isCustomAliasProvided ? customAlias : null)
                    .isProtected(Boolean.TRUE.equals(isProtected))
                    .isPasswordAutoGenerated(isAuto)
                    .password(passwordHash)
                    .maxClicks(maxClicks)
                    .expiresAt(expiry)
                    .shortCode(savedMapping)
                    .build();

            // Save th version
            UrlVersion savedVersion = urlVersionRepository.save(initialVersion);

            // Update the ShortCode with the current version reference
            savedMapping.setCurrentVersion(savedVersion);
            ShortCode finalMapping = urlRepository.save(savedMapping);

            // Cache using canonical shortCode (always use shortCode as key)
            redisTemplate.opsForValue().set(redisHashKey, generatedShortCode, Duration.ofDays(1));
            try {
                String json = objectMapper.writeValueAsString(finalMapping);
                redisTemplate.opsForValue().set("shortKey:" + generatedShortCode, json, Duration.ofDays(1));
            } catch (JsonProcessingException e) {
                log.warn("Failed to cache ShortUrl object", e);
            }

            // Add to Bloom filter
            bloomFilter.put(generatedShortCode);
            if(isCustomAliasProvided) {
                bloomFilter.put(customAlias);
            }

            return buildResponseFromEntity(finalMapping);
        } catch (Exception e) {
            log.error("Failed to create short URL with versioning", e);
            throw new RuntimeException("Failed to create short URL", e);
        }
    }

    // Update URL
    @Transactional
    public ShortUrlUpdateResponse updateUrl(String canonicalShortCode, ShortUrlUpdateRequest updatedData) {
        // Load existing mapping
        ShortCode shortCode = urlRepository.findByShortCodeIgnoreCase(canonicalShortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short code not found: " + canonicalShortCode));

        // Capture old alias (for cache eviction)
        String oldAlias = shortCode.getAlias();
        String oldShortCode = shortCode.getShortCode();

        // Compute new values
        String newOriginalUrl = updatedData.getLongUrl() != null
                ? updatedData.getLongUrl().trim()
                : shortCode.getOriginalUrl();

        String newAlias = updatedData.getAlias() != null
                ? updatedData.getAlias().trim()
                : shortCode.getAlias();

        Boolean newIsProtected = updatedData.getIsProtected() != null
                ? updatedData.getIsProtected()
                : shortCode.getIsProtected();

        Boolean newIsAuto = updatedData.getIsPasswordAutoGenerated() != null
                ? updatedData.getIsPasswordAutoGenerated()
                : shortCode.getIsPasswordAutoGenerated();

        // Handle password hashing
        String newPasswordHash = shortCode.getPassword();

        if (Boolean.TRUE.equals(newIsProtected)) {
            if (Boolean.TRUE.equals(newIsAuto)) {
                String autoPassword = PasswordUtil.generateRandomPassword(8);
                newPasswordHash = PasswordUtil.hashPassword(autoPassword);
            } else if (updatedData.getPassword() != null && !updatedData.getPassword().trim().isEmpty()) {
                newPasswordHash = PasswordUtil.hashPassword(updatedData.getPassword());
            }
        } else {
            newPasswordHash = null; // unprotected → remove password
        }

        // Expiration and clicks
        boolean isClickBased = updatedData.getMaxClicks() != null;
        LocalDateTime newExpiry = null;

        if (!isClickBased && updatedData.getExpiresAt() != null && !updatedData.getExpiresAt().trim().isEmpty()) {
            newExpiry = LocalDateTime.parse(updatedData.getExpiresAt());
        } else if (shortCode.getExpiresAt() != null && updatedData.getExpiresAt() == null) {
            newExpiry = shortCode.getExpiresAt(); // keep previous
        }

        Integer newMaxClicks = isClickBased ? updatedData.getMaxClicks() : shortCode.getMaxClicks();

        // Determine if anything changed
        boolean hasChanges = !shortCode.getOriginalUrl().equals(newOriginalUrl)
                || !equalsIgnoreCase(shortCode.getAlias(), newAlias)
                || !equalsNullable(shortCode.getIsProtected(), newIsProtected)
                || !equalsNullable(shortCode.getIsPasswordAutoGenerated(), newIsAuto)
                || !equalsNullable(shortCode.getPassword(), newPasswordHash)
                || !equalsNullable(shortCode.getMaxClicks(), newMaxClicks)
                || !equalsNullable(shortCode.getExpiresAt(), newExpiry);

        if (!hasChanges) {
            return buildUpdatedResponseFromEntity(shortCode);
        }

        // Evict old cache entries
        evictCacheEntry(oldShortCode);
        if (oldAlias != null && !oldAlias.equalsIgnoreCase(newAlias)) {
            evictCacheEntry(oldAlias.toLowerCase());
        }

        // Get the latest version number
        int latestVersion = urlVersionRepository.findLatestVersionNumber(shortCode.getId());

        // Check if we need to create a backup of the current state
        // Only create backup if this is the first time we're versioning OR
        // if the current state is different from the last saved version
        boolean shouldCreateBackup = false;

        if (latestVersion == 0) {
            // First time versioning - always create backup of original state
            shouldCreateBackup = true;
        } else {
            // Check if current state differs from last saved version
            UrlVersion lastVersion = urlVersionRepository.findByShortCodeIdAndVersionNumber(
                    shortCode.getId(), latestVersion);

            if (lastVersion != null) {
                boolean currentStateDiffersFromLastVersion =
                        !shortCode.getOriginalUrl().equals(lastVersion.getOriginalUrl())
                                || !equalsIgnoreCase(shortCode.getAlias(), lastVersion.getAlias())
                                || !equalsNullable(shortCode.getIsProtected(), lastVersion.getIsProtected())
                                || !equalsNullable(shortCode.getIsPasswordAutoGenerated(), lastVersion.getIsPasswordAutoGenerated())
                                || !equalsNullable(shortCode.getPassword(), lastVersion.getPassword())
                                || !equalsNullable(shortCode.getMaxClicks(), lastVersion.getMaxClicks())
                                || !equalsNullable(shortCode.getExpiresAt(), lastVersion.getExpiresAt());

                shouldCreateBackup = currentStateDiffersFromLastVersion;
            }
        }

        int nextVersionNumber = latestVersion + 1;

        // Save backup version if needed
        if (shouldCreateBackup) {
            UrlVersion backupVersion = UrlVersion.builder()
                    .shortCode(shortCode)
                    .versionNumber(nextVersionNumber)
                    .originalUrl(shortCode.getOriginalUrl())
                    .alias(shortCode.getAlias())
                    .isProtected(shortCode.getIsProtected())
                    .isPasswordAutoGenerated(shortCode.getIsPasswordAutoGenerated())
                    .password(shortCode.getPassword())
                    .maxClicks(shortCode.getMaxClicks())
                    .expiresAt(shortCode.getExpiresAt())
                    .rollbackFromVersion(null)
                    .isRollback(false)
                    .build();
            urlVersionRepository.save(backupVersion);
            nextVersionNumber++;
        }

        // Apply new values to the main entity
        shortCode.setOriginalUrl(newOriginalUrl);
        shortCode.setAlias(newAlias);
        shortCode.setIsProtected(newIsProtected);
        shortCode.setIsPasswordAutoGenerated(newIsAuto);
        shortCode.setPassword(newPasswordHash);
        shortCode.setMaxClicks(newMaxClicks);
        shortCode.setExpiresAt(newExpiry);
        shortCode.setIsClickBased(isClickBased);
        urlRepository.save(shortCode);

        // Save new version with updated data
        UrlVersion newVersion = UrlVersion.builder()
                .shortCode(shortCode)
                .versionNumber(nextVersionNumber)
                .originalUrl(newOriginalUrl)
                .alias(newAlias)
                .isProtected(newIsProtected)
                .isPasswordAutoGenerated(newIsAuto)
                .password(newPasswordHash)
                .maxClicks(newMaxClicks)
                .expiresAt(newExpiry)
                .rollbackFromVersion(null)
                .isRollback(false)
                .build();
        UrlVersion savedVersion = urlVersionRepository.save(newVersion);

        shortCode.setCurrentVersion(savedVersion);
        urlRepository.save(shortCode);

        // Refresh cache with updated values
        try {
            String serialized = objectMapper.writeValueAsString(shortCode);
            redisTemplate.opsForValue().set("shortKey:" + shortCode.getShortCode(), serialized, Duration.ofDays(1));
            if (newAlias != null) {
                redisTemplate.opsForValue().set("shortKey:" + newAlias.toLowerCase(), serialized, Duration.ofDays(1));
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize ShortCode for caching", e);
        }

        String redirectKey = "redirect:" + shortCode.getShortCode();
        Duration ttl = computeTtl(newExpiry);
        redisTemplate.opsForValue().set(redirectKey, shortCode.getOriginalUrl(), ttl);
        if (newAlias != null) {
            redisTemplate.opsForValue().set("redirect:" + newAlias.toLowerCase(), shortCode.getOriginalUrl(), ttl);
        }

        return buildUpdatedResponseFromEntity(shortCode);
    }

    private void evictCacheEntry(String key) {
        redisTemplate.delete("shortKey:" + key);
        redisTemplate.delete("redirect:" + key);
    }

    private Duration computeTtl(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            // no expiry → let Redis default (or set very long if you prefer)
            return Duration.ofDays(7);
        }
        Duration d = Duration.between(LocalDateTime.now(), expiresAt);
        return d.isNegative() ? Duration.ZERO : d;
    }

    // Building the updated response
    private ShortUrlUpdateResponse buildUpdatedResponseFromEntity(ShortCode entity) {
        String shortKey = (entity.getAlias() != null) ? entity.getAlias() : entity.getShortCode();
        String expiration = entity.getIsClickBased() != null && entity.getIsClickBased()
                ? "Expires after " + entity.getMaxClicks() + " clicks"
                : entity.getExpiresAt() != null ? "Expires at " + entity.getExpiresAt().toString() : "No expiration";
        String alias = entity.getAlias() != null ? appProperties.baseUrl() + "/" + entity.getAlias() : null;

        return ShortUrlUpdateResponse.builder()
                .originalUrl(entity.getOriginalUrl())
                .shortUrl(appProperties.baseUrl() + "/" + entity.getShortCode())
                .aliasUrl(alias)
                .qrCodeUrl(appProperties.baseUrl() + "/qr/" + shortKey)
                .isProtected(entity.getIsProtected())
                .isPasswordAutoGenerated(entity.getIsPasswordAutoGenerated())
                .password(entity.getPassword())
                .expiresAt(expiration)
                .maxClicksAllowed(entity.getMaxClicks())
                .currentVersion(entity.getCurrentVersion().getVersionNumber())
                .build();
    }

    // Building the response
    private ShortUrlResponse buildResponseFromEntity(ShortCode entity) {
        String shortKey = (entity.getAlias() != null) ? entity.getAlias() : entity.getShortCode();
        String expiration = entity.getIsClickBased() != null && entity.getIsClickBased()
                ? "Expires after " + entity.getMaxClicks() + " clicks"
                : entity.getExpiresAt() != null ? "Expires at " + entity.getExpiresAt().toString() : "No expiration";
        String alias = entity.getAlias() != null ? appProperties.baseUrl() + "/" + entity.getAlias() : null;
        return ShortUrlResponse.builder()
                .shortUrl(appProperties.baseUrl() + "/" + entity.getShortCode())
                .aliasUrl(alias)
                .qrCodeUrl(appProperties.baseUrl() + "/qr/" + shortKey)
                .expiresAt(expiration)
                .maxClicksAllowed(entity.getMaxClicks())
                .passwordProtected(entity.getIsProtected())
                .build();
    }

    // Redirect logic
    public String redirectUrl(String canonicalShortCode, HttpServletRequest request, HttpServletResponse response, boolean skipPasswordCheck) {
        if (canonicalShortCode == null || canonicalShortCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Short URL or alias cannot be empty");
        }

        canonicalShortCode = canonicalShortCode.trim().toLowerCase();
        ShortCode mapping = null;
        String cachedLongUrl = null;

        // 1. Check redirect cache first (using canonical shortCode)
        String redirectCacheKey = "redirect:" + canonicalShortCode;
        System.out.println("Redirect cache key: " + redirectCacheKey);
        cachedLongUrl = redisTemplate.opsForValue().get(redirectCacheKey);

        // 2. Redis full object cache (using canonical shortCode)
        if (mapping == null) {
            String fullCacheKey = "shortKey:" + canonicalShortCode;
            String fullObjectJson = redisTemplate.opsForValue().get(fullCacheKey);
            if (fullObjectJson != null) {
                try {
                    mapping = objectMapper.readValue(fullObjectJson, ShortCode.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to deserialize ShortCode from Redis: {}", canonicalShortCode, e);
                }
            }
        }

        // 3. Fallback to DB using canonical shortCode
        if (mapping == null) {
            mapping = urlRepository.findByShortCodeIgnoreCase(canonicalShortCode)
                    .orElseThrow(() -> new RuntimeException("URL not found"));
        }

        // 4. Expiration check
        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(LocalDateTime.now())) {
            urlRepository.delete(mapping);
            redisTemplate.delete("shortCode:" + canonicalShortCode);
            redisTemplate.delete("redirect:" + canonicalShortCode);
            throw new RuntimeException("URL expired");
        }

        // 5. Password check
        if (!skipPasswordCheck && mapping.getPassword() != null) {
            String suppliedPassword = request.getParameter("password");
            if (!PasswordUtil.verifyPassword(suppliedPassword, mapping.getPassword())) {
                throw new RuntimeException("Password required or incorrect");
            }
        }

        // 6. Max click limit check
        if (mapping.getMaxClicks() != null && mapping.getUniqueVisitorCount() >= mapping.getMaxClicks()) {
            throw new RuntimeException("Maximum click limit reached");
        }

        // 7. Redirect URL caching (if not already cached)
        if (cachedLongUrl == null) {
            String longUrl = mapping.getOriginalUrl();
            if (mapping.getExpiresAt() != null) {
                Duration ttl = Duration.between(LocalDateTime.now(), mapping.getExpiresAt());
                if (!ttl.isNegative()) {
                    redisTemplate.opsForValue().set(redirectCacheKey, longUrl, ttl);
                }
            } else {
                redisTemplate.opsForValue().set(redirectCacheKey, longUrl);
            }
            cachedLongUrl = longUrl;
        }

        // 8. Unique visitor logic
        String visitorId = getOrCreateVisitorId(request, response);
        String hashedSignature = DigestUtils.md5DigestAsHex(visitorId.getBytes());
        String visitorKey = "visitors:" + canonicalShortCode;
        boolean isNewVisitor = isNewVisitor(visitorKey, hashedSignature);

        if (isNewVisitor) {
            mapping.setUniqueVisitorCount(mapping.getUniqueVisitorCount() + 1);
            urlRepository.save(mapping);
        }

        // 9. Analytics (always)
        try {
            String ip = extractIp(request);
            String userAgentStr = request.getHeader("User-Agent");
            ReadableUserAgent agent = parser.parse(userAgentStr);
            String os = agent.getOperatingSystem().getName();

            analyticsService.saveAnalyticsAsync(mapping, ip, request, agent, os);
        } catch (Exception e) {
            log.error("Failed to track analytics for shortcode: {}", canonicalShortCode, e);
        }

        return cachedLongUrl != null ? cachedLongUrl : mapping.getOriginalUrl();
    }

    // Delete URL method
    public void deleteUrl(String canonicalShortCode) {
        ShortCode mapping = urlRepository.findByShortCodeIgnoreCase(canonicalShortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short code not found: " + canonicalShortCode));

        // Delete from database
        urlRepository.delete(mapping);

        // Clear all related caches
        String hash = DigestUtils.md5DigestAsHex(mapping.getOriginalUrl().getBytes());
        redisTemplate.delete("longUrl:" + hash);
        redisTemplate.delete("shortCode:" + canonicalShortCode);
        redisTemplate.delete("redirect:" + canonicalShortCode);
        redisTemplate.delete("visitors:" + canonicalShortCode);

        log.info("Deleted short URL: {}", canonicalShortCode);
    }

    // ------- Helpers -------

    private static final String VISITOR_COOKIE_NAME = "uid";

    // Get or create visitor ID
    private String getOrCreateVisitorId(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (VISITOR_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String uuid = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(VISITOR_COOKIE_NAME, uuid);
        cookie.setMaxAge(60 * 60 * 24 * 30);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        return uuid;
    }

    // Extract IP
    private String extractIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    // Check unique visitor
    private boolean isNewVisitor(String redisKey, String hashedSignature) {
        try {
            Long result = redisTemplate.opsForSet().add(redisKey, hashedSignature);
            boolean isNew = result != null && result == 1;
            if (isNew) {
                redisTemplate.expire(redisKey, Duration.ofDays(30));
            }
            return isNew;
        } catch (Exception e) {
            System.err.println("Redis error when checking unique visitor: " + e.getMessage());
            return false;
        }
    }

    private boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}