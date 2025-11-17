package com.redis.demo.spring.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用程序入口类，负责启动 Spring Boot 应用。
 */
@SpringBootApplication
public class RagApplication {

    /**
     * 启动方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }

}