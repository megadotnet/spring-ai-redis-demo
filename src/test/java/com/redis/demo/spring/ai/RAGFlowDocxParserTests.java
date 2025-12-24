package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.parse.RAGFlowDocxParser;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.junit.jupiter.api.Test;

import java.io.IOException;


/**
     * 测试RAGFlowDocxParser的主解析功能
     * 验证解析器能够正确解析远程docx文件并返回有效的解析结果
     * 包括验证解析结果不为null，段落和表格数据存在且段落数量大于0
     */
    @Test
    public void testMain() throws IOException, InvalidFormatException {
        RAGFlowDocxParser parser = new RAGFlowDocxParser();
        // 解析远程docx文件
        RAGFlowDocxParser.ParseResult result = parser.parse("https://disk.sample.cat/samples/docx/sample3.docx", null);

        // 验证解析结果不为null
        org.junit.jupiter.api.Assertions.assertNotNull(result);
        // 验证段落列表不为null
        org.junit.jupiter.api.Assertions.assertNotNull(result.getSections());
        // 验证表格列表不为null
        org.junit.jupiter.api.Assertions.assertNotNull(result.getTables());

        // 验证至少有一个段落被解析
        org.junit.jupiter.api.Assertions.assertTrue(result.getSections().size()>0, "Sections count should be more than 0");

        //for (RAGFlowDocxParser.Section section : result.getSections()) {
        //    org.junit.jupiter.api.Assertions.assertNotNull(section.getText());
        //    org.junit.jupiter.api.Assertions.assertNotNull(section.getStyle());
        //}
    }
}
