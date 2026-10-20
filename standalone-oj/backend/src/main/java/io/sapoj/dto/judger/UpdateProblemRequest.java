package io.sapoj.dto.judger;

import lombok.Data;

@Data
public class UpdateProblemRequest {
    private Long revision;
    private ProblemPack pack;
}
