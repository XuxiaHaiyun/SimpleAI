package com.xxhy.simpleai.network.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI 兼容格式的聊天消息（请求体用）。
 * 支持纯文本与多模态（文本 + 图片 URL）两种形式。
 * 通过自定义 JsonSerializer 在序列化时根据是否多模态决定 content 字段为 String 或 Array。
 */
public class ChatMessage {

    private String role;

    /** 纯文本内容；多模态时为 null */
    private String content;

    /** 多模态内容数组；为 null 时使用 content 字段 */
    private transient List<ContentPart> contentParts;

    /** 工具调用列表（assistant 消息中模型返回的 tool_calls） */
    private transient List<ToolCall> toolCalls;

    /** 工具调用 ID（role=tool 时使用） */
    private transient String toolCallId;

    /** 工具名称（role=tool 时使用，用于标识是哪个工具的返回） */
    private transient String name;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    /** 构造多模态消息（文本 + 图片） */
    public ChatMessage(String role, String text, List<String> imageUrls) {
        this.role = role;
        this.content = null;
        this.contentParts = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            contentParts.add(new ContentPart(ContentPart.TYPE_TEXT, text));
        }
        if (imageUrls != null) {
            for (String url : imageUrls) {
                contentParts.add(ContentPart.image(url));
            }
        }
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
        this.contentParts = null;
    }

    public List<ContentPart> getContentParts() {
        return contentParts;
    }

    public boolean isMultimodal() {
        return contentParts != null && !contentParts.isEmpty();
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** 工具调用（模型返回的 function call） */
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;

        public ToolCall(String id, String type, FunctionCall function) {
            this.id = id;
            this.type = type;
            this.function = function;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public FunctionCall getFunction() {
            return function;
        }
    }

    /** 函数调用详情 */
    public static class FunctionCall {
        private String name;
        private String arguments;

        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public String getArguments() {
            return arguments;
        }
    }

    /** 内容片段（文本或图片 URL） */
    public static class ContentPart {
        public static final String TYPE_TEXT = "text";
        public static final String TYPE_IMAGE_URL = "image_url";

        private String type;
        private String text;
        private ImageUrl image_url;

        public ContentPart(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public static ContentPart image(String url) {
            ContentPart p = new ContentPart(TYPE_IMAGE_URL, null);
            p.image_url = new ImageUrl(url);
            return p;
        }
    }

    /** 图片 URL 包装 */
    public static class ImageUrl {
        private String url;

        public ImageUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 自定义序列化器：多模态时 content 输出为数组，否则输出为字符串。
     */
    public static class Serializer implements JsonSerializer<ChatMessage> {
        @Override
        public JsonElement serialize(ChatMessage src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", src.role);

            // tool 角色：必须包含 tool_call_id
            if ("tool".equals(src.role)) {
                if (src.toolCallId != null) {
                    obj.addProperty("tool_call_id", src.toolCallId);
                }
                if (src.name != null) {
                    obj.addProperty("name", src.name);
                }
                obj.addProperty("content", src.content == null ? "" : src.content);
                return obj;
            }

            // assistant 角色：可能包含 tool_calls
            if ("assistant".equals(src.role) && src.toolCalls != null && !src.toolCalls.isEmpty()) {
                JsonArray tcArr = new JsonArray();
                for (ToolCall tc : src.toolCalls) {
                    JsonObject tcObj = new JsonObject();
                    tcObj.addProperty("id", tc.getId());
                    tcObj.addProperty("type", tc.getType() == null ? "function" : tc.getType());
                    JsonObject fnObj = new JsonObject();
                    fnObj.addProperty("name", tc.getFunction().getName());
                    fnObj.addProperty("arguments", tc.getFunction().getArguments());
                    tcObj.add("function", fnObj);
                    tcArr.add(tcObj);
                }
                obj.add("tool_calls", tcArr);
                // content 可以为 null
                if (src.content != null && !src.content.isEmpty()) {
                    obj.addProperty("content", src.content);
                } else {
                    obj.addProperty("content", "");
                }
                return obj;
            }

            // 普通消息
            if (src.isMultimodal()) {
                JsonArray arr = new JsonArray();
                for (ContentPart part : src.contentParts) {
                    JsonObject p = new JsonObject();
                    p.addProperty("type", part.type);
                    if (ContentPart.TYPE_TEXT.equals(part.type)) {
                        p.addProperty("text", part.text);
                    } else if (ContentPart.TYPE_IMAGE_URL.equals(part.type) && part.image_url != null) {
                        JsonObject img = new JsonObject();
                        img.addProperty("url", part.image_url.url);
                        p.add("image_url", img);
                    }
                    arr.add(p);
                }
                obj.add("content", arr);
            } else {
                obj.addProperty("content", src.content == null ? "" : src.content);
            }
            return obj;
        }
    }
}
