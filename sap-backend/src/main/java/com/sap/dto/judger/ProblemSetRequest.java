package com.sap.dto.judger;

import lombok.Data;
import java.util.*;

@Data
public class ProblemSetRequest {
    private String name;
    private String description="";
    private String mode="PRACTICE";
    private String accessType="PUBLIC";
    private List<String> students=new ArrayList<>();
    private List<String> languages=new ArrayList<>(List.of("c","cpp","java","python","rust"));
    private List<Item> items=new ArrayList<>();
    private Long startsAt;
    private Long endsAt;
    private Long revision;
    @Data public static class Item {
        private Long problemId;
        private List<String> modes=new ArrayList<>();
    }
}
