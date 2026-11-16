package com.xxhy.simpleai.network;

import com.xxhy.simpleai.network.model.TokenUsage;

/**
 * 流式对话回调接口。所有方法均在主线程调用。
 *
 * 参考 rikkahub 的 MessageChunk 流式设计，新增 reasoning/usage/finish 回调，
 * 支持 DeepSeek-R1 / GLM-4.5 / Moonshot 等推理模型的思考过程与 token 统计。
 */
public interface ChatStreamListener {

    /** 开始连接 */
    void onStart();

    /** 收到一个增量内容片段 */
    void onDelta(String delta);

    /**
     * 收到一个思考过程片段（推理模型专用）。
     * DeepSeek-R1 / GLM-4.5 / Qwen3 等模型会通过 reasoning_content 字段返回。
     *
     * @param delta 思考过程增量
     */
    void onReasoning(String delta);

    /**
     * 流式结束，fullContent 为完整回复。
     *
     * @param fullContent 完整回复内容
     * @param usage       token 用量统计（可能为 null）
     * @param finishReason 结束原因（stop/length/tool_calls 等，可能为 null）
     */
    void onComplete(String fullContent, TokenUsage usage, String finishReason);

    /** 发生错误 */
    void onError(String message);
}
