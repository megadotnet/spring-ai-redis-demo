package com.redis.demo.spring.ai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片提取器 - 从Markdown文本中提取图片引用
 * 基于Python实现 [4](#5-3)
 */
public class ImageExtractor {

    public List<ImageReference> extractImageReferences(String markdownText) {
        List<ImageReference> references = new ArrayList<>();
        String[] lines = markdownText.split("\n");

        // Markdown图片格式: ![alt](url)
        Pattern markdownPattern = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)");

        // HTML图片格式: <img src="url" alt="alt">
        Pattern htmlPattern = Pattern.compile("src=[\"']([^\"'>\\s]+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern altPattern = Pattern.compile("alt=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 提取Markdown格式图片
            Matcher markdownMatcher = markdownPattern.matcher(line);
            while (markdownMatcher.find()) {
                String altText = markdownMatcher.group(1);
                String url = markdownMatcher.group(2);

                references.add(new ImageReference(url, i, "markdown", altText));
            }

            // 提取HTML格式图片
            Matcher htmlMatcher = htmlPattern.matcher(line);
            while (htmlMatcher.find()) {
                String url = htmlMatcher.group(1);

                // 尝试提取alt文本
                Matcher altMatcher = altPattern.matcher(line);
                String altText = altMatcher.find() ? altMatcher.group(1) : "";

                references.add(new ImageReference(url, i, "html", altText));
            }
        }

        return references;
    }
}