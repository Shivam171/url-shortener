package com.shvmpk.url_shortener.exception;

import org.springframework.http.HttpStatus;

public class InvalidUrlException extends BaseApiException {
    public InvalidUrlException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}