package com.redis.demo.spring.ai.agent;

import com.redis.demo.spring.ai.model.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上下文组装器：按预算去重、限长，并为每条证据保留引用标识（documentId/来源/页码），
 * 避免仅拼接文本后丢失引用能力。
 *
 * MVP 使用字符数控制预算；生产代码建议改用 Token 计数器，
 * 从 chunkTokenNum、模型最大上下文、系统提示和回答预留 Token 计算实际可用窗口。
 */
@Component
public class ContextBuilder {

    public Context build(List<Evidence> evidence, int maxCharacters) {
        Map<String, Evidence> unique = new LinkedHashMap<>();

        for (Evidence item : evidence) {
            String id = item.documentId() == null
                    ? item.source() + ":" + item.content().hashCode()
                    : item.documentId();
            unique.putIfAbsent(id, item);
        }

        StringBuilder text = new StringBuilder();
        List<Evidence> included = new ArrayList<>();

        for (Evidence item : unique.values()) {
            String id = item.documentId() == null ? "unknown" : item.documentId();
            String block = """
                    [证据 %s | 来源=%s | 页码=%s]
                    %s

                    """.formatted(
                    id,
                    item.source() == null ? "unknown" : item.source(),
                    item.page() == null ? "unknown" : item.page(),
                    item.content() == null ? "" : item.content()
            );

            if (text.length() + block.length() > maxCharacters) {
                break;
            }

            text.append(block);
            included.add(item);
        }

        return new Context(text.toString(), List.copyOf(included));
    }

    /**
     * 组装完成的上下文。
     *
     * @param text     带引用标记的证据文本块
     * @param evidence 实际纳入上下文的证据（用于生成后的引用校验）
     */
    public record Context(String text, List<Evidence> evidence) {
    }
}
