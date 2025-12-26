package com.redis.demo.spring.ai.util;

import lombok.Data;

@Data
public class MarkdownElement {
    private String type;
    private String content;
    private int startLine;
    private int endLine;

    public MarkdownElement(String type, String content, int startLine, int endLine) {
        this.type = type;
        this.content = content;
        this.startLine = startLine;
        this.endLine = endLine;
    }

    // getters...
}
