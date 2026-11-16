package com.xxhy.simpleai.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * HTTP 错误响应解析器。参考 rikkahub ErrorParser 设计。
 * 递归解析 JSON 错误结构，提取常见错误字段（error/detail/message/description）。
 */
public final class ErrorParser {

    private ErrorParser() {
    }

    /**
     * 从错误响应体中解析出可读的错误信息。
     *
     * @param body 响应体字符串
     * @return 解析后的错误消息；解析失败返回原始 body
     */
    public static String parse(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "Unknown error";
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            return parseElement(element);
        } catch (Exception e) {
            return truncate(body, 300);
        }
    }

    private static String parseElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "Unknown error";
        }
        if (element.isJsonObject()) {
            return parseObject(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() == 0) {
                return "Unknown error: Empty array";
            }
            return parseElement(arr.get(0));
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive p = element.getAsJsonPrimitive();
            if (p.isString()) {
                return p.getAsString();
            }
            return p.toString();
        }
        return element.toString();
    }

    private static String parseObject(JsonObject obj) {
        // 按优先级查找常见错误字段
        String[] fields = {"error", "detail", "message", "description", "msg"};
        for (String field : fields) {
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                return parseElement(obj.get(field));
            }
        }
        // 没有匹配字段，返回整个对象
        return obj.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
