package com.xxhy.simpleai.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.xxhy.simpleai.App;
import com.xxhy.simpleai.data.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息表数据访问对象。
 */
public class MessageDao {

    private final DbHelper helper;

    public MessageDao(Context context) {
        this.helper = App.get() != null ? App.get().getDbHelper() : new DbHelper(context);
    }

    public long insert(Message message) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Messages.SESSION_ID, message.getSessionId());
        cv.put(Contract.Messages.ROLE, message.getRole());
        cv.put(Contract.Messages.CONTENT, message.getContent());
        long now = System.currentTimeMillis();
        cv.put(Contract.Messages.CREATED_AT, now);
        cv.put(Contract.Messages.REASONING, message.getReasoning());
        cv.put(Contract.Messages.MODEL, message.getModel());
        cv.put(Contract.Messages.USAGE_JSON, message.getUsageJson());
        cv.put(Contract.Messages.FINISH_REASON, message.getFinishReason());
        cv.put(Contract.Messages.ATTACHMENTS, message.getAttachments());
        long id = db.insert(Contract.Messages.TABLE, null, cv);
        message.setId(id);
        message.setCreatedAt(now);
        return id;
    }

    /** 按时间正序返回某会话的全部消息 */
    public List<Message> queryBySession(long sessionId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Messages.TABLE, null,
                Contract.Messages.SESSION_ID + "=?",
                new String[]{String.valueOf(sessionId)},
                null, null, Contract.Messages.CREATED_AT + " ASC");
        List<Message> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToMessage(c));
        }
        c.close();
        return list;
    }

    /** 返回最近 N 条消息（用于上下文截取） */
    public List<Message> queryRecent(long sessionId, int limit) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Messages.TABLE, null,
                Contract.Messages.SESSION_ID + "=?",
                new String[]{String.valueOf(sessionId)},
                null, null, Contract.Messages.CREATED_AT + " DESC",
                String.valueOf(limit));
        List<Message> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToMessage(c));
        }
        c.close();
        // 反转为正序
        java.util.Collections.reverse(list);
        return list;
    }

    public void updateContent(long id, String content) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Messages.CONTENT, content);
        db.update(Contract.Messages.TABLE, cv,
                Contract.Messages.ID + "=?", new String[]{String.valueOf(id)});
    }

    /** 更新消息完整内容（含 reasoning/usage/finishReason） */
    public void update(Message message) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Messages.CONTENT, message.getContent());
        cv.put(Contract.Messages.REASONING, message.getReasoning());
        cv.put(Contract.Messages.MODEL, message.getModel());
        cv.put(Contract.Messages.USAGE_JSON, message.getUsageJson());
        cv.put(Contract.Messages.FINISH_REASON, message.getFinishReason());
        db.update(Contract.Messages.TABLE, cv,
                Contract.Messages.ID + "=?", new String[]{String.valueOf(message.getId())});
    }

    public void delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(Contract.Messages.TABLE,
                Contract.Messages.ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public void deleteBySession(long sessionId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(Contract.Messages.TABLE,
                Contract.Messages.SESSION_ID + "=?",
                new String[]{String.valueOf(sessionId)});
    }

    /** 获取会话首条用户消息（用于自动生成标题） */
    public Message queryFirstUserMessage(long sessionId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Messages.TABLE, null,
                Contract.Messages.SESSION_ID + "=? AND " + Contract.Messages.ROLE + "=?",
                new String[]{String.valueOf(sessionId), Message.ROLE_USER},
                null, null, Contract.Messages.CREATED_AT + " ASC", "1");
        Message m = null;
        if (c.moveToFirst()) {
            m = cursorToMessage(c);
        }
        c.close();
        return m;
    }

    private Message cursorToMessage(Cursor c) {
        Message m = new Message();
        m.setId(c.getLong(c.getColumnIndexOrThrow(Contract.Messages.ID)));
        m.setSessionId(c.getLong(c.getColumnIndexOrThrow(Contract.Messages.SESSION_ID)));
        m.setRole(c.getString(c.getColumnIndexOrThrow(Contract.Messages.ROLE)));
        m.setContent(c.getString(c.getColumnIndexOrThrow(Contract.Messages.CONTENT)));
        m.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(Contract.Messages.CREATED_AT)));
        int idx;
        idx = c.getColumnIndex(Contract.Messages.REASONING);
        if (idx >= 0) m.setReasoning(c.getString(idx));
        idx = c.getColumnIndex(Contract.Messages.MODEL);
        if (idx >= 0) m.setModel(c.getString(idx));
        idx = c.getColumnIndex(Contract.Messages.USAGE_JSON);
        if (idx >= 0) m.setUsageJson(c.getString(idx));
        idx = c.getColumnIndex(Contract.Messages.FINISH_REASON);
        if (idx >= 0) m.setFinishReason(c.getString(idx));
        idx = c.getColumnIndex(Contract.Messages.ATTACHMENTS);
        if (idx >= 0) m.setAttachments(c.getString(idx));
        return m;
    }
}
