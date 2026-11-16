package com.xxhy.simpleai.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.xxhy.simpleai.data.crypto.CryptoHelper;

/**
 * 全局设置管理器，基于 SharedPreferences。
 */
public class SettingsManager {

    private static final String PREFS = "app_settings";

    private static final String KEY_LANG = "language";
    private static final String KEY_THEME = "theme";
    private static final String KEY_CURRENT_SESSION = "current_session";
    private static final String KEY_TYPEWRITER = "typewriter";
    private static final String KEY_DEFAULT_CONTEXT = "default_context";
    private static final String KEY_LAST_MODEL_PREFIX = "last_model_";

    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";
    public static final String THEME_SYSTEM = "system";

    private final SharedPreferences prefs;
    private final CryptoHelper crypto;

    public SettingsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.crypto = new CryptoHelper(context);
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANG, LANG_ZH);
    }

    public void setLanguage(String lang) {
        prefs.edit().putString(KEY_LANG, lang).apply();
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(String theme) {
        prefs.edit().putString(KEY_THEME, theme).apply();
    }

    public long getCurrentSessionId() {
        return prefs.getLong(KEY_CURRENT_SESSION, -1);
    }

    public void setCurrentSessionId(long id) {
        prefs.edit().putLong(KEY_CURRENT_SESSION, id).apply();
    }

    public boolean isTypewriterEnabled() {
        return prefs.getBoolean(KEY_TYPEWRITER, true);
    }

    public void setTypewriterEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_TYPEWRITER, enabled).apply();
    }

    public int getDefaultContextLength() {
        return prefs.getInt(KEY_DEFAULT_CONTEXT, 20);
    }

    public void setDefaultContextLength(int length) {
        prefs.edit().putInt(KEY_DEFAULT_CONTEXT, length).apply();
    }

    public CryptoHelper getCrypto() {
        return crypto;
    }

    /** 记录指定提供商上次选择的模型 ID */
    public void setLastModel(long apiConfigId, String modelId) {
        prefs.edit().putString(KEY_LAST_MODEL_PREFIX + apiConfigId, modelId).apply();
    }

    /** 获取指定提供商上次选择的模型 ID，未记忆返回 null */
    public String getLastModel(long apiConfigId) {
        return prefs.getString(KEY_LAST_MODEL_PREFIX + apiConfigId, null);
    }
}
