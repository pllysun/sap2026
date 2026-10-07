package com.sap.dto.judger;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SubmitRequest {
    @NotNull private Long problemId;
    @NotBlank @Size(max=16) private String language;
    @NotBlank @Size(max=16) private String mode;
    @NotBlank @Size(max=65536) private String code;
    /** null runs published samples; a string runs this single custom input. */
    @Size(max=1048576) private String input;
    /** Only problem-set routes accept this key; a retry must retain the same payload. */
    @Size(max=64) private String requestKey;
}
