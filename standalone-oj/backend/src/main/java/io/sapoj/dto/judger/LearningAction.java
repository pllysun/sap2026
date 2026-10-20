package io.sapoj.dto.judger;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class LearningAction {
    @NotNull @Min(0) private Long version;
    @NotBlank @Pattern(regexp="TRACK|PLAN|PLAN_CLEAR|BOOKMARK|NOTE|REVIEW") private String action;
    @Size(max=30) private String trackId;
    @Positive private Long problemId;
    private Boolean bookmarked;
    @Size(max=6000) private String text;
    @Size(max=80) private String title;
    @Min(1) @Max(10) private Integer dailyTarget;
    @Size(max=6) private List<@NotNull @Min(0) @Max(6) Integer> restDays;
    @Size(min=1,max=200) private List<@NotNull @Positive Long> problemIds;
    @Pattern(regexp="hard|ok|easy") private String ease;
}
