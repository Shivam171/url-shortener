package com.shvmpk.url_shortener.exception;

import org.springframework.http.HttpStatus;

public class InvalidPasswordException extends BaseApiException {
    public InvalidPasswordException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
