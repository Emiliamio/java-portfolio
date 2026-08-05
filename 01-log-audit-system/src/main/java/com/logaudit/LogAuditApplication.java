package com.logaudit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.logaudit.mapper")
public class LogAuditApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogAuditApplication.class, args);
    }
}