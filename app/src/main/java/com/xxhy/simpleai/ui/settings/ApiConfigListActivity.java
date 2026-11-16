package com.xxhy.simpleai.ui.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.ApiConfigDao;
import com.xxhy.simpleai.data.model.ApiConfig;

import java.util.List;

/**
 * API 配置列表页。支持新增、编辑、删除、设为活跃。
 */
public class ApiConfigListActivity extends AppCompatActivity
        implements ApiConfigAdapter.OnConfigActionListener {

    private static final int REQ_EDIT = 1;

    private ApiConfigDao dao;
    private ApiConfigAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_config_list);

        dao = new ApiConfigDao(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recycler_configs);
        emptyView = findViewById(R.id.empty_view);

        adapter = new ApiConfigAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add);
        fab.setOnClickListener(v -> showAddConfigDialog());

        loadConfigs();
    }

    /** 新增配置：提供预设快捷创建与自定义入口 */
    private void showAddConfigDialog() {
        final String[] items = {
                getString(R.string.preset_deepseek_name),
                getString(R.string.preset_glm_name),
                getString(R.string.preset_kimi_name),
                getString(R.string.preset_openai_name),
                getString(R.string.custom_config)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_config)
                .setItems(items, (d, which) -> {
                    ApiConfig cfg = new ApiConfig();
                    cfg.setProviderType(ApiConfig.TYPE_OPENAI);
                    cfg.setEnabled(true);
                    switch (which) {
                        case 0:
                            cfg.setName(getString(R.string.preset_deepseek_name));
                            cfg.setBaseUrl(getString(R.string.preset_deepseek_url));
                            cfg.setApiPath(getString(R.string.preset_deepseek_api_path));
                            cfg.setModels(getString(R.string.preset_deepseek_models));
                            break;
                        case 1:
                            cfg.setName(getString(R.string.preset_glm_name));
                            cfg.setBaseUrl(getString(R.string.preset_glm_url));
                            cfg.setApiPath(getString(R.string.preset_glm_api_path));
                            cfg.setModels(getString(R.string.preset_glm_models));
                            break;
                        case 2:
                            cfg.setName(getString(R.string.preset_kimi_name));
                            cfg.setBaseUrl(getString(R.string.preset_kimi_url));
                            cfg.setApiPath(getString(R.string.preset_kimi_api_path));
                            cfg.setModels(getString(R.string.preset_kimi_models));
                            break;
                        case 3:
                            cfg.setName(getString(R.string.preset_openai_name));
                            cfg.setBaseUrl(getString(R.string.preset_openai_url));
                            cfg.setApiPath(getString(R.string.preset_openai_api_path));
                            cfg.setModels(getString(R.string.preset_openai_models));
                            break;
                        default:
                            cfg.setApiPath("/v1/chat/completions");
                            break;
                    }
                    long id = dao.insert(cfg);
                    Intent intent = new Intent(this, ApiConfigEditActivity.class);
                    intent.putExtra(ApiConfigEditActivity.EXTRA_CONFIG_ID, id);
                    startActivityForResult(intent, REQ_EDIT);
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConfigs();
    }

    private void loadConfigs() {
        List<ApiConfig> list = dao.queryAll();
        adapter.setConfigs(list);
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onConfigClick(ApiConfig config) {
        dao.setActive(config.getId());
        loadConfigs();
        Toast.makeText(this, R.string.set_active, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConfigSetActive(ApiConfig config) {
        dao.setActive(config.getId());
        loadConfigs();
    }

    @Override
    public void onConfigEdit(ApiConfig config) {
        Intent intent = new Intent(this, ApiConfigEditActivity.class);
        intent.putExtra(ApiConfigEditActivity.EXTRA_CONFIG_ID, config.getId());
        startActivityForResult(intent, REQ_EDIT);
    }

    @Override
    public void onConfigDelete(final ApiConfig config) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.confirm_delete_config)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    dao.delete(config.getId());
                    loadConfigs();
                    Toast.makeText(this, R.string.config_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EDIT) {
            loadConfigs();
        }
    }
}
