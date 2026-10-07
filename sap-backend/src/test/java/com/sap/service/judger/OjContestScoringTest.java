package com.sap.service.judger;

import com.sap.entity.judger.OjSubmission;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OjContestScoringTest {
    private final OjContestScoringService scoring=new OjContestScoringService();
    private OjSubmission job(long id,long item,long time,String kind,String status){
        var j=new OjSubmission();j.setId(id);j.setProblemSetItemId(item);j.setAcceptedAt(time);j.setKind(kind);j.setStatus(status);return j;
    }
    @Test void delayedEarlierWrongAnswerStillCountsAndDuplicateAcDoesNot(){
        long start=100_000;
        var score=scoring.score(true,start,start+10_000_000,Set.of(1L,2L),List.of(
            job(3,1,start+35*60_000,"SUBMIT","AC"),job(4,1,start+36*60_000,"SUBMIT","WA"),
            job(2,1,start+10*60_000,"SUBMIT","CE"),job(1,1,start+5*60_000,"SUBMIT","WA"),
            job(5,1,start+37*60_000,"SUBMIT","AC"),job(6,2,start+40*60_000,"SUBMIT","WA")));
        assertEquals(1,score.acCount());assertEquals(55,score.penalty());assertEquals(1,score.cells().get(1L).wrong());
        assertEquals(1,score.cells().get(2L).wrong());assertFalse(score.cells().get(2L).accepted());
        assertEquals(start+35*60_000,score.lastAcAt());
    }
    @Test void cutoffUsesReceivedTimeAndMinutesAreFloored(){
        long start=200_000,end=start+120_000;
        var score=scoring.score(true,start,end,Set.of(1L,2L,3L,4L),List.of(
            job(1,1,start-1,"SUBMIT","AC"),job(2,2,end,"SUBMIT","AC"),
            job(3,3,end-1,"SUBMIT","AC"),job(4,4,start,"SUBMIT","AC")));
        assertEquals(2,score.acCount());assertEquals(1,score.penalty());assertEquals(end-1,score.lastAcAt());
    }
    @Test void infrastructureAndSampleRunsNeverAddPenaltyOrAc(){
        var jobs=new ArrayList<OjSubmission>();long id=0;
        for(String v:List.of("CE","SYSTEM_ERROR","QUEUED","RUNNING"))jobs.add(job(++id,1,id*60_000,"SUBMIT",v));
        jobs.add(job(++id,1,300_000,"RUN","AC"));jobs.add(job(++id,1,310_000,"VALIDATE","AC"));
        for(String v:List.of("WA","TLE","MLE","RE","OLE"))jobs.add(job(++id,1,id*60_000,"SUBMIT",v));
        jobs.add(job(++id,1,720_000,"SUBMIT","AC"));jobs.add(job(++id,99,740_000,"SUBMIT","AC"));
        var score=scoring.score(true,0,900_000,Set.of(1L),jobs);
        assertEquals(1,score.acCount());assertEquals(112,score.penalty());assertEquals(5,score.cells().get(1L).wrong());assertEquals(2,score.cells().get(1L).pending());
    }
    @Test void rejudgeRecomputesOriginalRecordAndPracticeHasNoPenalty(){
        var earlier=job(1,1,60_000,"SUBMIT","WA");var later=job(2,1,120_000,"SUBMIT","AC");
        assertEquals(22,scoring.score(true,0,300_000,Set.of(1L),List.of(earlier,later)).penalty());
        earlier.setStatus("AC");earlier.setResultVersion(2);
        var after=scoring.score(true,0,300_000,Set.of(1L),List.of(earlier,later));
        assertEquals(1,after.penalty());assertEquals(60_000,after.lastAcAt());
        var practice=scoring.score(false,900_000,900_001,Set.of(1L),List.of(earlier,later));
        assertEquals(1,practice.acCount());assertEquals(0,practice.penalty());
    }
}
