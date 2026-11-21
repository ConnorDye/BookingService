package com.connor.bookingservice.service;

// Simple exceptions so a controller advice can map them to 404 / 409 later.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
