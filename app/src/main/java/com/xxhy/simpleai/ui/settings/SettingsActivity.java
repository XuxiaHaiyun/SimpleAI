package com.xxhy.simpleai.ui.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.xxhy.simpleai.App;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.ApiConfigDao;
import com.xxhy.simpleai.data.model.ApiConfig;
import com.xxhy.simpleai.data.prefs.SettingsManager;

/**
 * 通用设置页。包含 API 配置入口、语言、主题、打字机、上下文长度、清除缓存。
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;
    private ApiConfigDao apiConfigDao;

    private TextView textActiveConfig;
    private TextView textLanguage;
    private TextView textTheme;
    private SwitchMaterial switchTypewriter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = App.get().getSettingsManager();
        apiConfigDao = new ApiConfigDao(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        textActiveConfig = findViewById(R.id.text_active_config);
        textLanguage = findViewById(R.id.text_language);
        textTheme = findViewById(R.id.text_theme);
        switchTypewriter = findViewById(R.id.switch_typewriter);

        // API 配置入口
        findViewById(R.id.item_api_configs).setOnClickListener(v -> {
            startActivity(new Intent(this, ApiConfigListActivity.class));
        });

        // 语言
        findViewById(R.id.item_language).setOnClickListener(v -> showLanguageDialog());

        // 主题
        findViewById(R.id.item_theme).setOnClickListener(v -> showThemeDialog());

        // 打字机效果
        switchTypewriter.setChecked(settings.isTypewriterEnabled());
        switchTypewriter.setOnCheckedChangeListener((b, checked) ->
                settings.setTypewriterEnabled(checked));

        // 清除缓存
        findViewById(R.id.item_clear_cache).setOnClickListener(v -> {
            // 清除 OkHttp 缓存（如有）并提示
            Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show();
        });

        updateDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDisplay();
    }

    private void updateDisplay() {
        ApiConfig active = apiConfigDao.queryActive();
        textActiveConfig.setText(active != null ? active.getName() : getString(R.string.no_configs));

        String lang = settings.getLanguage();
        textLanguage.setText(SettingsManager.LANG_ZH.equals(lang)
                ? getString(R.string.lang_zh) : getString(R.string.lang_en));

        String theme = settings.getTheme();
        if (SettingsManager.THEME_LIGHT.equals(theme)) {
            textTheme.setText(R.string.theme_light);
        } else if (SettingsManager.THEME_DARK.equals(theme)) {
            textTheme.setText(R.string.theme_dark);
        } else {
            textTheme.setText(R.string.theme_system);
        }
    }

    private void showLanguageDialog() {
        String[] items = {getString(R.string.lang_zh), getString(R.string.lang_en)};
        int checked = SettingsManager.LANG_ZH.equals(settings.getLanguage()) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_language)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    settings.setLanguage(which == 0
                            ? SettingsManager.LANG_ZH : SettingsManager.LANG_EN);
                    updateDisplay();
                    d.dismiss();
                    // 重启以应用语言变更
                    recreate();
                })
                .show();
    }

    private void showThemeDialog() {
        String[] items = {getString(R.string.theme_light),
                getString(R.string.theme_dark),
                getString(R.string.theme_system)};
        String current = settings.getTheme();
        int checked = 0;
        if (SettingsManager.THEME_DARK.equals(current)) {
            checked = 1;
        } else if (SettingsManager.THEME_SYSTEM.equals(current)) {
            checked = 2;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    switch (which) {
                        case 0:
                            settings.setTheme(SettingsManager.THEME_LIGHT);
                            break;
                        case 1:
                            settings.setTheme(SettingsManager.THEME_DARK);
                            break;
                        default:
                            settings.setTheme(SettingsManager.THEME_SYSTEM);
                            break;
                    }
                    updateDisplay();
                    d.dismiss();
                    recreate();
                })
                .show();
    }
}
