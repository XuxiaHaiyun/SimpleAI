package com.xxhy.simpleai.util;

import com.xxhy.simpleai.data.model.Message;

import java.util.List;

/**
 * 上下文管理工具。参考 rikkahub 的 limitContext 设计。
 *
 * 在简单消息列表场景下，按数量截取最近 N 条消息，
 * 并保证起始位置是 user 消息（避免从 assistant 回复中间开始）。
 *
 * 兼容 Android 4.x，仅使用 JDK API。
 */
public final class ContextUtils {

    private ContextUtils() {
    }

    /**
     * 按数量截取最近 N 条消息作为上下文。
     * - 若 size <= 0 或列表长度 <= size，原样返回
     * - 否则从尾部截取 size 条，并向前调整起始位置到第一条 user 消息
     *
     * @param messages 全部消息（按时间正序）
     * @param size     上下文长度（消息条数）
     * @return 截取后的上下文消息列表
     */
    public static List<Message> limitContext(List<Message> messages, int size) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }
        if (size <= 0 || messages.size() <= size) {
            return messages;
        }

        int startIndex = messages.size() - size;
        // 向前调整：起始位置应为 user 消息，避免从 assistant 中间截断
        while (startIndex > 0 && !messages.get(startIndex).isUser()) {
            startIndex--;
        }
        return messages.subList(startIndex, messages.size());
    }

    /**
     * 估算消息列表的近似 token 数（粗略 4 字符 ≈ 1 token）。
     * 用于 UI 提示，非精确值。
     *
     * @param messages 消息列表
     * @return 近似 token 数
     */
    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int chars = 0;
        for (Message m : messages) {
            if (m.getContent() != null) {
                chars += m.getContent().length();
            }
            if (m.getReasoning() != null) {
                chars += m.getReasoning().length();
            }
        }
        return chars / 4;
    }

    /**
     * 按近似 token 上限截取上下文。
     *
     * @param messages   全部消息（按时间正序）
     * @param maxTokens  token 上限
     * @return 截取后的上下文消息列表
     */
    public static List<Message> limitByTokens(List<Message> messages, int maxTokens) {
        if (messages == null || messages.isEmpty() || maxTokens <= 0) {
            return messages;
        }
        int total = 0;
        int start = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            int len = (m.getContent() != null ? m.getContent().length() : 0)
                    + (m.getReasoning() != null ? m.getReasoning().length() : 0);
            int tokens = len / 4;
            if (total + tokens > maxTokens) {
                break;
            }
            total += tokens;
            start = i;
        }
        // 起始位置应为 user 消息
        while (start < messages.size() && !messages.get(start).isUser()) {
            start++;
        }
        if (start >= messages.size()) {
            start = messages.size() - 1;
        }
        return messages.subList(start, messages.size());
    }
}
