package com.redis.demo.spring.ai.parse;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * RAGFlowDocxParser类用于解析DOCX文档并提取其中的段落和表格内容
 * 支持从文件路径、URL或字节数组解析DOCX文档
 */
public class RAGFlowDocxParser {

    /**
     * 定义文本块类型的枚举类
     * 用于标识不同类型的文本内容，如日期、数字、英文等
     */
    private static class BlockType {
        static final String DATE = "Dt";      // 日期类型
        static final String NUMBER = "Nu";    // 数字类型
        static final String CAPITAL = "Ca";   // 大写字母类型
        static final String ENGLISH = "En";   // 英文类型
        static final String TEXT = "Tx";      // 文本类型
        static final String LONG_TEXT = "Lx"; // 长文本类型
        static final String OTHER = "Ot";    // 其他类型
    }

    /**
     * 解析DOCX文档的主要方法
     * 根据提供的文件路径或字节数组解析DOCX文档，并返回解析结果
     *
     * @param filename 文件路径或URL，如果为null则使用binary参数
     * @param binary 字节数组形式的文档内容，如果filename不为null则忽略此参数
     * @return ParseResult 解析结果，包含段落和表格信息
     * @throws IOException 文件读取异常
     * @throws InvalidFormatException DOCX格式异常
     */
    public ParseResult parse(String filename, byte[] binary) throws IOException, InvalidFormatException {
        XWPFDocument document;
        // 根据输入类型创建文档对象：URL、文件路径或字节数组
        if (filename != null) {
            // 检查是否为URL
            if (isURL(filename)) {
                URL url = new URL(filename);
                try (InputStream inputStream = url.openStream()) {
                    document = new XWPFDocument(inputStream);
                }
            } else {
                document = new XWPFDocument(new FileInputStream(filename));
            }
        } else {
            document = new XWPFDocument(new ByteArrayInputStream(binary));
        }

        // 初始化段落和表格存储列表
        List<Section> sections = new ArrayList<>();
        List<List<String>> tables = new ArrayList<>();

        int pageNumber = 0;

        // 解析段落
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            StringBuilder paragraphText = new StringBuilder();
            String styleName = paragraph.getStyle() != null ? paragraph.getStyle() : "";

            for (XWPFRun run : paragraph.getRuns()) {
                paragraphText.append(run.getText(0));

                // 检测分页符
                if (run.getCTR().xmlText().contains("lastRenderedPageBreak")) {
                    pageNumber++;
                }
            }

            if (paragraphText.toString().trim().length() > 0) {
                sections.add(new Section(paragraphText.toString(), styleName));
            }
        }

        // 解析表格
        for (XWPFTable table : document.getTables()) {
            List<String> tableContent = extractTableContent(table);
            tables.add(tableContent);
        }

        document.close();
        return new ParseResult(sections, tables);
    }

    /**
     * 从表格中提取内容
     * 将XWPFTable对象转换为字符串列表的列表
     *
     * @param table XWPFTable对象
     * @return 包含表格内容的字符串列表
     */
    private List<String> extractTableContent(XWPFTable table) {
        List<List<String>> data = new ArrayList<>();

        for (XWPFTableRow row : table.getRows()) {
            List<String> rowData = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                rowData.add(cell.getText());
            }
            data.add(rowData);
        }

        return formatTableContent(data);
    }

    /**
     * 格式化表格内容
     * 分析表格数据类型，识别表头，并生成格式化的表格内容
     *
     * @param data 表格原始数据，以二维字符串列表形式存储
     * @return 格式化后的表格内容列表
     */
    private List<String> formatTableContent(List<List<String>> data) {
        if (data.size() < 2) {
            return Collections.emptyList();
        }

        // 分析表格内容类型
        Map<String, Integer> typeCount = new HashMap<>();
        for (int i = 1; i < data.size(); i++) {
            for (int j = 0; j < data.get(i).size(); j++) {
                String type = determineBlockType(data.get(i).get(j));
                typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
            }
        }

        String maxType = typeCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(BlockType.OTHER);

        // 处理表头
        List<Integer> headerRows = new ArrayList<>();
        headerRows.add(0);

        if (BlockType.NUMBER.equals(maxType)) {
            for (int r = 1; r < data.size(); r++) {
                Map<String, Integer> rowTypeCount = new HashMap<>();
                for (int j = 0; j < data.get(r).size(); j++) {
                    String type = determineBlockType(data.get(r).get(j));
                    rowTypeCount.put(type, rowTypeCount.getOrDefault(type, 0) + 1);
                }
                String rowMaxType = rowTypeCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(BlockType.OTHER);

                if (!rowMaxType.equals(maxType)) {
                    headerRows.add(r);
                }
            }
        }

        // 生成格式化的表格内容
        List<String> lines = new ArrayList<>();
        int colCount = data.get(0).size();

        for (int i = 1; i < data.size(); i++) {
            if (headerRows.contains(i)) {
                continue;
            }

            List<String> headers = new ArrayList<>();
            for (int j = 0; j < data.get(i).size(); j++) {
                StringBuilder header = new StringBuilder();
                for (int h : headerRows) {
                    if (i + h >= 0 && i + h < data.size() && j < data.get(i + h).size()) {
                        String cellValue = data.get(i + h).get(j).trim();
                        if (!cellValue.isEmpty() && !header.toString().contains(cellValue)) {
                            if (header.length() > 0) header.append(",");
                            header.append(cellValue);
                        }
                    }
                }
                if (header.length() > 0) {
                    header.append(": ");
                }
                headers.add(header.toString());
            }

            List<String> cells = new ArrayList<>();
            for (int j = 0; j < data.get(i).size(); j++) {
                String cellValue = data.get(i).get(j);
                if (!cellValue.trim().isEmpty()) {
                    cells.add(headers.get(j) + cellValue);
                }
            }

            if (!cells.isEmpty()) {
                lines.add(String.join(";", cells));
            }
        }

        return colCount > 3 ? lines : Collections.singletonList(String.join("\n", lines));
    }

    /**
     * 确定文本块的类型
     * 根据文本内容的特征判断其类型，如日期、数字、英文等
     *
     * @param text 待判断类型的文本
     * @return 文本类型标识符（如Dt、Nu、Ca等）
     */
    private String determineBlockType(String text) {
        if (text == null || text.trim().isEmpty()) {
            return BlockType.OTHER;
        }

        text = text.trim();

        // 日期模式
        if (Pattern.matches("^(20|19)[0-9]{2}[年/-][0-9]{1,2}[月/-][0-9]{1,2}日*$", text) ||
                Pattern.matches("^(20|19)[0-9]{2}年$", text) ||
                Pattern.matches("^(20|19)[0-9]{2}[年/-][0-9]{1,2}月*$", text) ||
                Pattern.matches("^[0-9]{1,2}[月/-][0-9]{1,2}日*$", text) ||
                Pattern.matches("^第*[一二三四1-4]季度$", text) ||
                Pattern.matches("^(20|19)[0-9]{2}年*[一二三四1-4]季度$", text) ||
                Pattern.matches("^(20|19)[0-9]{2}[ABCDE]$", text)) {
            return BlockType.DATE;
        }

        // 数字模式
        if (Pattern.matches("^[0-9.,+%/ -]+$", text)) {
            return BlockType.NUMBER;
        }

        // 大写字母模式
        if (Pattern.matches("^[0-9A-Z/\\._~-]+$", text)) {
            return BlockType.CAPITAL;
        }

        // 英文模式
        if (Pattern.matches("^[A-Z]*[a-z' -]+$", text)) {
            return BlockType.ENGLISH;
        }

        // 单字符
        if (text.length() == 1) {
            return BlockType.OTHER;
        }

        // 基于token数量判断文本类型
        String[] tokens = text.split("\\s+");
        long validTokens = Arrays.stream(tokens).filter(t -> t.length() > 1).count();

        if (validTokens > 3) {
            return validTokens < 12 ? BlockType.TEXT : BlockType.LONG_TEXT;
        }

        return BlockType.OTHER;
    }

    /**
     * 判断字符串是否为有效的URL
     * 检查字符串是否符合URL格式并以http或https开头
     *
     * @param urlString 待验证的字符串
     * @return 如果是有效URL返回true，否则返回false
     */
    private boolean isURL(String urlString) {
        try {
            new URL(urlString);
            return urlString.startsWith("http://") || urlString.startsWith("https://");
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * 解析结果类
     * 用于封装DOCX文档解析后的段落和表格信息
     */
    public static class ParseResult {
        private final List<Section> sections;  // 段落列表
        private final List<List<String>> tables;  // 表格列表

        /**
         * 构造函数
         *
         * @param sections 段落列表
         * @param tables 表格列表
         */
        public ParseResult(List<Section> sections, List<List<String>> tables) {
            this.sections = sections;
            this.tables = tables;
        }

        public List<Section> getSections() { return sections; }
        public List<List<String>> getTables() { return tables; }
    }

    /**
     * 段落类
     * 用于表示文档中的一个段落，包含文本内容和样式信息
     */
    public static class Section {
        private final String text;   // 段落文本内容
        private final String style;  // 段落样式

        /**
         * 构造函数
         *
         * @param text 段落文本内容
         * @param style 段落样式
         */
        public Section(String text, String style) {
            this.text = text;
            this.style = style;
        }

        public String getText() { return text; }
        public String getStyle() { return style; }
    }
}