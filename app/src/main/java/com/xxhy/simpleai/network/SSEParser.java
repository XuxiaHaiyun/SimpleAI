package com.xxhy.simpleai.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xxhy.simpleai.network.model.ChatMessage;
import com.xxhy.simpleai.network.model.MessageChunk;
import com.xxhy.simpleai.network.model.TokenUsage;

/**
 * SSE (Server-Sent Events) 事件解析器。
 * 解析形如 {@code data: {"choices":[{"delta":{"content":"你"}}]}} 的行。
 *
 * 增强支持：
 * - reasoning_content（DeepSeek-R1 / GLM-4.5 等推理模型思考过程）
 * - usage（token 用量，配合 stream_options.include_usage）
 * - finish_reason（结束原因）
 */
public class SSEParser {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    /**
     * 解析一行 SSE 数据。
     *
     * @param line 原始行
     * @return MessageChunk；若为 [DONE] 返回 done 标记；若非数据行返回 ignore 标记。
     */
    public static ParseResult parse(String line) {
        if (line == null) {
            return ParseResult.ignore();
        }
        line = line.trim();
        if (line.isEmpty() || line.startsWith(":")) {
            return ParseResult.ignore();
        }
        if (!line.startsWith(DATA_PREFIX)) {
            return ParseResult.ignore();
        }
        String data = line.substring(DATA_PREFIX.length()).trim();
        if (DONE.equals(data)) {
            return ParseResult.done();
        }
        try {
            JsonObject obj = JsonParser.parseString(data).getAsJsonObject();

            // 检测错误响应
            if (obj.has("error") && !obj.get("error").isJsonNull()) {
                String errMsg = obj.get("error").isJsonObject()
                        ? obj.get("error").toString()
                        : obj.get("error").getAsString();
                return ParseResult.error(errMsg);
            }

            String id = getString(obj, "id");
            String model = getString(obj, "model");

            String deltaContent = null;
            String deltaReasoning = null;
            String finishReason = null;
            java.util.List<ChatMessage.ToolCall> toolCalls = null;

            JsonElement choicesEl = obj.get("choices");
            if (choicesEl != null && choicesEl.isJsonArray() && choicesEl.getAsJsonArray().size() > 0) {
                JsonObject firstChoice = choicesEl.getAsJsonArray().get(0).getAsJsonObject();

                // finish_reason
                JsonElement frEl = firstChoice.get("finish_reason");
                if (frEl != null && !frEl.isJsonNull()) {
                    finishReason = frEl.getAsString();
                }

                // delta 或 message
                JsonElement deltaEl = firstChoice.get("delta");
                if (deltaEl == null || !deltaEl.isJsonObject()) {
                    deltaEl = firstChoice.get("message");
                }
                if (deltaEl != null && deltaEl.isJsonObject()) {
                    JsonObject delta = deltaEl.getAsJsonObject();

                    // content
                    JsonElement contentEl = delta.get("content");
                    if (contentEl != null && !contentEl.isJsonNull() && contentEl.isJsonPrimitive()) {
                        String c = contentEl.getAsString();
                        if (!c.isEmpty()) {
                            deltaContent = c;
                        }
                    }

                    // reasoning_content（DeepSeek-R1 / Qwen / 智谱等）
                    JsonElement reasoningEl = delta.get("reasoning_content");
                    if (reasoningEl == null || reasoningEl.isJsonNull()) {
                        // 备选字段名
                        reasoningEl = delta.get("reasoning");
                    }
                    if (reasoningEl != null && !reasoningEl.isJsonNull() && reasoningEl.isJsonPrimitive()) {
                        String r = reasoningEl.getAsString();
                        if (!r.isEmpty()) {
                            deltaReasoning = r;
                        }
                    }

                    // tool_calls（模型请求调用工具）
                    JsonElement tcEl = delta.get("tool_calls");
                    toolCalls = parseToolCalls(tcEl);
                }
            }

            // usage（通常在最后一个 chunk，需配合 stream_options.include_usage）
            TokenUsage usage = parseUsage(obj.get("usage"));

            MessageChunk chunk = new MessageChunk(id, model, deltaContent, deltaReasoning, usage, finishReason);
            if (toolCalls != null && !toolCalls.isEmpty()) {
                chunk.setToolCalls(toolCalls);
            }
            return ParseResult.chunk(chunk);
        } catch (Exception e) {
            return ParseResult.ignore();
        }
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el != null && !el.isJsonNull() && el.isJsonPrimitive()) {
            return el.getAsString();
        }
        return "";
    }

    private static TokenUsage parseUsage(JsonElement el) {
        if (el == null || !el.isJsonObject()) return null;
        try {
            JsonObject obj = el.getAsJsonObject();
            int prompt = getInt(obj, "prompt_tokens");
            int completion = getInt(obj, "completion_tokens");
            int total = getInt(obj, "total_tokens");
            int cached = 0;
            JsonElement details = obj.get("prompt_tokens_details");
            if (details != null && details.isJsonObject()) {
                cached = getInt(details.getAsJsonObject(), "cached_tokens");
            }
            return new TokenUsage(prompt, completion, total, cached);
        } catch (Exception e) {
            return null;
        }
    }

    private static int getInt(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el != null && !el.isJsonNull() && el.isJsonPrimitive()) {
            return el.getAsInt();
        }
        return 0;
    }

    /**
     * 解析流式 tool_calls 增量。
     * 流式响应中 tool_calls 是增量拼接的：
     * - 第一个 chunk 包含 id 和 function.name
     * - 后续 chunk 只包含 function.arguments 的增量片段
     */
    private static java.util.List<ChatMessage.ToolCall> parseToolCalls(JsonElement el) {
        if (el == null || !el.isJsonArray()) return null;
        java.util.List<ChatMessage.ToolCall> result = new java.util.ArrayList<>();
        try {
            for (JsonElement item : el.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                JsonObject tcObj = item.getAsJsonObject();
                String id = tcObj.has("id") && !tcObj.get("id").isJsonNull()
                        ? tcObj.get("id").getAsString() : null;
                String type = tcObj.has("type") && !tcObj.get("type").isJsonNull()
                        ? tcObj.get("type").getAsString() : "function";

                JsonObject fnObj = tcObj.has("function") ? tcObj.getAsJsonObject("function") : null;
                String fnName = fnObj != null && fnObj.has("name") && !fnObj.get("name").isJsonNull()
                        ? fnObj.get("name").getAsString() : null;
                String fnArgs = fnObj != null && fnObj.has("arguments") && !fnObj.get("arguments").isJsonNull()
                        ? fnObj.get("arguments").getAsString() : "";

                if (id != null || fnName != null) {
                    result.add(new ChatMessage.ToolCall(
                            id == null ? "" : id,
                            type,
                            new ChatMessage.FunctionCall(
                                    fnName == null ? "" : fnName,
                                    fnArgs == null ? "" : fnArgs)));
                }
            }
        } catch (Exception e) {
            return null;
        }
        return result.isEmpty() ? null : result;
    }

    /** 解析结果 */
    public static class ParseResult {
        public final MessageChunk chunk;
        public final boolean done;
        public final String error;

        private ParseResult(MessageChunk chunk, boolean done, String error) {
            this.chunk = chunk;
            this.done = done;
            this.error = error;
        }

        static ParseResult chunk(MessageChunk chunk) {
            return new ParseResult(chunk, false, null);
        }

        static ParseResult done() {
            return new ParseResult(null, true, null);
        }

        static ParseResult ignore() {
            return new ParseResult(null, false, null);
        }

        static ParseResult error(String msg) {
            return new ParseResult(null, false, msg);
        }

        public boolean shouldIgnore() {
            return !done && error == null && (chunk == null
                    || (!chunk.hasContent() && !chunk.hasReasoning()
                    && chunk.getUsage() == null && chunk.getFinishReason() == null));
        }
    }
}
