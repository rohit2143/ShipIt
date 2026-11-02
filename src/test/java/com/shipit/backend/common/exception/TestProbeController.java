package com.shipit.backend.common.exception;

import com.shipit.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestProbeController {

    @GetMapping("/success")
    public ResponseEntity<ApiResponse<String>> success() {
        return ResponseEntity.ok(ApiResponse.success("Operation completed successfully", "payload"));
    }

    @PostMapping("/validation")
    public ResponseEntity<ApiResponse<String>> testValidation(@Valid @RequestBody TestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(request.getName()));
    }

    @GetMapping("/not-found")
    public void testNotFound() {
        throw new ResourceNotFoundException("Project", "id", "proj_123");
    }

    @GetMapping("/bad-request")
    public void testBadRequest() {
        throw new BadRequestException("Invalid parameter value supplied");
    }

    @GetMapping("/conflict")
    public void testConflict() {
        throw new ConflictException("Project with name already exists");
    }

    @GetMapping("/server-error")
    public void testServerError() {
        throw new RuntimeException("Simulated unexpected database failure");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Email must be a valid address")
        @NotBlank(message = "Email is required")
        private String email;
    }
}
