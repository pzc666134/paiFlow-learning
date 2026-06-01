package com.iflytek.astron;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestMcpServerApplication {
    
    public static void main(String[] args) {
        SpringApplication.from(McpServerApplication::main).with(TestMcpServerApplication.class).run(args);
    }
}
