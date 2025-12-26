package com.redis.demo.spring.ai.util;

import lombok.Data;

import java.util.List;

@Data
public class ProcessingResult {
    private List<TextChunk> chunks;
    private List<String> tables;

    public ProcessingResult(List<TextChunk> chunks, List<String> tables) {
        this.chunks = chunks;
        this.tables = tables;
    }

    // getters...
}