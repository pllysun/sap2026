package com.sap.dto.judger;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data public class NodeRequest {
    @NotBlank @Size(max=80) private String name;
    @NotBlank @Size(max=500) private String endpoint;
    @Size(max=256) private String token;
    @NotNull @Min(1) @Max(32) private Integer maxConcurrency=1;
}
