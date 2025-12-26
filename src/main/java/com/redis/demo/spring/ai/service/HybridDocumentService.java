package com.redis.demo.spring.ai.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.redis.demo.spring.ai.RagDataLoader;
import com.redis.demo.spring.ai.parse.RAGFlowDocxParser;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class HybridDocumentService {

    private final RAGFlowDocxParser customDocxParser = new RAGFlowDocxParser();

    public List<Document> loadDocDirect(Resource resource) throws IOException, InvalidFormatException {
        String filename = resource.getFilename();
        if (filename.endsWith(".docx")) {
            // 由于 RAGFlowDocxParser 只接受文件路径，我们暂时仍需要获取文件路径
            // 但我们可以检查是否可以访问文件
            if (resource.isFile()) {
                RAGFlowDocxParser.ParseResult result = customDocxParser.parse(resource.getFile().getAbsolutePath(), null);

                // 2. 转换 Paragraphs
                List<Document> docs = customDocxParser.convertSectionsToDocuments(result.getSections());

                // 3. 转换 Tables (你的代码把表格作为 List<List<String>> 返回，你需要决定如何把它们变成 Document)
                for (List<String> tableLines : result.getTables()) {
                    String tableText = String.join("\n", tableLines);
                    docs.add(new Document(tableText, Map.of("type", "table")));
                }
                return docs;
            } else {
                throw new IOException("DOCX files must be accessed as files, not as streams");
            }
        } else if (filename.endsWith(".pdf")) {
            // 使用 Tika 处理 PDF 等
            List<Document> documents = new TikaDocumentReader(resource).read();
            // 添加文档分块逻辑
            TextSplitter splitter = new TokenTextSplitter();
            return splitter.split(documents);
        }
        return new JsonReader(resource, RagDataLoader.KEYS).get();
    }

    public List<Document> loadDocumentFromZip(Resource resource) throws IOException {
        return new JsonReader(resource, RagDataLoader.KEYS).get();
    }
}