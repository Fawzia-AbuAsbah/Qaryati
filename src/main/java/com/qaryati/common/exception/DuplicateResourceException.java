package com.qaryati.common.exception;

import org.springframework.modulith.NamedInterface;

@NamedInterface("errors")
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
