package com.redis.demo.spring.ai.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * 从URL读取Markdown内容的工具类
 */
public class MarkdownUrlReader {
    
    private static final Logger logger = LoggerFactory.getLogger(MarkdownUrlReader.class);
    
    /**
     * 从指定URL获取Markdown内容字符串
     * @param urlString URL地址
     * @return Markdown内容字符串
     * @throws IOException 读取异常
     */
    public static String readMarkdownFromUrl(String urlString) throws IOException {
        logger.info("正在从URL获取Markdown内容: {}", urlString);
        
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        
        // 设置连接参数
        connection.setConnectTimeout(10000); // 10秒连接超时
        connection.setReadTimeout(30000); // 30秒读取超时
        connection.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        connection.setRequestProperty("Accept", "text/markdown, text/plain, */*");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        logger.info("成功从URL获取Markdown内容，内容长度: {}", content.length());
        return content.toString();
    }

}