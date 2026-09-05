package com.codeorbit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class CodeOrbitApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeOrbitApplication.class, args);
    }

    @RestController
    @RequestMapping("/api/system")
    static class SystemController {
        @GetMapping("/health")
        public HealthResponse health() {
            return new HealthResponse("UP", "星码空间后端已启动", "第一阶段骨架");
        }
    }

    record HealthResponse(String status, String message, String stage) {}
}
