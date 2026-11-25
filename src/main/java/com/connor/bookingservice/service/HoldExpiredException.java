package com.connor.bookingservice.service;

public class HoldExpiredException extends RuntimeException{
    public HoldExpiredException(String message) {
        super(message);
    }
}
