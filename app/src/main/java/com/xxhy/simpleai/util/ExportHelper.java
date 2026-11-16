package com.xxhy.simpleai.util;

import android.content.Context;
import android.os.Environment;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.xxhy.simpleai.data.db.MessageDao;
import com.xxhy.simpleai.data.db.SessionDao;
import com.xxhy.simpleai.data.model.Message;
import com.xxhy.simpleai.data.model.Session;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 会话导出工具，支持 JSON / Markdown / TXT 格式。
 */
public class ExportHelper {

    public static final int FORMAT_JSON = 0;
    public static final int FORMAT_MARKDOWN = 1;
    public static final int FORMAT_TXT = 2;

    private final SessionDao sessionDao;
    private final MessageDao messageDao;
    private final Context context;

    public ExportHelper(Context context) {
        this.context = context;
        this.sessionDao = new SessionDao(context);
        this.messageDao = new MessageDao(context);
    }

    /** 导出多个会话到文件，返回文件路径 */
    public String export(List<Long> sessionIds, int format) throws IOException {
        String ext;
        switch (format) {
            case FORMAT_JSON:
                ext = "json";
                break;
            case FORMAT_MARKDOWN:
                ext = "md";
                break;
            default:
                ext = "txt";
                break;
        }

        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) {
            dir = context.getFilesDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = "simpleai_export_" + System.currentTimeMillis() + "." + ext;
        File file = new File(dir, fileName);

        StringBuilder sb = new StringBuilder();
        if (format == FORMAT_JSON) {
            sb.append(buildJson(sessionIds));
        } else if (format == FORMAT_MARKDOWN) {
            sb.append(buildMarkdown(sessionIds));
        } else {
            sb.append(buildText(sessionIds));
        }

        // 使用 try-with-resources 确保 writer 关闭，避免异常时资源泄漏
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(sb.toString());
        }

        return file.getAbsolutePath();
    }

    private String buildJson(List<Long> sessionIds) {
        JsonArray array = new JsonArray();
        for (long id : sessionIds) {
            Session session = sessionDao.queryById(id);
            if (session == null) continue;
            List<Message> messages = messageDao.queryBySession(id);

            JsonObject obj = new JsonObject();
            obj.addProperty("title", session.getTitle());
            obj.addProperty("model", session.getModel());
            obj.addProperty("createdAt", session.getCreatedAt());

            JsonArray msgArray = new JsonArray();
            for (Message m : messages) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("role", m.getRole());
                msgObj.addProperty("content", m.getContent());
                msgObj.addProperty("createdAt", m.getCreatedAt());
                msgArray.add(msgObj);
            }
            obj.add("messages", msgArray);
            array.add(obj);
        }
        return new Gson().toJson(array);
    }

    private String buildMarkdown(List<Long> sessionIds) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (long id : sessionIds) {
            Session session = sessionDao.queryById(id);
            if (session == null) continue;
            List<Message> messages = messageDao.queryBySession(id);

            sb.append("# ").append(session.getTitle()).append("\n\n");
            sb.append("**Model:** ").append(session.getModel() != null ? session.getModel() : "")
              .append("  \n");
            sb.append("**Date:** ").append(sdf.format(new Date(session.getCreatedAt())))
              .append("\n\n---\n\n");

            for (Message m : messages) {
                String role = m.isUser() ? "🧑 User" : "🤖 Assistant";
                if (Message.ROLE_SYSTEM.equals(m.getRole())) {
                    role = "⚙️ System";
                }
                sb.append("**").append(role).append(":**\n\n")
                  .append(m.getContent()).append("\n\n---\n\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildText(List<Long> sessionIds) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        for (long id : sessionIds) {
            Session session = sessionDao.queryById(id);
            if (session == null) continue;
            List<Message> messages = messageDao.queryBySession(id);

            sb.append("========================================\n");
            sb.append("Session: ").append(session.getTitle()).append("\n");
            sb.append("Model: ").append(session.getModel() != null ? session.getModel() : "")
              .append("\n");
            sb.append("Date: ").append(sdf.format(new Date(session.getCreatedAt()))).append("\n");
            sb.append("========================================\n\n");

            for (Message m : messages) {
                String role = m.isUser() ? "User" : "Assistant";
                if (Message.ROLE_SYSTEM.equals(m.getRole())) {
                    role = "System";
                }
                sb.append("[").append(role).append("]\n")
                  .append(m.getContent()).append("\n\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
