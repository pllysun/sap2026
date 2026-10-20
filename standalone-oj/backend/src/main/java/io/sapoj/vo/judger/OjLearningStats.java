package io.sapoj.vo.judger;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class OjLearningStats {
    private Long problemId;
    private Integer attempts;
    private Integer acAttempts;
    private LocalDateTime firstAcceptedAt;
    private LocalDateTime lastSubmittedAt;
}
