package com.xxhy.simpleai.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.xxhy.simpleai.App;
import com.xxhy.simpleai.data.model.Session;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话表数据访问对象。
 */
public class SessionDao {

    private final DbHelper helper;

    public SessionDao(Context context) {
        this.helper = App.get() != null ? App.get().getDbHelper() : new DbHelper(context);
    }

    public long insert(Session session) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Sessions.TITLE, session.getTitle());
        cv.put(Contract.Sessions.API_CONFIG_ID, session.getApiConfigId());
        cv.put(Contract.Sessions.MODEL, session.getModel());
        long now = System.currentTimeMillis();
        cv.put(Contract.Sessions.CREATED_AT, now);
        cv.put(Contract.Sessions.UPDATED_AT, now);
        long id = db.insert(Contract.Sessions.TABLE, null, cv);
        session.setId(id);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return id;
    }

    public Session queryById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Sessions.TABLE, null,
                Contract.Sessions.ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        Session s = null;
        if (c.moveToFirst()) {
            s = cursorToSession(c);
        }
        c.close();
        return s;
    }

    /** 按更新时间倒序返回全部会话 */
    public List<Session> queryAll() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Sessions.TABLE, null, null, null,
                null, null, Contract.Sessions.UPDATED_AT + " DESC");
        return cursorToList(c);
    }

    /** 关键词搜索标题 */
    public List<Session> search(String keyword) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Sessions.TABLE, null,
                Contract.Sessions.TITLE + " LIKE ?",
                new String[]{"%" + keyword + "%"},
                null, null, Contract.Sessions.UPDATED_AT + " DESC");
        return cursorToList(c);
    }

    public int updateTitle(long id, String title) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Sessions.TITLE, title);
        cv.put(Contract.Sessions.UPDATED_AT, System.currentTimeMillis());
        return db.update(Contract.Sessions.TABLE, cv,
                Contract.Sessions.ID + "=?", new String[]{String.valueOf(id)});
    }

    public int updateModel(long id, long apiConfigId, String model) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Sessions.API_CONFIG_ID, apiConfigId);
        cv.put(Contract.Sessions.MODEL, model);
        cv.put(Contract.Sessions.UPDATED_AT, System.currentTimeMillis());
        return db.update(Contract.Sessions.TABLE, cv,
                Contract.Sessions.ID + "=?", new String[]{String.valueOf(id)});
    }

    public void touch(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(Contract.Sessions.UPDATED_AT, System.currentTimeMillis());
        db.update(Contract.Sessions.TABLE, cv,
                Contract.Sessions.ID + "=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        // 先删消息（外键级联已开启，但显式删除更稳妥）
        db.delete(Contract.Messages.TABLE,
                Contract.Messages.SESSION_ID + "=?",
                new String[]{String.valueOf(id)});
        db.delete(Contract.Sessions.TABLE,
                Contract.Sessions.ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public void deleteAll(List<Long> ids) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (long id : ids) {
                db.delete(Contract.Messages.TABLE,
                        Contract.Messages.SESSION_ID + "=?",
                        new String[]{String.valueOf(id)});
                db.delete(Contract.Sessions.TABLE,
                        Contract.Sessions.ID + "=?",
                        new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private List<Session> cursorToList(Cursor c) {
        List<Session> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToSession(c));
        }
        c.close();
        return list;
    }

    private Session cursorToSession(Cursor c) {
        Session s = new Session();
        s.setId(c.getLong(c.getColumnIndexOrThrow(Contract.Sessions.ID)));
        s.setTitle(c.getString(c.getColumnIndexOrThrow(Contract.Sessions.TITLE)));
        s.setApiConfigId(c.getLong(c.getColumnIndexOrThrow(Contract.Sessions.API_CONFIG_ID)));
        s.setModel(c.getString(c.getColumnIndexOrThrow(Contract.Sessions.MODEL)));
        s.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(Contract.Sessions.CREATED_AT)));
        s.setUpdatedAt(c.getLong(c.getColumnIndexOrThrow(Contract.Sessions.UPDATED_AT)));
        return s;
    }
}
