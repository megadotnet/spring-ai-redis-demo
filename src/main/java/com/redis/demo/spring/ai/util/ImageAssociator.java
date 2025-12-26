package com.redis.demo.spring.ai.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片关联器 - 将图片与文本块关联
 * 基于Python实现 [5](#5-4)
 */
public class ImageAssociator {

    public void associateImagesWithChunks(List<TextChunk> chunks, List<ImageReference> imageRefs) {
        for (TextChunk chunk : chunks) {
            List<String> associatedImages = new ArrayList<>();

            for (ImageReference ref : imageRefs) {
                // 检查图片是否在文本块的行范围内
                if (chunk.getStartLine() <= ref.getLine() && ref.getLine() <= chunk.getEndLine()) {
                    associatedImages.add(ref.getUrl());
                }
            }

            chunk.setAssociatedImages(associatedImages);
        }
    }

    /**
     * 支持图片上下文扩展的关联方法
     * 基于Python实现 [6](#5-5)
     */
    public void associateImagesWithContext(List<TextChunk> chunks, List<ImageReference> imageRefs, int contextSize) {
        for (TextChunk chunk : chunks) {
            List<String> associatedImages = new ArrayList<>();

            for (ImageReference ref : imageRefs) {
                // 扩展上下文范围
                int startLine = Math.max(0, chunk.getStartLine() - contextSize);
                int endLine = chunk.getEndLine() + contextSize;

                if (startLine <= ref.getLine() && ref.getLine() <= endLine) {
                    associatedImages.add(ref.getUrl());
                }
            }

            chunk.setAssociatedImages(associatedImages);
        }
    }
}