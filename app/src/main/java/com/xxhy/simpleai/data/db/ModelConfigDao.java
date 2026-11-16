package com.xxhy.simpleai.data.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.xxhy.simpleai.App;
import com.xxhy.simpleai.data.model.ModelConfig;
import com.xxhy.simpleai.registry.ModelRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型配置表数据访问对象。
 */
public class ModelConfigDao {

    private final DbHelper helper;

    public ModelConfigDao(Context context) {
        this.helper = App.get() != null ? App.get().getDbHelper() : new DbHelper(context);
    }

    public long insert(ModelConfig model) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = toContentValues(model);
        cv.put(Contract.Models.CREATED_AT, System.currentTimeMillis());
        long id = db.insert(Contract.Models.TABLE, null, cv);
        model.setId(id);
        return id;
    }

    public int update(ModelConfig model) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues cv = toContentValues(model);
        return db.update(Contract.Models.TABLE, cv,
                Contract.Models.ID + "=?", new String[]{String.valueOf(model.getId())});
    }

    public void delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(Contract.Models.TABLE,
                Contract.Models.ID + "=?",
                new String[]{String.valueOf(id)});
    }

    public ModelConfig queryById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Models.TABLE, null,
                Contract.Models.ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        ModelConfig model = null;
        if (c.moveToFirst()) {
            model = cursorToModel(c);
        }
        c.close();
        return model;
    }

    /** 查询指定 API 配置下的所有模型 */
    public List<ModelConfig> queryByConfig(long apiConfigId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Models.TABLE, null,
                Contract.Models.API_CONFIG_ID + "=?",
                new String[]{String.valueOf(apiConfigId)},
                null, null, Contract.Models.CREATED_AT + " ASC");
        List<ModelConfig> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(cursorToModel(c));
        }
        c.close();
        return list;
    }

    /** 查询指定 API 配置下匹配 modelId 的模型 */
    public ModelConfig queryByModelId(long apiConfigId, String modelId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(Contract.Models.TABLE, null,
                Contract.Models.API_CONFIG_ID + "=? AND " + Contract.Models.MODEL_ID + "=?",
                new String[]{String.valueOf(apiConfigId), modelId},
                null, null, null, "1");
        ModelConfig model = null;
        if (c.moveToFirst()) {
            model = cursorToModel(c);
        }
        c.close();
        return model;
    }

    /**
     * 确保指定 API 配置下存在对应模型记录。
     * 若该配置下尚无任何模型记录，则根据 modelsCsv（逗号分隔）自动创建，
     * 并通过 ModelRegistry 自动推断模态与能力（视觉/推理/工具）。
     *
     * @return 实际新增的模型数量
     */
    public int ensureModelsFromCsv(long apiConfigId, String modelsCsv) {
        if (apiConfigId <= 0 || modelsCsv == null || modelsCsv.trim().isEmpty()) {
            return 0;
        }
        // 已有模型记录则跳过
        if (!queryByConfig(apiConfigId).isEmpty()) {
            return 0;
        }
        int added = 0;
        for (String raw : modelsCsv.split(",")) {
            String id = raw.trim();
            if (id.isEmpty()) continue;
            ModelConfig m = new ModelConfig();
            m.setApiConfigId(apiConfigId);
            m.setModelId(id);
            m.setDisplayName(id);
            // 通过 ModelRegistry 自动推断模态与能力
            ModelRegistry.applyTo(m);
            insert(m);
            added++;
        }
        return added;
    }

    private ContentValues toContentValues(ModelConfig m) {
        ContentValues cv = new ContentValues();
        cv.put(Contract.Models.API_CONFIG_ID, m.getApiConfigId());
        cv.put(Contract.Models.MODEL_ID, m.getModelId());
        cv.put(Contract.Models.DISPLAY_NAME, m.getDisplayName());
        cv.put(Contract.Models.TYPE, m.getType());
        cv.put(Contract.Models.INPUT_MODALITIES, m.getInputModalities());
        cv.put(Contract.Models.OUTPUT_MODALITIES, m.getOutputModalities());
        cv.put(Contract.Models.ABILITIES, m.getAbilities());
        cv.put(Contract.Models.CUSTOM_HEADERS, m.getCustomHeaders());
        cv.put(Contract.Models.CUSTOM_BODIES, m.getCustomBodies());
        cv.put(Contract.Models.PROVIDER_OVERWRITE, m.getProviderOverwrite());
        return cv;
    }

    private ModelConfig cursorToModel(Cursor c) {
        ModelConfig m = new ModelConfig();
        m.setId(c.getLong(c.getColumnIndexOrThrow(Contract.Models.ID)));
        m.setApiConfigId(c.getLong(c.getColumnIndexOrThrow(Contract.Models.API_CONFIG_ID)));
        m.setModelId(c.getString(c.getColumnIndexOrThrow(Contract.Models.MODEL_ID)));
        m.setDisplayName(c.getString(c.getColumnIndexOrThrow(Contract.Models.DISPLAY_NAME)));
        m.setType(c.getString(c.getColumnIndexOrThrow(Contract.Models.TYPE)));
        m.setInputModalities(c.getString(c.getColumnIndexOrThrow(Contract.Models.INPUT_MODALITIES)));
        m.setOutputModalities(c.getString(c.getColumnIndexOrThrow(Contract.Models.OUTPUT_MODALITIES)));
        m.setAbilities(c.getString(c.getColumnIndexOrThrow(Contract.Models.ABILITIES)));
        m.setCustomHeaders(c.getString(c.getColumnIndexOrThrow(Contract.Models.CUSTOM_HEADERS)));
        m.setCustomBodies(c.getString(c.getColumnIndexOrThrow(Contract.Models.CUSTOM_BODIES)));
        m.setProviderOverwrite(c.getString(c.getColumnIndexOrThrow(Contract.Models.PROVIDER_OVERWRITE)));
        m.setCreatedAt(c.getLong(c.getColumnIndexOrThrow(Contract.Models.CREATED_AT)));
        return m;
    }
}
