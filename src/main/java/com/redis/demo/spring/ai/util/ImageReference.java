package com.redis.demo.spring.ai.util;

import java.util.Objects;

/**
 * 图片引用类 - 用于关联图片与文本块
 * 基于Python实现 [3](#5-2)
 */
public class ImageReference {
    private String url;
    private int line;
    private String type; // "markdown" | "html"
    private String altText;

    public ImageReference(String url, int line, String type, String altText) {
        this.url = url;
        this.line = line;
        this.type = type;
        this.altText = altText;
    }

    // Getters
    public String getUrl() { return url; }
    public int getLine() { return line; }
    public String getType() { return type; }
    public String getAltText() { return altText; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ImageReference that = (ImageReference) obj;
        return line == that.line &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, line);
    }
}