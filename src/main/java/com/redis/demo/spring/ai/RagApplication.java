package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.util.SSLUtil;
import com.redis.demo.spring.ai.config.SSLConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(SSLConfig.class)  // 导入SSL配置
@ConfigurationPropertiesScan  // 启用 @ConfigurationProperties 扫描（AgentLimits 等）
public class RagApplication {

    public static void main(String[] args) {
        // 在应用启动时信任所有SSL证书，解决证书验证问题
        SSLUtil.trustAllCertificates();
        SpringApplication.run(RagApplication.class, args);
    }

}