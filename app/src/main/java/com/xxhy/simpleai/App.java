package com.xxhy.simpleai;

import androidx.multidex.MultiDexApplication;

import com.xxhy.simpleai.data.db.ApiConfigDao;
import com.xxhy.simpleai.data.db.DbHelper;
import com.xxhy.simpleai.data.model.ApiConfig;
import com.xxhy.simpleai.data.prefs.SettingsManager;

import java.util.List;

/**
 * 应用入口，负责初始化数据库与全局设置。
 */
public class App extends MultiDexApplication {

    private static App instance;

    private DbHelper dbHelper;
    private SettingsManager settingsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        dbHelper = new DbHelper(this);
        settingsManager = new SettingsManager(this);
        seedDefaultConfigsIfNeeded();
    }

    public static App get() {
        return instance;
    }

    public DbHelper getDbHelper() {
        return dbHelper;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    /**
     * 首次启动时预置默认服务商配置（DeepSeek / 智谱 GLM / Kimi / OpenAI）。
     * 用户只需填写 API Key 即可使用，后续可删除。
     */
    private void seedDefaultConfigsIfNeeded() {
        ApiConfigDao dao = new ApiConfigDao(this);
        List<ApiConfig> existing = dao.queryAll();
        if (!existing.isEmpty()) {
            return;
        }
        String[][] presets = {
                {getString(R.string.preset_deepseek_name), getString(R.string.preset_deepseek_url),
                        getString(R.string.preset_deepseek_api_path),
                        getString(R.string.preset_deepseek_models)},
                {getString(R.string.preset_glm_name), getString(R.string.preset_glm_url),
                        getString(R.string.preset_glm_api_path),
                        getString(R.string.preset_glm_models)},
                {getString(R.string.preset_kimi_name), getString(R.string.preset_kimi_url),
                        getString(R.string.preset_kimi_api_path),
                        getString(R.string.preset_kimi_models)},
                {getString(R.string.preset_openai_name), getString(R.string.preset_openai_url),
                        getString(R.string.preset_openai_api_path),
                        getString(R.string.preset_openai_models)}
        };
        boolean first = true;
        for (String[] p : presets) {
            ApiConfig cfg = new ApiConfig();
            cfg.setName(p[0]);
            cfg.setBaseUrl(p[1]);
            cfg.setApiPath(p[2]);
            cfg.setModels(p[3]);
            cfg.setProviderType(ApiConfig.TYPE_OPENAI);
            cfg.setEnabled(true);
            cfg.setActive(first);
            dao.insert(cfg);
            first = false;
        }
    }
}
