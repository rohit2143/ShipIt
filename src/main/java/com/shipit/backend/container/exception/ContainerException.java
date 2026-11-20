package com.shipit.backend.container.exception;

import com.shipit.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an error occurs during container lifecycle management,
 * port allocation, startup, or health checking.
 */
public class ContainerException extends AppException {

    public ContainerException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "CONTAINER_ERROR");
    }

    public ContainerException(String message, String errorCode) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, errorCode);
    }
}
