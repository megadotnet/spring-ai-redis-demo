package com.redis.demo.spring.ai;

import com.redis.demo.spring.ai.util.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static com.redis.demo.spring.ai.util.MarkdownUrlReader.readMarkdownFromUrl;
import static org.junit.jupiter.api.Assertions.*;

public class MarkdownElementExtractorTest {

    @Test
    public void testConvertTableToHtml() {
        MarkdownProcessor processor = new MarkdownProcessor(128, "\n!?;。；！？", true);

        String markdownTable = "| 姓名 | 年龄 | 职业 |\n" +
                "|------|------|------|\n" +
                "| 张三 | 25   | 工程师 |\n" +
                "| 李四 | 30   | 设计师 |";

        String htmlTable = processor.convertTableToHtml(markdownTable);

        // 验证转换结果
        assertNotNull(htmlTable);
        assertTrue(htmlTable.contains("<table>"));
        assertTrue(htmlTable.contains("<th>姓名</th>"));
        assertTrue(htmlTable.contains("<td>张三</td>"));
        assertTrue(htmlTable.contains("<td>25</td>"));
        assertTrue(htmlTable.contains("<td>工程师</td>"));
    }

    @Test
    public void testMarkdownElementExtractor_WithHeaders() {
        String markdown = "# 标题1\n\n这是第一段内容。\n\n## 标题2\n\n这是第二段内容。";

        MarkdownElementExtractor extractor = new MarkdownElementExtractor(markdown);
        List<MarkdownElement> elements = extractor.extractElements(null, true);

        assertEquals(4, elements.size());

        assertEquals("header", elements.get(0).getType());
        assertEquals("# 标题1", elements.get(0).getContent());
        assertEquals(0, elements.get(0).getStartLine());
        assertEquals(0, elements.get(0).getEndLine());

        assertEquals("text_block", elements.get(1).getType());
        assertEquals("这是第一段内容。", elements.get(1).getContent());

        assertEquals("header", elements.get(2).getType());
        assertEquals("## 标题2", elements.get(2).getContent());

        assertEquals("text_block", elements.get(3).getType());
        assertEquals("这是第二段内容。", elements.get(3).getContent());
    }

    @Test
    public void testMarkdownElementExtractor_WithCodeBlock() {
        String markdown = "这是一个代码块示例：\n\n```java\npublic class Test {\n    public static void main(String[] args) {\n        System.out.println(\"Hello World!\");\n    }\n}\n```\n\n结束。";

        MarkdownElementExtractor extractor = new MarkdownElementExtractor(markdown);
        List<MarkdownElement> elements = extractor.extractElements(null, true);

        assertEquals(3, elements.size());

        assertEquals("text_block", elements.get(0).getType());
        assertTrue(elements.get(0).getContent().contains("代码块示例"));

        assertEquals("code_block", elements.get(1).getType());
        assertTrue(elements.get(1).getContent().contains("public class Test"));
        assertTrue(elements.get(1).getContent().contains("System.out.println"));

        assertEquals("text_block", elements.get(2).getType());
        assertTrue(elements.get(2).getContent().contains("结束"));
    }

    @Test
    public void testMarkdownElementExtractor_WithBlockquote() {
        String markdown = "普通文本\n\n> 这是一个引用块\n> 第二行引用\n\n继续普通文本";

        MarkdownElementExtractor extractor = new MarkdownElementExtractor(markdown);
        List<MarkdownElement> elements = extractor.extractElements(null, true);

        assertEquals(3, elements.size());

        assertEquals("text_block", elements.get(0).getType());
        assertTrue(elements.get(0).getContent().contains("普通文本"));

        assertEquals("blockquote", elements.get(1).getType());
        assertTrue(elements.get(1).getContent().contains("这是一个引用块"));
        assertTrue(elements.get(1).getContent().contains("第二行引用"));

        assertEquals("text_block", elements.get(2).getType());
        assertTrue(elements.get(2).getContent().contains("继续普通文本"));
    }

    @Test
    public void testMarkdownElementExtractor_WithLists() {
        String markdown = "无序列表：\n\n- 项目1\n- 项目2\n- 项目3\n\n有序列表：\n\n1. 第一项\n2. 第二项\n3. 第三项";

        MarkdownElementExtractor extractor = new MarkdownElementExtractor(markdown);
        List<MarkdownElement> elements = extractor.extractElements(null, true);

        assertEquals(4, elements.size());

        assertEquals("text_block", elements.get(0).getType());
        assertTrue(elements.get(0).getContent().contains("无序列表"));

        assertEquals("list_block", elements.get(1).getType());
        assertTrue(elements.get(1).getContent().contains("- 项目1"));
        assertTrue(elements.get(1).getContent().contains("- 项目3"));

        assertEquals("text_block", elements.get(2).getType());
        assertTrue(elements.get(2).getContent().contains("有序列表"));

        assertEquals("list_block", elements.get(3).getType());
        assertTrue(elements.get(3).getContent().contains("1. 第一项"));
        assertTrue(elements.get(3).getContent().contains("3. 第三项"));
    }

    @Test
    public void testMarkdownProcessor_FullProcessing() {
        String markdown = "# 完整测试\n\n这是一个段落。\n\n| 姓名 | 年龄 |\n|------|------|\n| 张三 | 25 |\n\n```java\npublic class Test {}\n```";

        MarkdownProcessor processor = new MarkdownProcessor(128, "\n!?;。；！？", true);
        ProcessingResult result = processor.processMarkdown(markdown);

        assertNotNull(result);
        assertNotNull(result.getChunks());
        assertNotNull(result.getTables());

        // 验证表格被提取
        assertEquals(1, result.getTables().size());

        // 验证有多个文本块
        assertTrue(!result.getChunks().isEmpty());

        // 检查是否有表格类型的块
        boolean hasTableChunk = result.getChunks().stream()
                .anyMatch(chunk -> "table".equals(chunk.getType()));
        assertTrue(hasTableChunk, "应该有表格类型的文本块");
    }

    @Test
    public void testMarkdownProcessor_FullProcessing2() throws IOException {

        String markdownUrl = "https://gitee.com/Tencent-BlueKing/bk-ci/raw/master/README.md";
        String markdown = readMarkdownFromUrl(markdownUrl);

        MarkdownProcessor processor = new MarkdownProcessor(128, "\n!?;。；！？", false);
        ProcessingResult result = processor.processMarkdown(markdown);

        assertNotNull(result);
        assertNotNull(result.getChunks());
        assertNotNull(result.getTables());

        // 验证表格被提取
        assertEquals(0, result.getTables().size());

        // 验证有多个文本块
        assertTrue(!result.getChunks().isEmpty());
    }

    @Test
    public void testMarkdownProcessor_FullProcessingRemoteTable() throws IOException {

        String markdownUrl = "https://gitee.com/JD-opensource/sbom-tool/raw/master/README_zh.md";
        String markdown = readMarkdownFromUrl(markdownUrl);

        MarkdownProcessor processor = new MarkdownProcessor(128, "\n!?;。；！？", false);
        ProcessingResult result = processor.processMarkdown(markdown);

        assertNotNull(result);
        assertNotNull(result.getChunks());
        assertNotNull(result.getTables());

        // 验证表格被提取
        assertEquals(10, result.getTables().size());

        // 验证有多个文本块
        assertTrue(!result.getChunks().isEmpty());
    }

    @Test
    public void testExtractWithCustomDelimiter() {
        String markdown = "第一部分内容`custom_delimiter`第二部分`custom_delimiter`第三部分";

        MarkdownElementExtractor extractor = new MarkdownElementExtractor(markdown);
        List<MarkdownElement> elements = extractor.extractElements("`custom_delimiter`", true);

        // 注意：根据实现，自定义分隔符提取会将内容按分隔符分割
        assertTrue(elements.size() >= 1); // 至少有一个元素

        // 验证内容被正确处理
        String fullContent = String.join(" ", elements.stream()
                .map(MarkdownElement::getContent)
                .toArray(String[]::new));
        assertTrue(fullContent.contains("第一部分内容"));
        assertTrue(fullContent.contains("第二部分"));
        assertTrue(fullContent.contains("第三部分"));
    }

    @Test
    public void testImageAssociator() {
        String markdownText = "# 标题\n\n" +
                "文本内容。\n\n" +
                "![图片1](image1.jpg)\n\n" +
                "更多文本内容。\n\n" +
                "<img src=\"image2.png\" alt=\"图片2\">\n\n" +
                "最后的内容。";

        // 创建处理器
        MarkdownProcessor processor = new MarkdownProcessor(128, "\n!?;。；！？", true);

        // 提取图片引用
        ImageExtractor imageExtractor = new ImageExtractor();
        List<ImageReference> imageRefs = imageExtractor.extractImageReferences(markdownText);

        // 验证提取的图片
        assertNotNull(imageRefs);
        assertEquals(2, imageRefs.size());

        // 处理Markdown
        ProcessingResult result = processor.processMarkdown(markdownText);
        assertNotNull(result);
        assertNotNull(result.getChunks());

        // 关联图片与文本块
        ImageAssociator associator = new ImageAssociator();
        associator.associateImagesWithChunks(result.getChunks(), imageRefs);

        // 验证关联结果
        boolean foundImage1 = false;
        boolean foundImage2 = false;

        for (TextChunk chunk : result.getChunks()) {
            List<String> images = chunk.getAssociatedImages();
            if (images != null && !images.isEmpty()) {
                String imagesString = images.toString();
                if (imagesString.contains("image1.jpg")) {
                    foundImage1 = true;
                }
                if (imagesString.contains("image2.png")) {
                    foundImage2 = true;
                }
            }
        }

        assertTrue(foundImage1, "image1.jpg should be associated with a chunk");
        assertTrue(foundImage2, "image2.png should be associated with a chunk");
    }
}
