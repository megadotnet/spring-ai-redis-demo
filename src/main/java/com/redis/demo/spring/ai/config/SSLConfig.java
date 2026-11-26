package com.redis.demo.spring.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;


import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Configuration
public class SSLConfig {

    @PostConstruct
    public void disableSslValidation() {
        // 创建信任所有证书的TrustManager
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // 信任所有客户端证书
                }
                
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // 信任所有服务器证书
                }
            }
        };

        try {
            // 安装信任所有证书的TrustManager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // 设置默认的SSL Socket Factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            
            // 创建主机名验证器，始终验证通过
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            
            // 安装主机名验证器
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}