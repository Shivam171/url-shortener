package com.shvmpk.url_shortener;

import com.shvmpk.url_shortener.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
@EnableCaching
public class UrlShortenerApplication {
	public static void main(String[] args) {
		SpringApplication.run(UrlShortenerApplication.class, args);
	}
}
