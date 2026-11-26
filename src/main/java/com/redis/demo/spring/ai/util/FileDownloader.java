package com.redis.demo.spring.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 专门处理文件下载的工具类
 */
public class FileDownloader {

    private static final Logger logger = LoggerFactory.getLogger(FileDownloader.class);

    // 使用一个示例数据URL替代原来的无效URL
    public static final String DATA_BEERS_JSON_GZ = "https://arxiv.org/pdf/2506.10380";

    /**
     * 下载文件到临时文件并返回Resource
     * 
     * @return 下载文件的Resource表示
     * @throws IOException 文件下载异常
     */
    public Resource downloadDataFile() throws IOException {
        String fileExtension;

        // 检查是否是arxiv.org的URL
        if (DATA_BEERS_JSON_GZ.contains("arxiv.org")) {
            // 对于arxiv.org链接，使用.pdf作为扩展名
            fileExtension = ".pdf";
        } else {
            // 从URL中提取文件扩展名作为后缀
            fileExtension = getFileExtension(DATA_BEERS_JSON_GZ);
            if (fileExtension == null || fileExtension.isEmpty()) {
                fileExtension = ".json"; // 默认后缀
            }
            fileExtension = "." + fileExtension;
        }
        Path tempFile = Files.createTempFile("beers-", fileExtension);
        logger.info("Downloading data file from: {} to: {}", DATA_BEERS_JSON_GZ, tempFile);

        URL downloadUrl = new URL(DATA_BEERS_JSON_GZ);
        URLConnection connection = downloadUrl.openConnection();

        // 设置连接超时
        connection.setConnectTimeout(10000); // 10s
        connection.setReadTimeout(60000); // 60s

        // 设置用户代理以避免被识别为自动化请求
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        connection.setRequestProperty("Accept", "application/json, text/plain, */*");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setRequestProperty("Upgrade-Insecure-Requests", "1");

        // 强制信任所有证书以解决SSL问题
        if (connection instanceof javax.net.ssl.HttpsURLConnection) {
            javax.net.ssl.HttpsURLConnection httpsConnection = (javax.net.ssl.HttpsURLConnection) connection;

            // 创建信任所有证书的TrustManager
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            // 创建SSL上下文并设置信任管理器
            try {
                javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                httpsConnection.setSSLSocketFactory(sc.getSocketFactory());

                // 设置主机名验证器以接受所有主机名
                httpsConnection.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                    public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                        return true;
                    }
                });
            } catch (Exception e) {
                logger.warn("Failed to configure SSL trust: " + e.getMessage());
            }
        }

        // 检查HTTP响应码
        if (connection instanceof java.net.HttpURLConnection) {
            java.net.HttpURLConnection httpConnection = (java.net.HttpURLConnection) connection;
            int responseCode = httpConnection.getResponseCode();

            if (responseCode >= 400) {
                logger.error("HTTP error response code: {}", responseCode);
                throw new IOException(
                        "Server returned HTTP response code: " + responseCode + " for URL: " + DATA_BEERS_JSON_GZ);
            }
        }

        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        logger.info("Data file downloaded successfully.");
        return new UrlResource(tempFile.toUri());
    }

    /**
     * 提取文件扩展名的辅助方法
     * 
     * @param url URL地址
     * @return 文件扩展名
     */
    private String getFileExtension(String url) {
        try {
            URL downloadUrl = new URL(url);
            String path = downloadUrl.getPath();
            int lastDotIndex = path.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < path.length() - 1) {
                return path.substring(lastDotIndex + 1);
            }
        } catch (Exception e) {
            logger.warn("Error parsing file extension from URL: {}", e.getMessage());
        }
        return null;
    }
}