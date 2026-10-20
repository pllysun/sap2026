package io.sapoj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication @EnableScheduling @MapperScan("io.sapoj.mapper")
public class OjApplication {
    public static void main(String[] args) { SpringApplication.run(OjApplication.class, args); }
}
