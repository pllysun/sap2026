package com.sap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
@MapperScan("com.sap.mapper")
public class SapApplication {
    public static void main(String[] args) {
        // 固定时区为东八区，避免容器默认 UTC 导致 LocalDate.now()/LocalDateTime.now()
        // 在日期目录、24h 刷新判定、热力图等处出现 8 小时偏差
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(SapApplication.class, args);
    }
}
