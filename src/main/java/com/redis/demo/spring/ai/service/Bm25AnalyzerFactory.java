package com.redis.demo.spring.ai.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * BM25 Analyzer 工厂：支持可配置的分词器切换。
 * 更换 Analyzer 后必须重建 BM25 索引，不能只在查询时切换。
 *
 * 配置 hybrid.bm25.analyzer=standard (默认) 或 smartcn（需要 lucene-smartcn 依赖）。
 */
@Component
public class Bm25AnalyzerFactory {

    private static final Logger logger = LoggerFactory.getLogger(Bm25AnalyzerFactory.class);

    private final String analyzerName;

    public Bm25AnalyzerFactory(
            @Value("${hybrid.bm25.analyzer:standard}") String analyzerName) {
        this.analyzerName = analyzerName;
    }

    /**
     * 创建配置指定的 Analyzer 实例。
     *
     * @return Lucene Analyzer
     */
    public Analyzer create() {
        return switch (analyzerName.toLowerCase()) {
            case "smartcn" -> createSmartChineseAnalyzer();
            case "standard" -> new StandardAnalyzer();
            default -> throw new IllegalArgumentException(
                    "Unsupported BM25 analyzer: " + analyzerName
                            + ". Supported: standard, smartcn");
        };
    }

    /**
     * 返回当前配置的 Analyzer 名称。
     */
    public String getAnalyzerName() {
        return analyzerName;
    }

    private Analyzer createSmartChineseAnalyzer() {
        try {
            // 使用反射加载 SmartChineseAnalyzer，避免未添加依赖时编译失败
            Class<?> clazz = Class.forName(
                    "org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer");
            Analyzer analyzer = (Analyzer) clazz.getDeclaredConstructor().newInstance();
            logger.info("Using SmartChineseAnalyzer for BM25");
            return analyzer;
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
                    "SmartChineseAnalyzer requested but lucene-smartcn dependency not found. "
                            + "Add lucene-smartcn to pom.xml or use analyzer=standard.",
                    ex);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to instantiate SmartChineseAnalyzer", ex);
        }
    }
}
