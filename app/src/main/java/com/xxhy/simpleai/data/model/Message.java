package com.xxhy.simpleai.data.model;

/**
 * 聊天消息实体。
 */
public class Message {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    private long id;
    private long sessionId;
    private String role;
    private String content;
    private long createdAt;
    /** 标记是否正在生成中（仅 assistant 消息） */
    private boolean streaming;
    /** 思考过程内容（DeepSeek-R1/GLM-4.5 等推理模型） */
    private String reasoning;
    /** 模型名称（assistant 消息标记由哪个模型生成） */
    private String model;
    /** Token 使用统计（JSON 字符串持久化） */
    private String usageJson;
    /** 结束原因（stop/length/tool_calls 等） */
    private String finishReason;
    /** 附件 JSON（图片/文件 URI 列表，逗号分隔） */
    private String attachments;

    public Message() {
    }

    public Message(long sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equals(role);
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUsageJson() {
        return usageJson;
    }

    public void setUsageJson(String usageJson) {
        this.usageJson = usageJson;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    /** 是否包含思考过程 */
    public boolean hasReasoning() {
        return reasoning != null && !reasoning.isEmpty();
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    /** 是否包含附件 */
    public boolean hasAttachments() {
        return attachments != null && !attachments.trim().isEmpty();
    }

    /** 返回附件 URI 数组 */
    public String[] getAttachmentArray() {
        if (attachments == null || attachments.trim().isEmpty()) {
            return new String[0];
        }
        String[] parts = attachments.split("\\|");
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) list.add(t);
        }
        return list.toArray(new String[0]);
    }
}
