package com.redis.demo.spring.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

import com.redis.demo.spring.ai.service.HybridDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("RagDataLoader 单元测试")
class RagDataLoaderTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private DocumentCountProvider documentCountProvider;

    @Mock
    private HybridDocumentService hybridDocumentService;

    @Mock
    private Resource dataResource;

    @Mock
    private ApplicationArguments applicationArguments;

    private RagDataLoader ragDataLoader;

    private static final String INDEX_NAME = "test-index";
    private static final String JSON_DATA = """
        [
            {
                "name": "测试啤酒1",
                "abv": 5.0,
                "ibu": 20,
                "description": "这是一款测试啤酒的描述"
            },
            {
                "name": "测试啤酒2",
                "abv": 4.5,
                "ibu": 15,
                "description": "另一款测试啤酒的描述"
            }
        ]
        """;

    @BeforeEach
    void setUp() {
        ragDataLoader = new RagDataLoader(vectorStore, documentCountProvider,hybridDocumentService);
        ReflectionTestUtils.setField(ragDataLoader, "data", dataResource);
        ReflectionTestUtils.setField(ragDataLoader, "indexName", INDEX_NAME);
    }

    @Test
    @DisplayName("当数据已存在时应跳过加载")
    void shouldSkipLoadingWhenDataAlreadyExists() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(25000);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore, never()).add(anyList());
        verify(dataResource, never()).getInputStream();
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("当数据不存在时应成功加载普通JSON文件")
    void shouldLoadDataWhenNoExistingData() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(1000);
        setupDataResourceMock(false, JSON_DATA);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        ArgumentCaptor<List<Document>> documentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());
        
        List<Document> capturedDocuments = documentsCaptor.getValue();
        assertThat(capturedDocuments).isNotEmpty();
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("应正确处理GZIP压缩文件")
    void shouldHandleGzipFile() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(1000);
        byte[] gzipData = createGzipData(JSON_DATA);
        setupDataResourceMock(true, gzipData);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(dataResource, times(2)).getFilename();
        verify(dataResource).getInputStream();
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("当文件读取失败时应抛出异常")
    void shouldThrowExceptionWhenFileReadFails() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(1000);
        when(dataResource.exists()).thenReturn(true);
        when(dataResource.getFilename()).thenReturn("beers.json");
        when(dataResource.getInputStream()).thenThrow(new IOException("文件读取失败"));

        // When & Then
        assertThatThrownBy(() -> ragDataLoader.run(applicationArguments))
            .isInstanceOf(IOException.class)
            .hasMessage("文件读取失败");
    }

    @Test
    @DisplayName("当向量存储操作失败时应抛出异常")
    void shouldThrowExceptionWhenVectorStoreOperationFails() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(1000);
        setupDataResourceMock(false, JSON_DATA);
        doThrow(new RuntimeException("向量存储操作失败"))
            .when(vectorStore).add(anyList());

        // When & Then
        assertThatThrownBy(() -> ragDataLoader.run(applicationArguments))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("向量存储操作失败");
    }

    @Test
    @DisplayName("应正确处理文档数量为0的情况")
    void shouldHandleZeroDocumentCount() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(0);
        setupDataResourceMock(false, JSON_DATA);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("应正确处理文档数量小于阈值的情况")
    void shouldLoadWhenDocumentCountBelowThreshold() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(15000);
        setupDataResourceMock(false, JSON_DATA);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("应正确处理边界值20000")
    void shouldHandleBoundaryValue() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME)).thenReturn(20000);
        setupDataResourceMock(false, JSON_DATA);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }

    @Test
    @DisplayName("当DocumentCountProvider不支持操作时应继续加载数据")
    void shouldContinueLoadingWhenDocumentCountProviderNotSupported() throws Exception {
        // Given
        when(documentCountProvider.getDocumentCount(INDEX_NAME))
            .thenThrow(new UnsupportedOperationException("当前VectorStore实现不支持文档数量查询"));
        setupDataResourceMock(false, JSON_DATA);

        // When
        ragDataLoader.run(applicationArguments);

        // Then
        verify(vectorStore).add(anyList());
        verify(documentCountProvider).getDocumentCount(INDEX_NAME);
    }



    /**
     * 设置数据资源Mock对象
     */
    private void setupDataResourceMock(boolean isGzipFile, Object data) throws IOException {
        when(dataResource.exists()).thenReturn(true);
        String filename = isGzipFile ? "beers.json.gz" : "beers.json";
        when(dataResource.getFilename()).thenReturn(filename);
        
        InputStream inputStream;
        if (data instanceof String) {
            inputStream = new ByteArrayInputStream(((String) data).getBytes());
        } else if (data instanceof byte[]) {
            inputStream = new ByteArrayInputStream((byte[]) data);
        } else {
            throw new IllegalArgumentException("不支持的数据类型");
        }
        
        when(dataResource.getInputStream()).thenReturn(inputStream);
    }

    /**
     * 创建GZIP压缩数据
     */
    private byte[] createGzipData(String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(content.getBytes());
        }
        return baos.toByteArray();
    }

}