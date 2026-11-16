package com.xxhy.simpleai.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite 数据库管理器，负责建表与升级。
 */
public class DbHelper extends SQLiteOpenHelper {

    public DbHelper(Context context) {
        super(context, Contract.DB_NAME, null, Contract.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Contract.Sessions.TABLE + " ("
                + Contract.Sessions.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Contract.Sessions.TITLE + " TEXT, "
                + Contract.Sessions.API_CONFIG_ID + " INTEGER DEFAULT 0, "
                + Contract.Sessions.MODEL + " TEXT, "
                + Contract.Sessions.CREATED_AT + " INTEGER, "
                + Contract.Sessions.UPDATED_AT + " INTEGER)");

        db.execSQL("CREATE TABLE " + Contract.Messages.TABLE + " ("
                + Contract.Messages.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Contract.Messages.SESSION_ID + " INTEGER, "
                + Contract.Messages.ROLE + " TEXT, "
                + Contract.Messages.CONTENT + " TEXT, "
                + Contract.Messages.CREATED_AT + " INTEGER, "
                + Contract.Messages.REASONING + " TEXT, "
                + Contract.Messages.MODEL + " TEXT, "
                + Contract.Messages.USAGE_JSON + " TEXT, "
                + Contract.Messages.FINISH_REASON + " TEXT, "
                + Contract.Messages.ATTACHMENTS + " TEXT, "
                + "FOREIGN KEY(" + Contract.Messages.SESSION_ID + ") REFERENCES "
                + Contract.Sessions.TABLE + "(" + Contract.Sessions.ID + ") ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX idx_msg_session ON " + Contract.Messages.TABLE
                + "(" + Contract.Messages.SESSION_ID + ")");

        db.execSQL("CREATE TABLE " + Contract.ApiConfigs.TABLE + " ("
                + Contract.ApiConfigs.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Contract.ApiConfigs.NAME + " TEXT, "
                + Contract.ApiConfigs.BASE_URL + " TEXT, "
                + Contract.ApiConfigs.API_PATH + " TEXT, "
                + Contract.ApiConfigs.API_KEY + " TEXT, "
                + Contract.ApiConfigs.MODELS + " TEXT, "
                + Contract.ApiConfigs.TEMPERATURE + " REAL DEFAULT 0.7, "
                + Contract.ApiConfigs.MAX_TOKENS + " INTEGER DEFAULT 2048, "
                + Contract.ApiConfigs.CONTEXT_LENGTH + " INTEGER DEFAULT 20, "
                + Contract.ApiConfigs.IS_ACTIVE + " INTEGER DEFAULT 0, "
                + Contract.ApiConfigs.CREATED_AT + " INTEGER, "
                + Contract.ApiConfigs.PROVIDER_TYPE + " TEXT DEFAULT 'openai', "
                + Contract.ApiConfigs.ENABLED + " INTEGER DEFAULT 1, "
                + Contract.ApiConfigs.VISION_SUPPORTED + " INTEGER DEFAULT 0, "
                + Contract.ApiConfigs.REASONING_SUPPORTED + " INTEGER DEFAULT 0, "
                + Contract.ApiConfigs.INCLUDE_HISTORY_REASONING + " INTEGER DEFAULT 1, "
                + Contract.ApiConfigs.SEARCH_ENABLED + " INTEGER DEFAULT 0, "
                + Contract.ApiConfigs.USE_RESPONSE_API + " INTEGER DEFAULT 0, "
                + Contract.ApiConfigs.BALANCE_ENABLED + " INTEGER DEFAULT 0, "
                + Contract.ApiConfigs.BALANCE_API_PATH + " TEXT DEFAULT '/credits', "
                + Contract.ApiConfigs.BALANCE_RESULT_PATH + " TEXT DEFAULT 'data.total_usage')");

        db.execSQL("CREATE TABLE " + Contract.Models.TABLE + " ("
                + Contract.Models.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Contract.Models.API_CONFIG_ID + " INTEGER, "
                + Contract.Models.MODEL_ID + " TEXT, "
                + Contract.Models.DISPLAY_NAME + " TEXT, "
                + Contract.Models.TYPE + " TEXT DEFAULT 'chat', "
                + Contract.Models.INPUT_MODALITIES + " TEXT DEFAULT 'text', "
                + Contract.Models.OUTPUT_MODALITIES + " TEXT DEFAULT 'text', "
                + Contract.Models.ABILITIES + " TEXT DEFAULT '', "
                + Contract.Models.CUSTOM_HEADERS + " TEXT DEFAULT '[]', "
                + Contract.Models.CUSTOM_BODIES + " TEXT DEFAULT '[]', "
                + Contract.Models.PROVIDER_OVERWRITE + " TEXT, "
                + Contract.Models.CREATED_AT + " INTEGER, "
                + "FOREIGN KEY(" + Contract.Models.API_CONFIG_ID + ") REFERENCES "
                + Contract.ApiConfigs.TABLE + "(" + Contract.ApiConfigs.ID + ") ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX idx_models_config ON " + Contract.Models.TABLE
                + "(" + Contract.Models.API_CONFIG_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 增量升级，保留用户数据
        if (oldVersion < 2) {
            // v2: 新增 reasoning / model / usage_json / finish_reason 列
            addColumnIfNotExists(db, Contract.Messages.TABLE, Contract.Messages.REASONING, "TEXT");
            addColumnIfNotExists(db, Contract.Messages.TABLE, Contract.Messages.MODEL, "TEXT");
            addColumnIfNotExists(db, Contract.Messages.TABLE, Contract.Messages.USAGE_JSON, "TEXT");
            addColumnIfNotExists(db, Contract.Messages.TABLE, Contract.Messages.FINISH_REASON, "TEXT");
        }
        if (oldVersion < 3) {
            // v3: 消息表新增 attachments；API 配置表新增服务商类型与能力标记
            addColumnIfNotExists(db, Contract.Messages.TABLE, Contract.Messages.ATTACHMENTS, "TEXT");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.PROVIDER_TYPE, "TEXT DEFAULT 'openai'");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.ENABLED, "INTEGER DEFAULT 1");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.VISION_SUPPORTED, "INTEGER DEFAULT 0");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.REASONING_SUPPORTED, "INTEGER DEFAULT 0");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.INCLUDE_HISTORY_REASONING, "INTEGER DEFAULT 1");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.SEARCH_ENABLED, "INTEGER DEFAULT 0");
        }
        if (oldVersion < 4) {
            // v4: API 配置表新增 Response API / 余额字段；新增模型配置表
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.USE_RESPONSE_API, "INTEGER DEFAULT 0");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.BALANCE_ENABLED, "INTEGER DEFAULT 0");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.BALANCE_API_PATH, "TEXT DEFAULT '/credits'");
            addColumnIfNotExists(db, Contract.ApiConfigs.TABLE, Contract.ApiConfigs.BALANCE_RESULT_PATH, "TEXT DEFAULT 'data.total_usage'");
            createModelsTableIfNotExists(db);
        }
    }

    /** 创建模型配置表（升级与新建共用） */
    private void createModelsTableIfNotExists(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + Contract.Models.TABLE + " ("
                + Contract.Models.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Contract.Models.API_CONFIG_ID + " INTEGER, "
                + Contract.Models.MODEL_ID + " TEXT, "
                + Contract.Models.DISPLAY_NAME + " TEXT, "
                + Contract.Models.TYPE + " TEXT DEFAULT 'chat', "
                + Contract.Models.INPUT_MODALITIES + " TEXT DEFAULT 'text', "
                + Contract.Models.OUTPUT_MODALITIES + " TEXT DEFAULT 'text', "
                + Contract.Models.ABILITIES + " TEXT DEFAULT '', "
                + Contract.Models.CUSTOM_HEADERS + " TEXT DEFAULT '[]', "
                + Contract.Models.CUSTOM_BODIES + " TEXT DEFAULT '[]', "
                + Contract.Models.PROVIDER_OVERWRITE + " TEXT, "
                + Contract.Models.CREATED_AT + " INTEGER, "
                + "FOREIGN KEY(" + Contract.Models.API_CONFIG_ID + ") REFERENCES "
                + Contract.ApiConfigs.TABLE + "(" + Contract.ApiConfigs.ID + ") ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_models_config ON " + Contract.Models.TABLE
                + "(" + Contract.Models.API_CONFIG_ID + ")");
    }

    private void addColumnIfNotExists(SQLiteDatabase db, String table, String column, String type) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (Exception ignored) {
            // 列已存在则忽略
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }
}
