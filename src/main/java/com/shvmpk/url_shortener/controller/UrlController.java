package com.shvmpk.url_shortener.controller;

import com.shvmpk.url_shortener.dto.ShortUrlRequest;
import com.shvmpk.url_shortener.dto.ShortUrlResponse;
import com.shvmpk.url_shortener.dto.ShortUrlUpdateRequest;
import com.shvmpk.url_shortener.dto.ShortUrlUpdateResponse;
import com.shvmpk.url_shortener.model.ShortCode;
import com.shvmpk.url_shortener.service.SecureCookieService;
import com.shvmpk.url_shortener.service.UrlService;
import com.shvmpk.url_shortener.util.PasswordUtil;
import com.shvmpk.url_shortener.util.ShortUrlUpdateValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
@RequestMapping("/urls")
@Validated
@CrossOrigin("*")
@Tag(name = "URL Management", description = "Endpoints for managing URLs")
public class UrlController {
    private final UrlService urlService;
    private final SecureCookieService secureCookieService;

    @Operation(summary = "Create a shorten URL")
    @PostMapping("/shorten")
    @ResponseBody
    public ResponseEntity<ShortUrlResponse> shortenUrl(@Valid @RequestBody ShortUrlRequest request) {
        ShortUrlResponse response = urlService.shortenUrl(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Check if protected")
    @GetMapping("/{shortCodeOrAlias}")
    public String checkIfProtected(
            @PathVariable String shortCodeOrAlias,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        String canonicalShortCode = mapping.getShortCode();

        boolean isProtected = Boolean.TRUE.equals(mapping.getIsProtected());
        boolean isVerified = Boolean.TRUE.equals(request.getSession().getAttribute("verified_" + canonicalShortCode));
        String source = null;

        if (!isVerified && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (("verified_" + canonicalShortCode).equals(cookie.getName()) &&
                        secureCookieService.matchesDecryption(cookie.getValue(), "true")) {
                    isVerified = true;
                    source = "COOKIE";
                    break;
                }
            }
        } else if (isVerified) {
            source = "SESSION";
        }

        // If protected + verified => show already-verified page
        if (isProtected && isVerified) {
            String targetUrl = urlService.redirectUrl(canonicalShortCode, request, response, true);
            model.addAttribute("targetUrl", targetUrl);
            model.addAttribute("verifiedSource", source);
            model.addAttribute("originalInput", shortCodeOrAlias); // Keep original for display
            return "already-verified";
        }

        // If not protected => redirect directly (no password check required)
        if (!isProtected) {
            String targetUrl = urlService.redirectUrl(canonicalShortCode, request, response, true);
            return "redirect:" + targetUrl;
        }

        // If protected but not verified => show verify page
        // Use canonical shortCode for internal processing, but show original input to user
        model.addAttribute("alias", canonicalShortCode);
        model.addAttribute("originalInput", shortCodeOrAlias);
        return "verify";
    }

    @Operation(summary = "Verify password")
    @PostMapping("/{shortCodeOrAlias}/verify")
    public String verifyPassword(
            @PathVariable String shortCodeOrAlias,
            @RequestParam String password,
            @RequestParam(required = false) String remember,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            Model model
    ) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        String canonicalShortCode = mapping.getShortCode();

        if (!Boolean.TRUE.equals(mapping.getIsProtected())) {
            return "redirect:" + urlService.redirectUrl(canonicalShortCode, request, response, true);
        }

        // Retry check
        Integer attempts = (Integer) session.getAttribute("attempts:" + canonicalShortCode);
        Instant last = (Instant) session.getAttribute("lastAttempt:" + canonicalShortCode);
        if (attempts == null) attempts = 0;

        if (last != null && Duration.between(last, Instant.now()).toHours() < 6 && attempts >= 3) {
            model.addAttribute("alias", shortCodeOrAlias);
            model.addAttribute("canonicalShortCode", canonicalShortCode);
            model.addAttribute("error", "Too many failed attempts. Try again after 6 hours.");
            return "verify";
        }

        if (!PasswordUtil.verifyPassword(password, mapping.getPassword())) {
            attempts++;
            session.setAttribute("attempts:" + canonicalShortCode, attempts);
            session.setAttribute("lastAttempt:" + canonicalShortCode, Instant.now());

            model.addAttribute("alias", shortCodeOrAlias);
            model.addAttribute("canonicalShortCode", canonicalShortCode);
            model.addAttribute("error", "Incorrect password. Attempt " + attempts + " of 3.");
            return "verify";
        }

        // Success: set session and cookie (if "remember me" checked)
        session.setAttribute("verified_" + canonicalShortCode, true);
        session.removeAttribute("attempts:" + canonicalShortCode);
        session.removeAttribute("lastAttempt:" + canonicalShortCode);

        if (remember != null) {
            // Set encrypted cookie
            Cookie cookie = secureCookieService.createCookie(
                    "verified_" + canonicalShortCode,
                    "true",
                    "/urls/" + shortCodeOrAlias, // Keep original path for user experience
                    60 * 60 * 24 * 7
            );
            response.addCookie(cookie);
        } else {
            // Only set session if "remember me" is NOT checked
            session.setAttribute("verified_" + canonicalShortCode, true);
        }

        String targetUrl = urlService.redirectUrl(canonicalShortCode, request, response, true);
        return "redirect:" + targetUrl;
    }

    @Operation(summary = "Update short URL")
    @PatchMapping("/{shortCodeOrAlias}")
    @ResponseBody
    public ResponseEntity<ShortUrlUpdateResponse> updateShortUrl(
            @PathVariable String shortCodeOrAlias,
            @Valid @RequestBody ShortUrlUpdateRequest updatedData
    ) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        String canonicalShortCode = mapping.getShortCode();

        ShortUrlUpdateValidator.validate(updatedData);
        ShortUrlUpdateResponse response = urlService.updateUrl(canonicalShortCode, updatedData);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete short URL")
    @DeleteMapping("/{shortCodeOrAlias}")
    public String deleteShortUrl(@PathVariable String shortCodeOrAlias) {
        ShortCode mapping = urlService.resolveShortCodeOrAlias(shortCodeOrAlias);
        urlService.deleteUrl(mapping.getShortCode());
        return "URL deleted";
    }
}
