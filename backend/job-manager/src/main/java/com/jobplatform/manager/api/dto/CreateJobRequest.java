package com.jobplatform.manager.api.dto;

import com.jobplatform.common.domain.JobType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateJobRequest(
        @NotBlank(message = "name is required")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        @NotNull(message = "jobType is required")
        JobType jobType,

        @NotBlank(message = "payload must not be empty")
        @Size(max = 10000, message = "payload must be at most 10000 characters")
        String payload,

        @Min(value = 1, message = "priority must be between 1 and 10")
        @Max(value = 10, message = "priority must be between 1 and 10")
        Integer priority,

        Instant scheduledAt,

        @Min(value = 0, message = "maxRetries must be between 0 and 10")
        @Max(value = 10, message = "maxRetries must be between 0 and 10")
        Integer maxRetries,

        @Min(value = 1, message = "timeoutSeconds must be between 1 and 3600")
        @Max(value = 3600, message = "timeoutSeconds must be between 1 and 3600")
        Integer timeoutSeconds,

        @Size(max = 100, message = "idempotencyKey must be at most 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9._:-]*$", message = "idempotencyKey may only contain letters, digits, '.', '_', ':' and '-'")
        String idempotencyKey) {
}
