package com.xxhy.simpleai.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.xxhy.simpleai.App;
import com.xxhy.simpleai.data.model.ApiConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * API 配置表数据访问对象。
 */
public class ApiConfigDao {

    private final DbHelper helper;

    public ApiConfigDao(Context context) {
        this.helper = App.get() != null ? App.get().getDbHelper() : new DbHelper(context);
    }

    public long insert(ApiConfig config) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = toContentValues(config);
        cv.put(Contract.ApiConfigs.CREATED_AT, System.currentTimeMillis());
        long id = db.insert(Contract.ApiConfigs.TABLE, null, cv);
        config.setId(id);
        return id;
    }

    public int update(ApiConfig config) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = toContentValues(config);
        return db.update(Contract.ApiConfigs.TABLE, cv,
                Contract.ApiConfigs.ID + "=?", new String[]{String.valueOf(config.getId())});
    }

    public void delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(Contract.ApiConfigs.TABLE,
                Contract.ApiConfigs.ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public ApiConfig queryById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.ApiConfigs.TABLE, null,
                Contract.ApiConfigs.ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        ApiConfig cfg = null;
        if (c.moveToFirst()) {
            cfg = cursorToConfig(c);
        }
        c.close();
        return cfg;
    }

    public List<ApiConfig> queryAll() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.ApiConfigs.TABLE, null, null, null,
                null, null, Contract.ApiConfigs.CREATED_AT + " ASC");
        List<ApiConfig> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToConfig(c));
        }
        c.close();
        return list;
    }

    /** 仅查询已启用的配置 */
    public List<ApiConfig> queryEnabled() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.ApiConfigs.TABLE, null,
                Contract.ApiConfigs.ENABLED + "=1", null,
                null, null, Contract.ApiConfigs.CREATED_AT + " ASC");
        List<ApiConfig> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToConfig(c));
        }
        c.close();
        return list;
    }

    public ApiConfig queryActive() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.ApiConfigs.TABLE, null,
                Contract.ApiConfigs.IS_ACTIVE + "=1", null,
                null, null, null, "1");
        ApiConfig cfg = null;
        if (c.moveToFirst()) {
            cfg = cursorToConfig(c);
        }
        c.close();
        return cfg;
    }

    /** 将指定配置设为激活，其余取消激活 */
    public void setActive(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues clear = new ContentValues();
            clear.put(Contract.ApiConfigs.IS_ACTIVE, 0);
            db.update(Contract.ApiConfigs.TABLE, clear, null, null);

            ContentValues set = new ContentValues();
            set.put(Contract.ApiConfigs.IS_ACTIVE, 1);
            db.update(Contract.ApiConfigs.TABLE, set,
                    Contract.ApiConfigs.ID + "=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues toContentValues(ApiConfig config) {
        ContentValues cv = new ContentValues();
        cv.put(Contract.ApiConfigs.NAME, config.getName());
        cv.put(Contract.ApiConfigs.BASE_URL, config.getBaseUrl());
        cv.put(Contract.ApiConfigs.API_PATH, config.getApiPath());
        cv.put(Contract.ApiConfigs.API_KEY, config.getApiKeyEncrypted());
        cv.put(Contract.ApiConfigs.MODELS, config.getModels());
        cv.put(Contract.ApiConfigs.TEMPERATURE, config.getTemperature());
        cv.put(Contract.ApiConfigs.MAX_TOKENS, config.getMaxTokens());
        cv.put(Contract.ApiConfigs.CONTEXT_LENGTH, config.getContextLength());
        cv.put(Contract.ApiConfigs.IS_ACTIVE, config.isActive() ? 1 : 0);
        cv.put(Contract.ApiConfigs.PROVIDER_TYPE, config.getProviderType());
        cv.put(Contract.ApiConfigs.ENABLED, config.isEnabled() ? 1 : 0);
        cv.put(Contract.ApiConfigs.VISION_SUPPORTED, config.isVisionSupported() ? 1 : 0);
        cv.put(Contract.ApiConfigs.REASONING_SUPPORTED, config.isReasoningSupported() ? 1 : 0);
        cv.put(Contract.ApiConfigs.SEARCH_ENABLED, config.isSearchEnabled() ? 1 : 0);
        cv.put(Contract.ApiConfigs.USE_RESPONSE_API, config.isUseResponseApi() ? 1 : 0);
        cv.put(Contract.ApiConfigs.BALANCE_ENABLED, config.isBalanceEnabled() ? 1 : 0);
        cv.put(Contract.ApiConfigs.BALANCE_API_PATH, config.getBalanceApiPath());
        cv.put(Contract.ApiConfigs.BALANCE_RESULT_PATH, config.getBalanceResultPath());
        return cv;
    }

    private ApiConfig cursorToConfig(Cursor c) {
        ApiConfig cfg = new ApiConfig();
        cfg.setId(c.getLong(c.getColumnIndexOrThrow(Contract.ApiConfigs.ID)));
        cfg.setName(c.getString(c.getColumnIndexOrThrow(Contract.ApiConfigs.NAME)));
        cfg.setBaseUrl(c.getString(c.getColumnIndexOrThrow(Contract.ApiConfigs.BASE_URL)));
        cfg.setApiPath(c.getString(c.getColumnIndexOrThrow(Contract.ApiConfigs.API_PATH)));
        cfg.setApiKeyEncrypted(c.getString(c.getColumnIndexOrThrow(Contract.ApiConfigs.API_KEY)));
        cfg.setModels(c.getString(c.getColumnIndexOrThrow(Contract.ApiConfigs.MODELS)));
        cfg.setTemperature(c.getFloat(c.getColumnIndexOrThrow(Contract.ApiConfigs.TEMPERATURE)));
        cfg.setMaxTokens(c.getInt(c.getColumnIndexOrThrow(Contract.ApiConfigs.MAX_TOKENS)));
        cfg.setContextLength(c.getInt(c.getColumnIndexOrThrow(Contract.ApiConfigs.CONTEXT_LENGTH)));
        cfg.setActive(c.getInt(c.getColumnIndexOrThrow(Contract.ApiConfigs.IS_ACTIVE)) == 1);
        cfg.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(Contract.ApiConfigs.CREATED_AT)));
        cfg.setProviderType(getStringOptional(c, Contract.ApiConfigs.PROVIDER_TYPE, ApiConfig.TYPE_OPENAI));
        cfg.setEnabled(getIntOptional(c, Contract.ApiConfigs.ENABLED, 1) == 1);
        cfg.setVisionSupported(getIntOptional(c, Contract.ApiConfigs.VISION_SUPPORTED, 0) == 1);
        cfg.setReasoningSupported(getIntOptional(c, Contract.ApiConfigs.REASONING_SUPPORTED, 0) == 1);
        cfg.setSearchEnabled(getIntOptional(c, Contract.ApiConfigs.SEARCH_ENABLED, 0) == 1);
        cfg.setUseResponseApi(getIntOptional(c, Contract.ApiConfigs.USE_RESPONSE_API, 0) == 1);
        cfg.setBalanceEnabled(getIntOptional(c, Contract.ApiConfigs.BALANCE_ENABLED, 0) == 1);
        cfg.setBalanceApiPath(getStringOptional(c, Contract.ApiConfigs.BALANCE_API_PATH, "/credits"));
        cfg.setBalanceResultPath(getStringOptional(c, Contract.ApiConfigs.BALANCE_RESULT_PATH, "data.total_usage"));
        return cfg;
    }

    private static String getStringOptional(Cursor c, String col, String def) {
        int idx = c.getColumnIndex(col);
        if (idx < 0 || c.isNull(idx)) return def;
        return c.getString(idx);
    }

    private static int getIntOptional(Cursor c, String col, int def) {
        int idx = c.getColumnIndex(col);
        if (idx < 0 || c.isNull(idx)) return def;
        return c.getInt(idx);
    }
}
