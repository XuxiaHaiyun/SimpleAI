package com.xxhy.simpleai.data.model;

/**
 * 模型配置实体。参考 rikkahub 的 Model 设计。
 *
 * 支持自定义 Header / Body 与提供商重写。
 * 模型级别的能力标记（视觉/推理）覆盖提供商默认值。
 */
public class ModelConfig {

    /** 模型类型 */
    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_EMBEDDING = "embedding";

    /** 输入/输出模态 */
    public static final String MODALITY_TEXT = "text";
    public static final String MODALITY_IMAGE = "image";

    /** 模型能力 */
    public static final String ABILITY_TOOL = "tool";
    public static final String ABILITY_REASONING = "reasoning";

    private long id;
    /** 所属 API 配置 ID */
    private long apiConfigId;
    /** 模型 ID（调用 API 时使用的名称） */
    private String modelId;
    /** 显示名称 */
    private String displayName;
    /** 模型类型：chat / image / embedding */
    private String type;
    /** 输入模态，逗号分隔：text,image */
    private String inputModalities;
    /** 输出模态，逗号分隔：text,image */
    private String outputModalities;
    /** 模型能力，逗号分隔：tool,reasoning */
    private String abilities;
    /** 自定义 Header JSON，形如 [{"name":"X-Custom","value":"v"}] */
    private String customHeaders;
    /** 自定义 Body JSON，形如 [{"key":"top_k","value":50}] */
    private String customBodies;
    /** 提供商重写 JSON（覆盖父级提供商配置），可为空 */
    private String providerOverwrite;
    private long createdAt;

    public ModelConfig() {
        this.type = TYPE_CHAT;
        this.inputModalities = MODALITY_TEXT;
        this.outputModalities = MODALITY_TEXT;
        this.abilities = "";
        this.customHeaders = "[]";
        this.customBodies = "[]";
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getApiConfigId() {
        return apiConfigId;
    }

    public void setApiConfigId(long apiConfigId) {
        this.apiConfigId = apiConfigId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getType() {
        return type == null ? TYPE_CHAT : type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getInputModalities() {
        return inputModalities == null ? MODALITY_TEXT : inputModalities;
    }

    public void setInputModalities(String inputModalities) {
        this.inputModalities = inputModalities;
    }

    public String getOutputModalities() {
        return outputModalities == null ? MODALITY_TEXT : outputModalities;
    }

    public void setOutputModalities(String outputModalities) {
        this.outputModalities = outputModalities;
    }

    public String getAbilities() {
        return abilities == null ? "" : abilities;
    }

    public void setAbilities(String abilities) {
        this.abilities = abilities;
    }

    public String getCustomHeaders() {
        return customHeaders == null ? "[]" : customHeaders;
    }

    public void setCustomHeaders(String customHeaders) {
        this.customHeaders = customHeaders;
    }

    public String getCustomBodies() {
        return customBodies == null ? "[]" : customBodies;
    }

    public void setCustomBodies(String customBodies) {
        this.customBodies = customBodies;
    }

    public String getProviderOverwrite() {
        return providerOverwrite;
    }

    public void setProviderOverwrite(String providerOverwrite) {
        this.providerOverwrite = providerOverwrite;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** 是否支持图片输入 */
    public boolean isVisionSupported() {
        return containsToken(getInputModalities(), MODALITY_IMAGE);
    }

    /** 是否为推理模型 */
    public boolean isReasoningSupported() {
        return containsToken(getAbilities(), ABILITY_REASONING);
    }

    /** 是否支持工具调用 */
    public boolean isToolSupported() {
        return containsToken(getAbilities(), ABILITY_TOOL);
    }

    /** 返回显示名，为空则返回模型 ID */
    public String getDisplayLabel() {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        return modelId == null ? "" : modelId;
    }

    private static boolean containsToken(String csv, String token) {
        if (csv == null || token == null) return false;
        for (String p : csv.split(",")) {
            if (token.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    /** 自定义 Header 条目 */
    public static class CustomHeader {
        public String name;
        public String value;

        public CustomHeader() {
        }

        public CustomHeader(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /** 自定义 Body 条目（value 为任意 JSON 值的字符串表示） */
    public static class CustomBody {
        public String key;
        public String value;

        public CustomBody() {
        }

        public CustomBody(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
