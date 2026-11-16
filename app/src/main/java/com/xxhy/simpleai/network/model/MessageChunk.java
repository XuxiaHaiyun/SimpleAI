package com.xxhy.simpleai.network.model;

import com.xxhy.simpleai.network.model.ChatMessage.ToolCall;

import java.util.List;

/**
 * 流式响应的一个 chunk。参考 rikkahub MessageChunk 设计。
 * 包含增量内容、思考过程、token 用量、结束原因、工具调用。
 */
public class MessageChunk {

    private String id;
    private String model;
    private String deltaContent;
    private String deltaReasoning;
    private TokenUsage usage;
    private String finishReason;
    private List<ToolCall> toolCalls;

    public MessageChunk() {
    }

    public MessageChunk(String id, String model, String deltaContent, String deltaReasoning,
                        TokenUsage usage, String finishReason) {
        this.id = id;
        this.model = model;
        this.deltaContent = deltaContent;
        this.deltaReasoning = deltaReasoning;
        this.usage = usage;
        this.finishReason = finishReason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getDeltaContent() {
        return deltaContent;
    }

    public void setDeltaContent(String deltaContent) {
        this.deltaContent = deltaContent;
    }

    public String getDeltaReasoning() {
        return deltaReasoning;
    }

    public void setDeltaReasoning(String deltaReasoning) {
        this.deltaReasoning = deltaReasoning;
    }

    public TokenUsage getUsage() {
        return usage;
    }

    public void setUsage(TokenUsage usage) {
        this.usage = usage;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public boolean hasContent() {
        return deltaContent != null && !deltaContent.isEmpty();
    }

    public boolean hasReasoning() {
        return deltaReasoning != null && !deltaReasoning.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
