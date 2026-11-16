package com.xxhy.simpleai.network.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * OpenAI 兼容格式的聊天请求体。
 *
 * 参考 rikkahub 的 buildChatCompletionRequest 设计，支持：
 * - stream_options.include_usage（流式返回 token 用量）
 * - thinking（DeepSeek/GLM/Moonshot 等推理模型开关）
 * - reasoning_effort（OpenAI o 系列 / DeepSeek-V4 推理强度）
 */
public class ChatRequest {

    @SerializedName("model")
    private String model;

    @SerializedName("messages")
    private List<ChatMessage> messages;

    @SerializedName("stream")
    private boolean stream;

    @SerializedName("temperature")
    private Float temperature;

    @SerializedName("max_tokens")
    private Integer maxTokens;

    @SerializedName("stream_options")
    private StreamOptions streamOptions;

    @SerializedName("thinking")
    private ThinkingConfig thinking;

    @SerializedName("reasoning_effort")
    private String reasoningEffort;

    @SerializedName("enable_thinking")
    private Boolean enableThinking;

    @SerializedName("tools")
    private List<ToolDef> tools;

    @SerializedName("tool_choice")
    private String toolChoice;

    public ChatRequest(String model, List<ChatMessage> messages, boolean stream,
                       float temperature, int maxTokens) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    /** 使用模型默认配置：temperature 与 max_tokens 为 null 时不参与序列化 */
    public ChatRequest(String model, List<ChatMessage> messages, boolean stream) {
        this.model = model;
        this.messages = messages;
        this.stream = stream;
        this.temperature = null;
        this.maxTokens = null;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public StreamOptions getStreamOptions() {
        return streamOptions;
    }

    public void setStreamOptions(StreamOptions streamOptions) {
        this.streamOptions = streamOptions;
    }

    public ThinkingConfig getThinking() {
        return thinking;
    }

    public void setThinking(ThinkingConfig thinking) {
        this.thinking = thinking;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    public List<ToolDef> getTools() {
        return tools;
    }

    public void setTools(List<ToolDef> tools) {
        this.tools = tools;
    }

    public String getToolChoice() {
        return toolChoice;
    }

    public void setToolChoice(String toolChoice) {
        this.toolChoice = toolChoice;
    }

    /** 流式选项，控制是否返回 usage */
    public static class StreamOptions {
        @SerializedName("include_usage")
        private boolean includeUsage;

        public StreamOptions(boolean includeUsage) {
            this.includeUsage = includeUsage;
        }

        public boolean isIncludeUsage() {
            return includeUsage;
        }

        public void setIncludeUsage(boolean includeUsage) {
            this.includeUsage = includeUsage;
        }
    }

    /**
     * 推理开关配置（DeepSeek/GLM/Moonshot/豆包通用格式）。
     * 形如：{"type":"enabled"} 或 {"type":"disabled"}
     */
    public static class ThinkingConfig {
        @SerializedName("type")
        private String type;

        public ThinkingConfig(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * 工具定义（OpenAI function calling 格式）。
     * 形如：{"type":"function","function":{"name":"search_web","description":"...","parameters":{...}}}
     */
    public static class ToolDef {
        @SerializedName("type")
        private String type;

        @SerializedName("function")
        private FunctionDef function;

        public ToolDef(String type, FunctionDef function) {
            this.type = type;
            this.function = function;
        }

        public String getType() {
            return type;
        }

        public FunctionDef getFunction() {
            return function;
        }
    }

    /** 工具函数定义 */
    public static class FunctionDef {
        @SerializedName("name")
        private String name;

        @SerializedName("description")
        private String description;

        @SerializedName("parameters")
        private Object parameters;

        public FunctionDef(String name, String description, Object parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }
}
