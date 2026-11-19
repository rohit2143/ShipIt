package com.shipit.backend.build.exception;

import com.shipit.backend.common.exception.AppException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an error occurs during repository cloning, project detection,
 * Dockerfile synthesis, or container image builds.
 */
public class BuildException extends AppException {

    public BuildException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "BUILD_FAILED");
    }

    public BuildException(String message, String errorCode) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, errorCode);
    }
}
