package com.sap.config.judger;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data @Component @ConfigurationProperties(prefix="judger")
public class JudgerProperties {
    private boolean enabled = false;
    private String endpoint = "http://127.0.0.1:5050";
    private String tokenFile = "/run/judger/token";
    private String agentEndpoint = "http://127.0.0.1:5051";
    private String nodeKeyFile = "./data/judger-node.key";
    private String runtimeId = "go-judge-e9d70a0-gcc15.3-jdk27-python3.14.7-rust1.98.1-v1";
    private int queueCapacity = 50;
    private long offlineWaitMs = 120000;
    private long maxJudgeGroupMs = 600000;
    private int dailySubmissionLimit = 0;
}
