package com.xxhy.simpleai.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xxhy.simpleai.App;
import com.xxhy.simpleai.data.model.ApiConfig;
import com.xxhy.simpleai.data.model.Message;
import com.xxhy.simpleai.data.model.ModelConfig;
import com.xxhy.simpleai.network.model.ChatMessage;
import com.xxhy.simpleai.network.model.ChatRequest;
import com.xxhy.simpleai.network.model.MessageChunk;
import com.xxhy.simpleai.network.model.TokenUsage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI API 客户端，基于 OkHttp 实现 SSE 流式对话。
 * 兼容 Android 4.x（OkHttp 3.12.x）。
 *
 * 参考 rikkahub 的 ChatCompletionsAPI 设计，集成：
 * - KeyRoulette：多 API Key 轮询
 * - ErrorParser：错误响应递归解析
 * - ProviderAdapter：多服务商推理参数适配
 * - SSEParser：增强解析 reasoning_content/usage/finish_reason
 */
public class ApiClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String SSE_ACCEPT = "text/event-stream";
    /** tool_calls 递归最大深度，防止模型反复请求搜索导致死循环 */
    private static final int MAX_TOOL_CALL_DEPTH = 5;
    /** 单次搜索同步等待上限（毫秒） */
    private static final long SEARCH_TIMEOUT_MS = 15_000L;

    private static volatile ApiClient instance;

    private final OkHttpClient client;
    private final Gson gson;
    private final KeyRoulette keyRoulette;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        gson = new GsonBuilder()
                .registerTypeAdapter(ChatMessage.class, new ChatMessage.Serializer())
                .create();
        keyRoulette = new KeyRoulette();
    }

    public static ApiClient get() {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) {
                    instance = new ApiClient();
                }
            }
        }
        return instance;
    }

    /** 检查网络是否可用 */
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    /**
     * 发起流式对话请求（默认 AUTO 推理级别）。
     */
    public Call streamChat(ApiConfig config, String model, List<Message> messages,
                           ChatStreamListener listener) {
        return streamChat(config, null, model, messages,
                ProviderAdapter.ReasoningLevel.AUTO, null, false, listener);
    }

    /**
     * 发起流式对话请求（带附件与联网搜索）。
     *
     * @param config           API 配置
     * @param model            使用的模型名称
     * @param messages         历史消息列表
     * @param reasoningLevel   推理级别
     * @param imageDataUrls    当前用户消息附带的图片数据 URL 列表（可为空）
     * @param webSearchEnabled 是否启用联网搜索工具（模型通过 tool_call 主动搜索）
     * @param listener         回调（主线程）
     */
    public Call streamChat(ApiConfig config, String model, List<Message> messages,
                           ProviderAdapter.ReasoningLevel reasoningLevel,
                           List<String> imageDataUrls, boolean webSearchEnabled,
                           final ChatStreamListener listener) {
        return streamChat(config, null, model, messages, reasoningLevel,
                imageDataUrls, webSearchEnabled, listener);
    }

    /**
     * 发起流式对话请求（完整参数，支持模型级配置与自定义 Header/Body）。
     *
     * @param config           API 配置
     * @param modelConfig      模型配置（可为空，提供时应用自定义 Header/Body 与能力覆盖）
     * @param model            使用的模型名称
     * @param messages         历史消息列表
     * @param reasoningLevel   推理级别
     * @param imageDataUrls    当前用户消息附带的图片数据 URL 列表（可为空）
     * @param webSearchEnabled 是否启用联网搜索工具
     * @param listener         回调（主线程）
     */
    public Call streamChat(ApiConfig config, ModelConfig modelConfig, String model,
                           List<Message> messages,
                           ProviderAdapter.ReasoningLevel reasoningLevel,
                           List<String> imageDataUrls, boolean webSearchEnabled,
                           final ChatStreamListener listener) {
        // 解密 API Key
        String apiKey = "";
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isEmpty()) {
            try {
                apiKey = App.get().getSettingsManager().getCrypto()
                        .decrypt(config.getApiKeyEncrypted());
            } catch (Exception e) {
                // 解密失败时可能是明文存储（加密降级），直接使用原值
                apiKey = config.getApiKeyEncrypted();
            }
        }
        apiKey = keyRoulette.next(apiKey);

        // 构建请求消息
        List<ChatMessage> chatMessages = new ArrayList<>();

        // 联网搜索工具：注入 tools 定义，由模型决定何时搜索
        if (webSearchEnabled) {
            // 不再预搜索，而是通过 tool_call 让模型主动请求搜索
        }

        // 构建历史消息（始终保留思考记忆：assistant 历史消息的 reasoning 一并带入上下文）
        int lastIndex = messages.size() - 1;
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            // 最后一条用户消息若带图片，使用多模态格式
            if (i == lastIndex && m.isUser() && imageDataUrls != null && !imageDataUrls.isEmpty()) {
                chatMessages.add(new ChatMessage(m.getRole(), m.getContent(), imageDataUrls));
            } else {
                String content = m.getContent();
                // 始终保留思考记忆：将历史 assistant 消息的 reasoning 拼接到内容前
                if (m.isAssistant() && m.getReasoning() != null && !m.getReasoning().isEmpty()) {
                    content = "<think>" + m.getReasoning() + "</think>\n" + (content == null ? "" : content);
                }
                chatMessages.add(new ChatMessage(m.getRole(), content));
            }
        }

        // 使用模型默认配置：不设置 temperature/max_tokens，由模型决定
        ChatRequest request = new ChatRequest(
                model != null ? model : "",
                chatMessages, true);

        // 服务商适配：注入 stream_options / thinking / reasoning_effort
        ProviderAdapter.apply(request, config.getBaseUrl(), reasoningLevel);

        // 注入联网搜索工具定义
        if (webSearchEnabled) {
            request.setTools(buildSearchTools());
            request.setToolChoice("auto");
        }

        // 应用模型级自定义 Body（合并到请求 JSON）
        if (modelConfig != null) {
            applyCustomBodies(request, modelConfig.getCustomBodies());
        }

        String json = gson.toJson(request);

        // 拼接 URL
        String baseUrl = config.getBaseUrl() == null ? "" : config.getBaseUrl().trim();
        String apiPath = config.getApiPath() == null ? "/v1/chat/completions" : config.getApiPath().trim();
        String url = buildUrl(baseUrl, apiPath);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", SSE_ACCEPT)
                .post(RequestBody.create(JSON, json));

        // 应用模型级自定义 Header
        if (modelConfig != null) {
            applyCustomHeaders(builder, modelConfig.getCustomHeaders());
        }

        // 构建流式上下文（用于 tool_calls 循环）
        StreamContext ctx = new StreamContext();
        ctx.chatMessages = chatMessages;
        ctx.config = config;
        ctx.modelConfig = modelConfig;
        ctx.model = model;
        ctx.apiKey = apiKey;
        ctx.baseUrl = baseUrl;
        ctx.apiPath = apiPath;
        ctx.reasoningLevel = reasoningLevel;
        ctx.webSearchEnabled = webSearchEnabled;

        return executeStreamCall(builder.build(), ctx, listener);
    }

    /** 将自定义 Header 应用到请求构建器 */
    private void applyCustomHeaders(Request.Builder builder, String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) return;
        try {
            com.google.gson.JsonArray arr = gson.fromJson(headersJson, com.google.gson.JsonArray.class);
            if (arr == null) return;
            for (com.google.gson.JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                String name = obj.has("name") && !obj.get("name").isJsonNull()
                        ? obj.get("name").getAsString() : null;
                String value = obj.has("value") && !obj.get("value").isJsonNull()
                        ? obj.get("value").getAsString() : null;
                if (name != null && !name.isEmpty()) {
                    builder.header(name, value == null ? "" : value);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 将自定义 Body 合并到请求对象（覆盖同名字段） */
    private void applyCustomBodies(ChatRequest request, String bodiesJson) {
        if (bodiesJson == null || bodiesJson.isEmpty()) return;
        try {
            com.google.gson.JsonArray arr = gson.fromJson(bodiesJson, com.google.gson.JsonArray.class);
            if (arr == null) return;
            // 通过 Gson 合并：序列化 request → 合并自定义字段 → 反序列化
            com.google.gson.JsonObject root = gson.toJsonTree(request).getAsJsonObject();
            for (com.google.gson.JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                String key = obj.has("key") && !obj.get("key").isJsonNull()
                        ? obj.get("key").getAsString() : null;
                if (key == null || key.isEmpty()) continue;
                com.google.gson.JsonElement value = obj.has("value") ? obj.get("value") : null;
                root.add(key, value);
            }
            // 反序列化回 request 对象（保留已知字段）
            ChatRequest merged = gson.fromJson(root, ChatRequest.class);
            // 复制合并后的字段（覆盖所有可配置字段）
            request.setTemperature(merged.getTemperature());
            request.setMaxTokens(merged.getMaxTokens());
            request.setThinking(merged.getThinking());
            request.setReasoningEffort(merged.getReasoningEffort());
            request.setEnableThinking(merged.getEnableThinking());
            request.setStreamOptions(merged.getStreamOptions());
            request.setTools(merged.getTools());
            request.setToolChoice(merged.getToolChoice());
        } catch (Exception ignored) {
        }
    }

    /** 执行 SSE 流式请求，支持 tool_calls 循环（模型请求搜索 → 本地执行 → 继续对话） */
    private Call executeStreamCall(Request httpRequest, final StreamContext ctx,
                                  final ChatStreamListener listener) {
        final StringBuilder fullContent = new StringBuilder();
        final StringBuilder fullReasoning = new StringBuilder();
        final TokenUsage[] usageHolder = new TokenUsage[1];
        final String[] finishHolder = new String[1];
        // tool_calls 增量拼接
        final java.util.Map<String, ChatMessage.ToolCall> toolCallMap = new java.util.LinkedHashMap<>();

        Call call = client.newCall(httpRequest);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    notifyComplete(listener, fullContent.toString(),
                            usageHolder[0], finishHolder[0]);
                } else {
                    notifyError(listener, "网络请求失败: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                if (!response.isSuccessful()) {
                    String body = "";
                    try {
                        body = response.body() != null ? response.body().string() : "";
                    } catch (Exception ignored) {
                    }
                    String parsed = ErrorParser.parse(body);
                    notifyError(listener, "请求失败 (" + response.code() + "): " + truncate(parsed, 300));
                    return;
                }
                if (response.body() == null) {
                    notifyError(listener, "响应体为空");
                    return;
                }
                try {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (call.isCanceled()) {
                            break;
                        }
                        SSEParser.ParseResult result = SSEParser.parse(line);
                        if (result.shouldIgnore()) {
                            continue;
                        }
                        if (result.done) {
                            break;
                        }
                        if (result.error != null) {
                            notifyError(listener, truncate(result.error, 300));
                            return;
                        }
                        MessageChunk chunk = result.chunk;
                        if (chunk == null) {
                            continue;
                        }

                        if (chunk.hasContent()) {
                            final String delta = chunk.getDeltaContent();
                            fullContent.append(delta);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onDelta(delta);
                                }
                            });
                        }

                        if (chunk.hasReasoning()) {
                            final String rDelta = chunk.getDeltaReasoning();
                            fullReasoning.append(rDelta);
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onReasoning(rDelta);
                                }
                            });
                        }

                        // 拼接 tool_calls 增量
                        if (chunk.hasToolCalls()) {
                            for (ChatMessage.ToolCall tc : chunk.getToolCalls()) {
                                String tcId = tc.getId();
                                ChatMessage.ToolCall existing = toolCallMap.get(tcId);
                                if (existing == null) {
                                    // 新的 tool_call
                                    toolCallMap.put(tcId, tc);
                                } else {
                                    // 增量拼接 arguments
                                    String oldArgs = existing.getFunction().getArguments();
                                    String newArgs = tc.getFunction().getArguments();
                                    String mergedName = tc.getFunction().getName();
                                    if (mergedName == null || mergedName.isEmpty()) {
                                        mergedName = existing.getFunction().getName();
                                    }
                                    toolCallMap.put(tcId, new ChatMessage.ToolCall(
                                            tcId,
                                            tc.getType() == null ? existing.getType() : tc.getType(),
                                            new ChatMessage.FunctionCall(mergedName, oldArgs + newArgs)));
                                }
                            }
                        }

                        if (chunk.getUsage() != null) {
                            if (usageHolder[0] == null) {
                                usageHolder[0] = chunk.getUsage();
                            } else {
                                usageHolder[0].merge(chunk.getUsage());
                            }
                        }

                        if (chunk.getFinishReason() != null) {
                            finishHolder[0] = chunk.getFinishReason();
                        }
                    }

                    // 处理 tool_calls：模型请求调用工具
                    if ("tool_calls".equals(finishHolder[0]) && !toolCallMap.isEmpty()) {
                        handleToolCalls(ctx, toolCallMap, fullContent, fullReasoning,
                                usageHolder, finishHolder, listener);
                    } else {
                        notifyComplete(listener, fullContent.toString(),
                                usageHolder[0], finishHolder[0]);
                    }
                } catch (Exception e) {
                    if (call.isCanceled()) {
                        notifyComplete(listener, fullContent.toString(),
                                usageHolder[0], finishHolder[0]);
                    } else {
                        notifyError(listener, "读取流失败: " + e.getMessage());
                    }
                } finally {
                    response.close();
                }
            }
        });

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onStart();
            }
        });

        return call;
    }

    /**
     * 处理模型返回的 tool_calls：执行搜索工具，将结果追加到消息列表，继续对话。
     */
    private void handleToolCalls(final StreamContext ctx,
                                 final java.util.Map<String, ChatMessage.ToolCall> toolCallMap,
                                 final StringBuilder fullContent, final StringBuilder fullReasoning,
                                 final TokenUsage[] usageHolder, final String[] finishHolder,
                                 final ChatStreamListener listener) {
        // 递归深度保护：超过上限时强制结束，避免模型反复请求搜索导致死循环
        if (ctx.toolCallDepth >= MAX_TOOL_CALL_DEPTH) {
            notifyComplete(listener, fullContent.toString(), usageHolder[0], finishHolder[0]);
            return;
        }
        ctx.toolCallDepth++;
        // 1. 将 assistant 的 tool_calls 消息追加到上下文
        java.util.List<ChatMessage.ToolCall> toolCalls = new java.util.ArrayList<>(toolCallMap.values());
        ChatMessage assistantMsg = new ChatMessage("assistant", fullContent.toString());
        assistantMsg.setToolCalls(toolCalls);
        ctx.chatMessages.add(assistantMsg);

        // 2. 执行每个 tool_call，追加 tool 消息
        for (ChatMessage.ToolCall tc : toolCalls) {
            String fnName = tc.getFunction().getName();
            String fnArgs = tc.getFunction().getArguments();
            String toolResult = "";

            if ("search_web".equals(fnName)) {
                // 解析搜索关键词
                String query = parseSearchQuery(fnArgs);
                // 通知 UI 正在搜索
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onDelta("\n🔍 正在搜索: " + query + " ...\n");
                    }
                });
                // 同步执行搜索
                toolResult = executeSearchSync(query);
            } else {
                toolResult = "未知工具: " + fnName;
            }

            // 追加 tool 消息
            ChatMessage toolMsg = new ChatMessage("tool", toolResult);
            toolMsg.setToolCallId(tc.getId());
            toolMsg.setName(fnName);
            ctx.chatMessages.add(toolMsg);
        }

        // 3. 重新构建请求（保留 tools 定义），继续对话
        ChatRequest nextRequest = new ChatRequest(
                ctx.model != null ? ctx.model : "",
                ctx.chatMessages, true);
        ProviderAdapter.apply(nextRequest, ctx.baseUrl, ctx.reasoningLevel);
        if (ctx.webSearchEnabled) {
            nextRequest.setTools(buildSearchTools());
            nextRequest.setToolChoice("auto");
        }

        String json = gson.toJson(nextRequest);
        String url = buildUrl(ctx.baseUrl, ctx.apiPath);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + ctx.apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", SSE_ACCEPT)
                .post(RequestBody.create(JSON, json));

        // 递归调用，继续处理（可能再次触发 tool_calls）
        executeStreamCall(builder.build(), ctx, listener);
    }

    /** 从 tool_call arguments 中解析搜索关键词 */
    private String parseSearchQuery(String arguments) {
        if (arguments == null || arguments.isEmpty()) return "";
        try {
            com.google.gson.JsonObject obj = gson.fromJson(arguments, com.google.gson.JsonObject.class);
            if (obj != null && obj.has("query")) {
                return obj.get("query").getAsString();
            }
        } catch (Exception e) {
            // 如果不是 JSON，直接作为关键词
            return arguments;
        }
        return arguments;
    }

    /** 同步执行搜索（在后台线程调用） */
    private String executeSearchSync(String query) {
        try {
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final String[] result = new String[1];
            BingSearchClient.get().search(query, new BingSearchClient.SearchCallback() {
                @Override
                public void onSuccess(String formattedResults) {
                    result[0] = formattedResults;
                    latch.countDown();
                }

                @Override
                public void onError(String message) {
                    result[0] = "搜索失败: " + message;
                    latch.countDown();
                }
            });
            // 等待搜索完成（最多 SEARCH_TIMEOUT_MS 毫秒），避免忙等浪费 CPU
            if (!latch.await(SEARCH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                return "搜索超时";
            }
            return result[0] != null ? result[0] : "搜索超时";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "搜索中断";
        } catch (Exception e) {
            return "搜索异常: " + e.getMessage();
        }
    }

    private void notifyComplete(final ChatStreamListener listener, final String content,
                                final TokenUsage usage, final String finishReason) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onComplete(content, usage, finishReason);
            }
        });
    }

    private void notifyError(final ChatStreamListener listener, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onError(msg);
            }
        });
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /** 拼接 BaseUrl + path，处理末尾/前导斜杠，兼容旧版智谱 GLM 预设 */
    private static String buildUrl(String baseUrl, String apiPath) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = apiPath == null ? "" : apiPath.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        // 兼容旧版智谱 GLM 预设：Base URL 以 /v4 结尾而 API Path 为 /v1/chat/completions
        if (base.endsWith("/v4") && "/v1/chat/completions".equals(path)) {
            path = "/chat/completions";
        }
        return base + path;
    }

    /** 构建联网搜索工具定义（OpenAI function calling 格式） */
    private static java.util.List<ChatRequest.ToolDef> buildSearchTools() {
        java.util.List<ChatRequest.ToolDef> tools = new java.util.ArrayList<>();
        // search_web 工具
        java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("type", "object");
        java.util.Map<String, Object> queryProp = new java.util.LinkedHashMap<>();
        queryProp.put("type", "string");
        queryProp.put("description", "搜索关键词");
        java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("query", queryProp);
        params.put("properties", properties);
        java.util.List<String> required = new java.util.ArrayList<>();
        required.add("query");
        params.put("required", required);

        tools.add(new ChatRequest.ToolDef("function",
                new ChatRequest.FunctionDef("search_web",
                        "搜索互联网获取最新信息。当需要查找实时数据、新闻、事实或任何不确定的信息时使用此工具。",
                        params)));
        return tools;
    }

    /** 流式上下文，用于 tool_calls 循环时保留状态 */
    private static class StreamContext {
        List<ChatMessage> chatMessages;
        ApiConfig config;
        ModelConfig modelConfig;
        String model;
        String apiKey;
        String baseUrl;
        String apiPath;
        ProviderAdapter.ReasoningLevel reasoningLevel;
        boolean webSearchEnabled;
        /** 当前 tool_calls 递归深度，防止模型反复请求搜索导致死循环 */
        int toolCallDepth = 0;
    }

    /** 测试连接回调 */
    public interface TestCallback {
        void onSuccess();
        void onError(String message);
    }

    /** 在线模型列表回调 */
    public interface ModelsCallback {
        void onSuccess(java.util.List<String> modelIds);
        void onError(String message);
    }

    /**
     * 测试 API 连接。通过请求 models 接口验证 API Key 与 Base URL 是否有效。
     */
    public void testConnection(ApiConfig config, final TestCallback callback) {
        Request request = buildModelsRequest(config, new TestCallback() {
            @Override public void onError(String msg) { callback.onError(msg); }
            @Override public void onSuccess() { callback.onSuccess(); }
        });
        if (request == null) return;
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (response.isSuccessful()) {
                        mainHandler.post(callback::onSuccess);
                    } else {
                        String body = response.body() != null ? response.body().string() : "";
                        String parsed = ErrorParser.parse(body);
                        mainHandler.post(() -> callback.onError(
                                "请求失败 (" + response.code() + "): " + truncate(parsed, 300)));
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("读取响应失败: " + e.getMessage()));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 获取在线模型列表。请求 models 接口并解析 data[].id。
     */
    public void fetchModels(ApiConfig config, final ModelsCallback callback) {
        Request request = buildModelsRequest(config, new TestCallback() {
            @Override public void onError(String msg) { callback.onError(msg); }
            @Override public void onSuccess() {}
        });
        if (request == null) return;
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("网络请求失败: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        String body = response.body() != null ? response.body().string() : "";
                        String parsed = ErrorParser.parse(body);
                        final String err = "请求失败 (" + response.code() + "): " + truncate(parsed, 300);
                        mainHandler.post(() -> callback.onError(err));
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : "";
                    java.util.List<String> ids = parseModelList(body);
                    mainHandler.post(() -> callback.onSuccess(ids));
                } catch (Exception e) {
                    mainHandler.post(() -> callback.onError("解析失败: " + e.getMessage()));
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * 构建 models 端点请求。解密失败时通过 onError 通知并返回 null。
     */
    private Request buildModelsRequest(ApiConfig config, final TestCallback onError) {
        String apiKey = decryptApiKey(config);
        if (apiKey == null) {
            onError.onError("API Key 解密失败");
            return null;
        }
        String modelsPath = inferModelsPath(config.getApiPath());
        String url = buildUrl(config.getBaseUrl(), modelsPath);
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .get()
                .build();
    }

    /** 解析 /v1/models 响应，提取 data[].id */
    private java.util.List<String> parseModelList(String body) {
        java.util.List<String> ids = new ArrayList<>();
        if (body == null || body.isEmpty()) return ids;
        try {
            com.google.gson.JsonObject root = gson.fromJson(body, com.google.gson.JsonObject.class);
            if (root == null || !root.has("data")) return ids;
            com.google.gson.JsonElement dataEl = root.get("data");
            if (!dataEl.isJsonArray()) return ids;
            for (com.google.gson.JsonElement el : dataEl.getAsJsonArray()) {
                if (el.isJsonObject()) {
                    com.google.gson.JsonObject obj = el.getAsJsonObject();
                    if (obj.has("id") && !obj.get("id").isJsonNull()) {
                        ids.add(obj.get("id").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    /** 解密 API Key，失败时回退为原值（兼容明文存储降级） */
    private String decryptApiKey(ApiConfig config) {
        if (config.getApiKeyEncrypted() == null || config.getApiKeyEncrypted().isEmpty()) {
            return "";
        }
        try {
            return App.get().getSettingsManager().getCrypto()
                    .decrypt(config.getApiKeyEncrypted());
        } catch (Exception e) {
            // 解密失败时可能是明文存储（加密降级），直接返回原值
            return config.getApiKeyEncrypted();
        }
    }

    /** 根据 API Path 推断 models 端点路径，如 /v4/chat/completions → /v4/models */
    private String inferModelsPath(String apiPath) {
        if (apiPath != null && apiPath.matches("/v\\d+/.*")) {
            String version = apiPath.substring(0, apiPath.indexOf('/', 1));
            return version + "/models";
        }
        return "/v1/models";
    }
}
