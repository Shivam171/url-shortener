package com.shvmpk.url_shortener.util;

import java.net.*;
import java.util.*;
import java.io.*;

public class UrlUtils {

    private UrlUtils() {} // prevent instantiation

    public static boolean isValid(String url) {
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static boolean isReachable(String urlStr) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            int responseCode = connection.getResponseCode();
            return (200 <= responseCode && responseCode < 400);
        } catch (IOException e) {
            return false;
        }
    }

    public static String normalize(String inputUrl) {
        try {
            URL url = new URL(inputUrl.trim());

            String protocol = url.getProtocol().toLowerCase();
            String host = url.getHost().toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);

            int port = url.getPort();
            String portPart = (port == -1 || port == url.getDefaultPort()) ? "" : ":" + port;

            String path = url.getPath().replaceAll("/+$", "");

            String query = url.getQuery();
            String sortedQuery = "";
            if (query != null) {
                List<String> params = Arrays.asList(query.split("&"));
                params.sort(String::compareTo);
                sortedQuery = String.join("&", params);
            }

            return protocol + "://" + host + portPart + path +
                    (sortedQuery.isEmpty() ? "" : "?" + sortedQuery);

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format for normalization");
        }
    }
}