package com.redis.demo.spring.ai.util;

import java.util.*;
import java.util.regex.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownProcessor {

    // 配置参数
    private int chunkTokenNum = 128;
    private String delimiter = "\n!?;。；！？";
    private boolean separateTables = true;

    public MarkdownProcessor(int chunkTokenNum, String delimiter, boolean separateTables) {
        this.chunkTokenNum = chunkTokenNum;
        this.delimiter = delimiter;
        this.separateTables = separateTables;
    }

    // 主处理方法
    public ProcessingResult processMarkdown(String markdownText) {
        // 1. 表格提取与分离
        TableExtractionResult tableResult = extractTablesAndRemainder(markdownText);

        // 2. 元素提取
        MarkdownElementExtractor extractor = new MarkdownElementExtractor(tableResult.getRemainder());
        List<MarkdownElement> elements = extractor.extractElements(delimiter, true);

        // 3. 分块处理
        List<TextChunk> chunks = processChunks(elements, tableResult.getTables());

        return new ProcessingResult(chunks, tableResult.getTables());
    }

    private TableExtractionResult extractTablesAndRemainder(String markdownText) {
        List<String> tables = new ArrayList<>();
        String workingText = markdownText;

        // 标准Markdown表格模式 - 基于Python实现 [1](#3-0)
        Pattern borderTablePattern = Pattern.compile(
                "(?:\\n|^)(?:\\|.*?\\|.*?\\|.*?\\n)(?:\\|(?:\\s*[:-]+[-| :]*\\s*)\\|.*?\\n)(?:\\|.*?\\|.*?\\|.*?\\n)+",
                Pattern.MULTILINE);

        // 无边框表格模式 - 基于Python实现 [2](#3-1)
        Pattern noBorderTablePattern = Pattern.compile(
                "(?:\\n|^)(?:\\S.*?\\|.*?\\n)(?:(?:\\s*[:-]+[-| :]*\\s*).*?\\n)(?:\\S.*?\\|.*?\\n)+",
                Pattern.MULTILINE);

        // HTML表格模式 - 基于Python实现 [3](#3-2)
        Pattern htmlTablePattern = Pattern.compile(
                "(?:\\n|^)\\s*(?:(?:<html[^>]*>\\s*<body[^>]*>\\s*<table[^>]*>.*?</table>\\s*</body>\\s*</html>)|(?:<body[^>]*>\\s*<table[^>]*>.*?</table>\\s*</body>)|(?:<table[^>]*>.*?</table>))\\s*(?=\\n|$)",
                Pattern.MULTILINE | Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

        // 提取表格
        workingText = extractTables(workingText, borderTablePattern, tables);
        workingText = extractTables(workingText, noBorderTablePattern, tables);
        workingText = extractTables(workingText, htmlTablePattern, tables);

        return new TableExtractionResult(workingText, tables);
    }

    private String extractTables(String text, Pattern pattern, List<String> tables) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String table = matcher.group();
            tables.add(table);

            if (separateTables) {
                // 分离表格，替换为空行
                matcher.appendReplacement(result, "\n\n");
            } else {
                // 保留表格，转换为HTML
                String htmlTable = convertTableToHtml(table);
                matcher.appendReplacement(result, Matcher.quoteReplacement(htmlTable + "\n\n"));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private List<TextChunk> processChunks(List<MarkdownElement> elements, List<String> tables) {
        List<TextChunk> chunks = new ArrayList<>();

        // 处理文本元素分块 - 基于Python实现 [6](#3-5)
        for (MarkdownElement element : elements) {
            String content = element.getContent();

            // 计算token数量（简化实现）
            int tokenCount = estimateTokenCount(content);

            if (tokenCount <= chunkTokenNum) {
                // 单个元素作为一块
                TextChunk chunk = new TextChunk(
                        content,
                        element.getType(),
                        element.getStartLine(),
                        element.getEndLine());
                chunks.add(chunk);
            } else {
                // 需要进一步分割
                List<TextChunk> subChunks = splitContent(content, element);
                chunks.addAll(subChunks);
            }
        }

        // 处理表格 - 基于Python实现 [7](#3-6)
        for (String table : tables) {
            String htmlTable = convertTableToHtml(table);
            TextChunk tableChunk = new TextChunk(
                    htmlTable,
                    "table",
                    -1,
                    -1);
            chunks.add(tableChunk);
        }

        return chunks;
    }

    /**
     * 将Markdown表格转换为HTML格式
     * 基于Python实现 [1](#6-0)
     */
    public String convertTableToHtml(String markdownTable) {
        if (markdownTable == null || markdownTable.trim().isEmpty()) {
            return "";
        }

        // 检查是否已经是HTML表格
        if (markdownTable.trim().toLowerCase().contains("<table>")) {
            return cleanHtmlTable(markdownTable);
        }

        // 解析Markdown表格
        List<String[]> tableData = parseMarkdownTable(markdownTable);
        if (tableData.isEmpty()) {
            return "";
        }

        // 构建HTML表格
        StringBuilder html = new StringBuilder();
        html.append("<table>\n");

        for (int i = 0; i < tableData.size(); i++) {
            String[] row = tableData.get(i);
            html.append("  <tr>\n");

            for (String cell : row) {
                String tag = (i == 0) ? "th" : "td"; // 第一行作为表头
                html.append("    <").append(tag).append(">")
                        .append(escapeHtml(cell.trim()))
                        .append("</").append(tag).append(">\n");
            }

            html.append("  </tr>\n");
        }

        html.append("</table>");
        return html.toString();
    }

    /**
     * 解析Markdown表格为二维数组
     * 基于Python实现 [2](#6-1)
     */
    private List<String[]> parseMarkdownTable(String markdownTable) {
        List<String[]> tableData = new ArrayList<>();
        String[] lines = markdownTable.trim().split("\n");

        for (String line : lines) {
            line = line.trim();

            // 跳过分隔行 (|---|---|)
            if (line.matches("^\\|\\s*[:\\-\\|\\s]*\\|$")) {
                continue;
            }

            // 处理表格行
            if (line.startsWith("|") && line.endsWith("|")) {
                // 移除首尾的|，然后按|分割
                String content = line.substring(1, line.length() - 1);
                String[] cells = content.split("\\|");

                // 清理单元格内容
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = cells[i].trim();
                }

                tableData.add(cells);
            }
        }

        return tableData;
    }

    /**
     * 清理HTML表格标签，移除属性
     * 基于Python实现 [3](#6-2)
     */
    private String cleanHtmlTable(String htmlTable) {
        // 移除HTML标签中的属性，只保留标签名
        Pattern tagPattern = Pattern.compile("<(/?)(\\w+)[^>]*>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = tagPattern.matcher(htmlTable);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String closingSlash = matcher.group(1);
            String tagName = matcher.group(2);
            matcher.appendReplacement(result, "<" + closingSlash + tagName + ">");
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * HTML转义
     */
    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private List<TextChunk> splitContent(String content, MarkdownElement originalElement) {
        List<TextChunk> chunks = new ArrayList<>();

        // 按语义边界分割 - 基于Python实现 [8](#3-7)
        String[] parts = content.split("(?<=" + Pattern.quote(delimiter) + ")");

        StringBuilder currentChunk = new StringBuilder();
        int currentTokens = 0;

        for (String part : parts) {
            int partTokens = estimateTokenCount(part);

            if (currentTokens + partTokens <= chunkTokenNum) {
                currentChunk.append(part);
                currentTokens += partTokens;
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(new TextChunk(
                            currentChunk.toString().trim(),
                            originalElement.getType(),
                            originalElement.getStartLine(),
                            originalElement.getEndLine()));
                }
                currentChunk = new StringBuilder(part);
                currentTokens = partTokens;
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(new TextChunk(
                    currentChunk.toString().trim(),
                    originalElement.getType(),
                    originalElement.getStartLine(),
                    originalElement.getEndLine()));
        }

        return chunks;
    }

    private int estimateTokenCount(String text) {
        // 简化的token计算，实际应用中应使用更精确的方法
        return text.length() / 4; // 假设平均4个字符一个token
    }

}