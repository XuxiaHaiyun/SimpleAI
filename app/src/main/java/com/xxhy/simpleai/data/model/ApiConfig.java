package com.xxhy.simpleai.data.model;

/**
 * API 服务商配置实体。API Key 以密文存储。
 *
 * 参考 rikkahub 的 ProviderSetting 设计：
 * - 区分服务商类型（OpenAI / Claude / Google）
 * - 标记模型能力（视觉 / 推理）
 * - 控制历史推理是否带入上下文
 */
public class ApiConfig {

    /** 服务商类型 */
    public static final String TYPE_OPENAI = "openai";
    public static final String TYPE_CLAUDE = "claude";
    public static final String TYPE_GOOGLE = "google";

    private long id;
    private String name;
    private String baseUrl;
    private String apiPath;
    /** 加密后的 API Key */
    private String apiKeyEncrypted;
    /** 逗号分隔的模型列表 */
    private String models;
    private float temperature;
    private int maxTokens;
    private int contextLength;
    private boolean active;
    private long createdAt;

    /** 服务商类型：openai / claude / google */
    private String providerType;
    /** 是否启用 */
    private boolean enabled;
    /** 是否支持视觉（图片输入） */
    private boolean visionSupported;
    /** 是否为推理模型（显示思考深度调节） */
    private boolean reasoningSupported;
    /** 是否启用网络搜索 */
    private boolean searchEnabled;
    /** 是否使用 Response API（OpenAI） */
    private boolean useResponseApi;
    /** 是否启用账户余额获取 */
    private boolean balanceEnabled;
    /** 余额获取 API 路径 */
    private String balanceApiPath;
    /** 余额获取结果 JSON 路径，如 data.total_usage */
    private String balanceResultPath;

    public ApiConfig() {
        this.apiPath = "/v1/chat/completions";
        this.temperature = 0.7f;
        this.maxTokens = 2048;
        this.contextLength = 20;
        this.providerType = TYPE_OPENAI;
        this.enabled = true;
        this.visionSupported = false;
        this.reasoningSupported = false;
        this.searchEnabled = false;
        this.useResponseApi = false;
        this.balanceEnabled = false;
        this.balanceApiPath = "/credits";
        this.balanceResultPath = "data.total_usage";
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public void setApiKeyEncrypted(String apiKeyEncrypted) {
        this.apiKeyEncrypted = apiKeyEncrypted;
    }

    public String getModels() {
        return models;
    }

    public void setModels(String models) {
        this.models = models;
    }

    /** 返回模型数组 */
    public String[] getModelArray() {
        if (models == null || models.trim().isEmpty()) {
            return new String[0];
        }
        String[] parts = models.split(",");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        return parts;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getContextLength() {
        return contextLength;
    }

    public void setContextLength(int contextLength) {
        this.contextLength = contextLength;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getProviderType() {
        return providerType == null ? TYPE_OPENAI : providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isVisionSupported() {
        return visionSupported;
    }

    public void setVisionSupported(boolean visionSupported) {
        this.visionSupported = visionSupported;
    }

    public boolean isReasoningSupported() {
        return reasoningSupported;
    }

    public void setReasoningSupported(boolean reasoningSupported) {
        this.reasoningSupported = reasoningSupported;
    }

    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    public void setSearchEnabled(boolean searchEnabled) {
        this.searchEnabled = searchEnabled;
    }

    public boolean isUseResponseApi() {
        return useResponseApi;
    }

    public void setUseResponseApi(boolean useResponseApi) {
        this.useResponseApi = useResponseApi;
    }

    public boolean isBalanceEnabled() {
        return balanceEnabled;
    }

    public void setBalanceEnabled(boolean balanceEnabled) {
        this.balanceEnabled = balanceEnabled;
    }

    public String getBalanceApiPath() {
        return balanceApiPath == null ? "/credits" : balanceApiPath;
    }

    public void setBalanceApiPath(String balanceApiPath) {
        this.balanceApiPath = balanceApiPath;
    }

    public String getBalanceResultPath() {
        return balanceResultPath == null ? "data.total_usage" : balanceResultPath;
    }

    public void setBalanceResultPath(String balanceResultPath) {
        this.balanceResultPath = balanceResultPath;
    }
}
