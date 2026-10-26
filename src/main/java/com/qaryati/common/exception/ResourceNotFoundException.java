package com.qaryati.common.exception;

import org.springframework.modulith.NamedInterface;

@NamedInterface("errors")
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " with id " + id + " not found");
    }
}
