package com.shipit.backend.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchEnvironmentVariablesRequest {

    @NotEmpty(message = "Environment variables list must not be empty")
    @Valid
    private List<EnvironmentVariableRequest> variables;
}
