package com.sap.dto.judger;
import lombok.Data;
@Data
public class ProblemSetActionRequest {
    private String status;
    private Boolean publicCode;
    private Long endsAt;
    private Long userId;
    private Boolean disqualified;
    private String reason;
}
