package com.sap.service.judger;

import com.sap.entity.judger.OjSubmission;
import com.sap.vo.judger.ProblemSetScore;
import org.springframework.stereotype.Service;
import java.util.*;

/** Recomputable from durable submissions; judge completion order never changes scoring. */
@Service
public class OjContestScoringService {
    private static final Set<String> PENALIZED=Set.of("WA","TLE","MLE","RE","OLE");
    public ProblemSetScore score(boolean contest, long startsAt, long endsAt, Set<Long> itemIds, List<OjSubmission> jobs) {
        Map<Long,List<OjSubmission>> byItem=new HashMap<>();
        jobs.stream().filter(s->"SUBMIT".equals(s.getKind()) && s.getAcceptedAt()!=null && itemIds.contains(s.getProblemSetItemId()))
            .filter(s->!contest || s.getAcceptedAt()>=startsAt && s.getAcceptedAt()<endsAt)
            .sorted(Comparator.comparing(OjSubmission::getAcceptedAt).thenComparing(OjSubmission::getId))
            .forEach(s->byItem.computeIfAbsent(s.getProblemSetItemId(),k->new ArrayList<>()).add(s));
        Map<Long,ProblemSetScore.Cell> cells=new LinkedHashMap<>();int ac=0;long penalty=0,last=0;
        for(Long id:itemIds) {
            int wrong=0,pending=0;Long accepted=null;
            for(OjSubmission s:byItem.getOrDefault(id,List.of())) {
                if("QUEUED".equals(s.getStatus())||"RUNNING".equals(s.getStatus()))pending++;
                if(accepted!=null)continue;
                if("AC".equals(s.getStatus()))accepted=s.getAcceptedAt();
                else if(PENALIZED.contains(s.getStatus()))wrong++;
            }
            long elapsed=accepted==null||!contest?0:Math.max(0,(accepted-startsAt)/60000);
            if(accepted!=null){ac++;last=Math.max(last,accepted);if(contest)penalty+=elapsed+20L*wrong;}
            cells.put(id,new ProblemSetScore.Cell(accepted!=null,wrong,pending,accepted,elapsed));
        }
        return new ProblemSetScore(ac,penalty,last,cells);
    }
}
