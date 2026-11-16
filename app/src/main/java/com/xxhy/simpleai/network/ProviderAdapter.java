package com.xxhy.simpleai.network;

import com.xxhy.simpleai.network.model.ChatRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 多服务商适配器。参考 rikkahub 的 buildChatCompletionRequest 中按 host 分支处理的设计。
 *
 * 根据不同的 API 服务商（DeepSeek/智谱 GLM/Moonshot/豆包/书生/SiliconFlow/阿里云百炼等）
 * 注入对应的推理参数（thinking / enable_thinking / reasoning_effort 等），
 * 并根据 host 决定是否启用 stream_options.include_usage。
 *
 * 兼容 Android 4.x，仅使用 JDK 与 Gson API。
 */
public final class ProviderAdapter {

    /** 推理级别，参考 rikkahub ReasoningLevel */
    public enum ReasoningLevel {
        /** 关闭推理 */
        OFF,
        /** 自动（默认行为，由模型决定） */
        AUTO,
        /** 低强度 */
        LOW,
        /** 中等强度 */
        MEDIUM,
        /** 高强度 */
        HIGH;

        public boolean isEnabled() {
            return this != OFF;
        }

        /** 转换为 OpenAI/DeepSeek-V4 的 effort 字符串 */
        public String effort() {
            switch (this) {
                case OFF:
                    return "none";
                case LOW:
                    return "low";
                case MEDIUM:
                    return "medium";
                case HIGH:
                    return "high";
                case AUTO:
                default:
                    return "auto";
            }
        }
    }

    private ProviderAdapter() {
    }

    /**
     * 提取 baseUrl 中的 host（小写）。
     *
     * @param baseUrl 完整 URL，如 https://api.deepseek.com
     * @return host，如 api.deepseek.com；解析失败返回空串
     */
    public static String hostOf(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "";
        }
        try {
            // 兼容 Android 4，使用 URI 解析
            String url = baseUrl.trim();
            if (!url.contains("://")) {
                url = "https://" + url;
            }
            URI uri = new URI(url);
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /**
     * 根据服务商特性对请求体进行后处理。
     * - 流式时按需注入 stream_options.include_usage（Mistral 不支持）
     * - 根据 host 与 reasoningLevel 注入推理参数
     *
     * @param request        已构造的请求体
     * @param baseUrl        服务商 baseUrl
     * @param reasoningLevel 推理级别
     */
    public static void apply(ChatRequest request, String baseUrl, ReasoningLevel reasoningLevel) {
        String host = hostOf(baseUrl);

        // 1. 流式时注入 stream_options.include_usage（Mistral 不支持）
        if (request.isStream() && !"api.mistral.ai".equals(host)) {
            request.setStreamOptions(new ChatRequest.StreamOptions(true));
        }

        // 2. 推理参数注入（仅当用户显式选择非 AUTO 级别，或显式关闭时）
        if (reasoningLevel == ReasoningLevel.AUTO) {
            // AUTO 时不主动注入，使用服务商默认行为
            return;
        }

        boolean enabled = reasoningLevel.isEnabled();
        String effort = reasoningLevel.effort();

        if ("api.deepseek.com".equals(host)) {
            // DeepSeek-V4 系列
            request.setThinking(new ChatRequest.ThinkingConfig(enabled ? "enabled" : "disabled"));
            if (enabled) {
                request.setReasoningEffort(effort);
            }
        } else if ("open.bigmodel.cn".equals(host)
                || "api.moonshot.cn".equals(host)
                || "ark.cn-beijing.volces.com".equals(host)) {
            // 智谱 GLM-4.5+ / Moonshot Kimi / 豆包（火山方舟）
            request.setThinking(new ChatRequest.ThinkingConfig(enabled ? "enabled" : "disabled"));
        } else if ("chat.intern-ai.org.cn".equals(host)
                || "api.siliconflow.cn".equals(host)
                || "dashscope.aliyuncs.com".equals(host)
                || "aiping.cn".equals(host)) {
            // 书生 InternLM / SiliconFlow / 阿里云百炼 / aiping
            request.setEnableThinking(Boolean.valueOf(enabled));
        } else if ("openrouter.ai".equals(host)) {
            // OpenRouter 通过 reasoning.effort 控制
            // 这里通过 reasoning_effort 字段简化处理（OpenRouter 实际是 reasoning 对象，
            // 但为兼容 Android 4 与现有 ChatRequest 结构，使用 reasoning_effort）
            request.setReasoningEffort(enabled ? effort : "none");
        } else if ("integrate.api.nvidia.com".equals(host)
                || "opencode.ai".equals(host)) {
            // NVIDIA / OpenCode
            request.setReasoningEffort(effort);
        } else {
            // OpenAI 官方及其他兼容服务：completions API 支持 low/medium/high
            // OpenAI 不接受 "none"，转换为 "low"
            request.setReasoningEffort("none".equals(effort) ? "low" : effort);
        }
    }

    /**
     * 简单识别模型是否可能为推理模型（用于 UI 提示）。
     *
     * @param modelId 模型 ID
     * @return true 表示可能是推理模型
     */
    public static boolean isReasoningModel(String modelId) {
        if (modelId == null || modelId.isEmpty()) {
            return false;
        }
        String m = modelId.toLowerCase();
        return m.contains("r1")
                || m.contains("reasoning")
                || m.contains("thinking")
                || m.contains("o1")
                || m.contains("o3")
                || m.contains("o4")
                || m.contains("glm-4.5")
                || m.contains("glm-4.6")
                || m.contains("glm-5")
                || m.contains("kimi-k2")
                || m.contains("deepseek-v3.1")
                || m.contains("deepseek-v3.2")
                || m.contains("deepseek-v4")
                || m.contains("qwen3")
                || m.contains("qwq");
    }
}
