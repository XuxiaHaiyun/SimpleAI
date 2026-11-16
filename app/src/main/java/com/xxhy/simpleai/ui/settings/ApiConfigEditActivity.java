package com.xxhy.simpleai.ui.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.ApiConfigDao;
import com.xxhy.simpleai.data.model.ApiConfig;

/**
 * API 配置编辑/新建页。模仿 rikkahub 的 ProviderConfigure：
 * 底部 Tab 栏切换「配置」与「模型」两个页面。
 */
public class ApiConfigEditActivity extends AppCompatActivity
        implements ProviderConfigFragment.Host, ProviderModelsFragment.Host {

    public static final String EXTRA_CONFIG_ID = "config_id";

    private MaterialToolbar toolbar;
    private ViewPager viewPager;
    private BottomNavigationView bottomNav;

    private ApiConfigDao dao;
    private ApiConfig config;
    private boolean isNew;

    private ProviderConfigFragment configFragment;
    private ProviderModelsFragment modelsFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_config_edit);

        dao = new ApiConfigDao(this);

        toolbar = findViewById(R.id.toolbar);
        viewPager = findViewById(R.id.view_pager);
        bottomNav = findViewById(R.id.bottom_nav);

        toolbar.setNavigationOnClickListener(v -> finish());

        long configId = getIntent().getLongExtra(EXTRA_CONFIG_ID, -1);
        if (configId > 0) {
            config = dao.queryById(configId);
            if (config == null) {
                config = new ApiConfig();
                config.setId(configId);
                isNew = true;
            } else {
                isNew = false;
            }
            toolbar.setTitle(isNew ? R.string.new_config : R.string.edit_config);
        } else {
            config = new ApiConfig();
            isNew = true;
            toolbar.setTitle(R.string.new_config);
        }

        configFragment = new ProviderConfigFragment();
        modelsFragment = new ProviderModelsFragment();

        viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                bottomNav.getMenu().getItem(position).setChecked(true);
            }
        });

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_config) {
                viewPager.setCurrentItem(0);
            } else if (id == R.id.tab_models) {
                // 切换到模型 Tab 前先保存配置（新配置需要先持久化以获得 ID）
                if (isNew || config.getId() <= 0) {
                    Toast.makeText(this, R.string.setting_provider_page_save, Toast.LENGTH_SHORT).show();
                    return false;
                }
                viewPager.setCurrentItem(1);
            }
            return true;
        });
    }

    @Override
    public ApiConfig getConfig() {
        return config;
    }

    @Override
    public void onConfigSaved(ApiConfig cfg) {
        if (isNew) {
            long id = dao.insert(cfg);
            cfg.setId(id);
            config.setId(id);
            isNew = false;
            toolbar.setTitle(R.string.edit_config);
        } else {
            dao.update(cfg);
        }
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show();
    }

    /** ViewPager 适配器 */
    private class PagerAdapter extends FragmentStatePagerAdapter {
        PagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            return position == 0 ? configFragment : modelsFragment;
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
