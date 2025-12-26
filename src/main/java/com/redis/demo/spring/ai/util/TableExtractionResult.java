package com.redis.demo.spring.ai.util;

import lombok.Data;

import java.util.List;

@Data
public class TableExtractionResult {
    private String remainder;
    private List<String> tables;

    public TableExtractionResult(String remainder, List<String> tables) {
        this.remainder = remainder;
        this.tables = tables;
    }

    // getters...
}

