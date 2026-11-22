package com.shipit.backend.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    @Builder.Default
    private boolean success = false;

    private int status;

    private String errorCode;

    private String message;

    private List<ValidationError> errors;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static ApiError of(int status, String errorCode, String message) {
        return ApiError.builder()
                .success(false)
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static ApiError of(int status, String errorCode, String message, List<ValidationError> errors) {
        return ApiError.builder()
                .success(false)
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .errors(errors)
                .timestamp(Instant.now())
                .build();
    }
}
