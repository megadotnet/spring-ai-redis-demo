package com.redis.demo.spring.ai.util;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TextChunk {
    private String content;
    private String type;
    private int startLine;
    private int endLine;
    private List<String> associatedImages;

    public TextChunk(String content, String type, int startLine, int endLine) {
        this.content = content;
        this.type = type;
        this.startLine = startLine;
        this.endLine = endLine;
        this.associatedImages = new ArrayList<>();
    }

    // getters and setters for image association...
}