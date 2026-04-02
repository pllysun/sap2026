package com.sap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.sap.mapper")
public class SapApplication {
    public static void main(String[] args) {
        SpringApplication.run(SapApplication.class, args);
    }
}
