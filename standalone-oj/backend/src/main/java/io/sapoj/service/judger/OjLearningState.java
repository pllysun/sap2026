package io.sapoj.service.judger;
import lombok.Data;
import java.util.*;

@Data
public class OjLearningState {
    private String trackId="all";
    private Plan plan;
    private List<Long> bookmarks=new ArrayList<>();
    private Map<String,String> notes=new LinkedHashMap<>();
    private Map<String,Review> reviews=new LinkedHashMap<>();
    public record Plan(String title,int dailyTarget,List<Integer> restDays,List<Long> problemIds,String startedOn) {}
    public record Review(int stage,String due) {}
}
