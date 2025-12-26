package com.redis.demo.spring.ai.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownElementExtractor {
    private String markdownContent;
    private String[] lines;

    public MarkdownElementExtractor(String markdownContent) {
        this.markdownContent = markdownContent;
        this.lines = markdownContent.split("\n");
    }

    private List<String> getSemanticBoundaries() {
        return Arrays.asList(
                "\\n\\n+",      // 段落边界
                "\\.# ",        // 标题边界
                "```",          // 代码块边界
                "\\n> ",        // 引用边界
                "\\n[-*+] ",    // 列表边界
                "\\n\\d+\\. "   // 有序列表边界
        );
    }

    private MarkdownElement extractBlockquote(int startLine) {
        StringBuilder content = new StringBuilder();
        int endLine = startLine;

        // 基于Python实现 [1](#4-0)
        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i];
            // 检查是否是引用行或空行（引用块内的空行）
            if (line.trim().startsWith(">") || (i > startLine && !line.trim().isEmpty())) {
                content.append(line).append("\n");
                endLine = i;
            } else {
                break;
            }
        }

        return new MarkdownElement(
                "blockquote",
                content.toString().trim(),
                startLine,
                endLine
        );
    }

    private MarkdownElement extractTextBlock(int startLine) {
        StringBuilder content = new StringBuilder();
        content.append(lines[startLine]).append("\n");
        int endLine = startLine;

        // 基于Python实现 [2](#4-1)
        for (int i = startLine + 1; i < lines.length; i++) {
            String line = lines[i];

            // 检查是否遇到块级元素，如果是则停止
            if (isBlockElement(line)) {
                break;
            } else if (!line.trim().isEmpty()) {
                // 如果是空行，检查下一行是否是块元素
                if (i + 1 < lines.length && isBlockElement(lines[i + 1])) {
                    break;
                } else {
                    content.append(line).append("\n");
                    endLine = i;
                }
            } else {
                content.append(line).append("\n");
                endLine = i;
            }
        }

        return new MarkdownElement(
                "text_block",
                content.toString().trim(),
                startLine,
                endLine
        );
    }

    // 辅助方法：检查是否是块级元素
    private boolean isBlockElement(String line) {
        return line.matches("^#{1,6}\\s+.*$") ||           // 标题
                line.trim().startsWith("```") ||           // 代码块
                line.matches("^\\s*[-*+]\\s+.*$") ||       // 无序列表
                line.matches("^\\s*\\d+\\.\\s+.*$") ||     // 有序列表
                line.trim().startsWith(">");               // 引用块
    }


    private List<MarkdownElement> extractWithCustomDelimiter(String delimiter, boolean includeMeta) {
        List<MarkdownElement> sections = new ArrayList<>();

        // 基于Python实现 [1](#5-0)
        String delimitersPattern = getDelimiters(delimiter);

        if (delimitersPattern.isEmpty()) {
            return sections;
        }

        String fullText = String.join("\n", lines);
        Pattern pattern = Pattern.compile(delimitersPattern);
        Matcher matcher = pattern.matcher(fullText);

        int lastEnd = 0;
        int lineOffset = 0;

        while (matcher.find()) {
            String part = fullText.substring(lastEnd, matcher.start());
            if (!part.trim().isEmpty()) {
                if (includeMeta) {
                    int startLine = countLines(fullText.substring(0, lastEnd));
                    int endLine = countLines(fullText.substring(0, matcher.start()));

                    sections.add(new MarkdownElement(
                            "custom_delimiter",
                            part.trim(),
                            startLine,
                            endLine
                    ));
                } else {
                    sections.add(new MarkdownElement(
                            "custom_delimiter",
                            part.trim(),
                            -1,
                            -1
                    ));
                }
            }
            lastEnd = matcher.end();
        }

        // 处理最后一部分
        String lastPart = fullText.substring(lastEnd);
        if (!lastPart.trim().isEmpty()) {
            if (includeMeta) {
                int startLine = countLines(fullText.substring(0, lastEnd));
                int endLine = countLines(fullText);

                sections.add(new MarkdownElement(
                        "custom_delimiter",
                        lastPart.trim(),
                        startLine,
                        endLine
                ));
            } else {
                sections.add(new MarkdownElement(
                        "custom_delimiter",
                        lastPart.trim(),
                        -1,
                        -1
                ));
            }
        }

        return sections;
    }

    private String getDelimiters(String delimiter) {
        // 基于Python实现 [2](#5-1)
        Pattern pattern = Pattern.compile("`([^`]+)`");
        Matcher matcher = pattern.matcher(delimiter);

        List<String> delimiters = new ArrayList<>();
        while (matcher.find()) {
            delimiters.add(matcher.group(1));
        }

        // 去重并按长度排序（长的优先）
        delimiters = delimiters.stream()
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .collect(java.util.stream.Collectors.toList());

        if (delimiters.isEmpty()) {
            return "";
        }

        // 转义并构建正则表达式
        String escapedDelimiters = delimiters.stream()
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));

        return "(" + escapedDelimiters + ")";
    }

    private int countLines(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        return text.split("\n").length;
    }

    public List<MarkdownElement> extractElements(String delimiter, boolean includeMeta) {
        List<MarkdownElement> sections = new ArrayList<>();

        // 处理自定义分隔符 - 基于Python实现 [4](#3-3)
        if (delimiter != null && !delimiter.isEmpty()) {
            return extractWithCustomDelimiter(delimiter, includeMeta);
        }

        // 按元素类型提取 - 基于Python实现 [5](#3-4)
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];

            if (line.matches("^#{1,6}\\s+.*$")) {
                // 标题元素
                MarkdownElement element = extractHeader(i);
                sections.add(element);
                i = element.getEndLine() + 1;
            } else if (line.trim().startsWith("```")) {
                // 代码块元素
                MarkdownElement element = extractCodeBlock(i);
                sections.add(element);
                i = element.getEndLine() + 1;
            } else if (line.matches("^\\s*[-*+]\\s+.*$") || line.matches("^\\s*\\d+\\.\\s+.*$")) {
                // 列表元素
                MarkdownElement element = extractListBlock(i);
                sections.add(element);
                i = element.getEndLine() + 1;
            } else if (line.trim().startsWith(">")) {
                // 引用块元素
                MarkdownElement element = extractBlockquote(i);
                sections.add(element);
                i = element.getEndLine() + 1;
            } else if (!line.trim().isEmpty()) {
                // 文本块元素
                MarkdownElement element = extractTextBlock(i);
                sections.add(element);
                i = element.getEndLine() + 1;
            } else {
                i++;
            }
        }

        return sections;
    }

    private MarkdownElement extractHeader(int startLine) {
        return new MarkdownElement(
                "header",
                lines[startLine],
                startLine,
                startLine
        );
    }

    private MarkdownElement extractCodeBlock(int startLine) {
        StringBuilder content = new StringBuilder();
        content.append(lines[startLine]).append("\n");
        int endLine = startLine;

        for (int i = startLine + 1; i < lines.length; i++) {
            content.append(lines[i]).append("\n");
            endLine = i;
            if (lines[i].trim().startsWith("```")) {
                break;
            }
        }

        return new MarkdownElement(
                "code_block",
                content.toString().trim(),
                startLine,
                endLine
        );
    }

    private MarkdownElement extractListBlock(int startLine) {
        StringBuilder content = new StringBuilder();
        int endLine = startLine;

        for (int i = startLine; i < lines.length; i++) {
            String line = lines[i];
            if (isListLine(line, i == startLine)) {
                content.append(line).append("\n");
                endLine = i;
            } else if (line.trim().isEmpty() || (i > startLine && line.matches("^\\s{2,}.*$"))) {
                content.append(line).append("\n");
                endLine = i;
            } else {
                break;
            }
        }

        return new MarkdownElement(
                "list_block",
                content.toString().trim(),
                startLine,
                endLine
        );
    }

    private boolean isListLine(String line, boolean isFirst) {
        if (isFirst) {
            return line.matches("^\\s*[-*+]\\s+.*$") || line.matches("^\\s*\\d+\\.\\s+.*$");
        } else {
            return line.matches("^\\s*[-*+]\\s+.*$") ||
                    line.matches("^\\s*\\d+\\.\\s+.*$") ||
                    line.matches("^\\s{2,}[-*+]\\s+.*$") ||
                    line.matches("^\\s{2,}\\d+\\.\\s+.*$") ||
                    line.matches("^\\s+\\w+.*$");
        }
    }
}







