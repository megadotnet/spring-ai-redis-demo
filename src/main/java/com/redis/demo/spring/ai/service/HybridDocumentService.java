package com.redis.demo.spring.ai.service;

import com.redis.demo.spring.ai.RagDataLoader;
import com.redis.demo.spring.ai.parse.RAGFlowDocxParser;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class HybridDocumentService {

    private final RAGFlowDocxParser customDocxParser = new RAGFlowDocxParser();
    // 注入 Spring AI 的 Tika Reader...

    public List<Document> loadDocument(String path) throws IOException, InvalidFormatException {
        if (path.endsWith(".docx")) {
            // 1. 使用你的深度解析器
            RAGFlowDocxParser.ParseResult result = customDocxParser.parse(path, null);

            // 2. 转换 Paragraphs
            List<Document> docs = customDocxParser.convertSectionsToDocuments(result.getSections());

            // 3. 转换 Tables (你的代码把表格作为 List<List<String>> 返回，你需要决定如何把它们变成 Document)
            for (List<String> tableLines : result.getTables()) {
                String tableText = String.join("\n", tableLines);
                docs.add(new Document(tableText, Map.of("type", "table")));
            }
            return docs;
        } else if (path.endsWith(".pdf")){
            // 使用 Tika 处理 PDF 等
            return new TikaDocumentReader(new FileSystemResource(path)).read();
        }
        else {
            // 其他文件json类型
            JsonReader loader = new JsonReader(new FileSystemResource(path), RagDataLoader.KEYS);
            return loader.get();
        }
    }
}