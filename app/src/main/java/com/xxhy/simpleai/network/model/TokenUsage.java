package com.xxhy.simpleai.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * Token 使用统计。对应 OpenAI 响应中的 usage 字段。
 */
public class TokenUsage {

    @SerializedName("prompt_tokens")
    private int promptTokens;

    @SerializedName("completion_tokens")
    private int completionTokens;

    @SerializedName("total_tokens")
    private int totalTokens;

    @SerializedName("cached_tokens")
    private int cachedTokens;

    public TokenUsage() {
    }

    public TokenUsage(int promptTokens, int completionTokens, int totalTokens, int cachedTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.cachedTokens = cachedTokens;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getCachedTokens() {
        return cachedTokens;
    }

    public void setCachedTokens(int cachedTokens) {
        this.cachedTokens = cachedTokens;
    }

    /** 累加另一个 usage（用于多次请求合并） */
    public TokenUsage merge(TokenUsage other) {
        if (other == null) return this;
        this.promptTokens += other.promptTokens;
        this.completionTokens += other.completionTokens;
        this.totalTokens += other.totalTokens;
        this.cachedTokens += other.cachedTokens;
        return this;
    }

    @Override
    public String toString() {
        return "↑" + promptTokens + " ↓" + completionTokens + " Σ" + totalTokens;
    }
}
