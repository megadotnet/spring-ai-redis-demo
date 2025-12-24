package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.parse.RAGFlowDocxParser;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试将RAGFlowDocxParser.Section转换为Spring AI Document的功能
 */
public class SectionToDocumentConverterTest {

    @Test
    public void testConvertSectionToDocument() {
        // 创建一个RAGFlowDocxParser实例
        RAGFlowDocxParser parser = new RAGFlowDocxParser();
        
        // 创建一个测试Section
        RAGFlowDocxParser.Section section = new RAGFlowDocxParser.Section(
            "这是一个测试段落", 
            "Normal"
        );
        
        // 转换单个Section为Document
        Document document = parser.convertSectionToDocument(section);
        
        // 验证转换结果
        assertNotNull(document, "Document不应为null");
        assertEquals("这是一个测试段落", document.getText(), "文档内容应匹配");
        assertEquals("Normal", document.getMetadata().get("style"), "样式应匹配");
        assertEquals("paragraph", document.getMetadata().get("type"), "类型应为paragraph");
    }
    
    @Test
    public void testConvertSectionsToDocuments() {
        // 创建一个RAGFlowDocxParser实例
        RAGFlowDocxParser parser = new RAGFlowDocxParser();
        
        // 创建测试Sections列表
        RAGFlowDocxParser.Section section1 = new RAGFlowDocxParser.Section(
            "第一个测试段落", 
            "Heading1"
        );
        
        RAGFlowDocxParser.Section section2 = new RAGFlowDocxParser.Section(
            "第二个测试段落", 
            "Normal"
        );
        
        RAGFlowDocxParser.Section section3 = new RAGFlowDocxParser.Section(
            "第三个测试段落", 
            "BodyText"
        );
        
        java.util.List<RAGFlowDocxParser.Section> sections = 
            java.util.Arrays.asList(section1, section2, section3);
        
        // 批量转换Sections为Documents
        List<Document> documents = parser.convertSectionsToDocuments(sections);
        
        // 验证转换结果
        assertNotNull(documents, "Documents列表不应为null");
        assertEquals(3, documents.size(), "应有3个文档");
        
        // 验证每个文档
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            RAGFlowDocxParser.Section originalSection = sections.get(i);
            
            assertEquals(originalSection.getText(), doc.getText(),
                "文档内容应匹配");
            assertEquals(originalSection.getStyle(), doc.getMetadata().get("style"), 
                "样式应匹配");
            assertEquals("paragraph", doc.getMetadata().get("type"), 
                "类型应为paragraph");
        }
    }
}