package com.xxhy.simpleai;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.xxhy.simpleai.data.prefs.SettingsManager;
import com.xxhy.simpleai.ui.chat.ChatFragment;
import com.xxhy.simpleai.ui.history.HistoryFragment;
import com.xxhy.simpleai.ui.settings.SettingsActivity;

/**
 * 主界面，承载聊天 Fragment 与历史记录。
 * 手机模式：DrawerLayout 侧边栏。
 * 手表模式：全屏 Fragment 切换。
 */
public class MainActivity extends AppCompatActivity
        implements ChatFragment.Callback, HistoryFragment.HistoryHost {

    private DrawerLayout drawerLayout;
    private MaterialToolbar toolbar;
    private View drawerContainer;
    private ChatFragment chatFragment;
    private HistoryFragment historyFragment;
    private SettingsManager settings;
    private boolean isWatchMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用主题设置
        settings = App.get().getSettingsManager();
        applyTheme();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isWatchMode = getResources().getBoolean(R.bool.is_watch_mode);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerContainer = findViewById(R.id.drawer_container);

        if (isWatchMode) {
            setupWatchMode();
        } else {
            setupPhoneMode();
        }

        // 加载 Fragment
        if (savedInstanceState == null) {
            chatFragment = ChatFragment.newInstance();
            historyFragment = new HistoryFragment();

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.chat_container, chatFragment)
                    .replace(R.id.drawer_container, historyFragment)
                    .commit();
        } else {
            chatFragment = (ChatFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.chat_container);
            historyFragment = (HistoryFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.drawer_container);
        }

        // 加载上次会话
        long lastSession = settings.getCurrentSessionId();
        if (lastSession > 0 && chatFragment != null) {
            chatFragment.loadSession(lastSession);
        }
    }

    /** 手机模式：使用 DrawerLayout 抽屉 */
    private void setupPhoneMode() {
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.history, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }

    /** 手表模式：Toolbar 导航按钮切换全屏历史视图 */
    private void setupWatchMode() {
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationContentDescription(R.string.history);
        toolbar.setNavigationOnClickListener(v -> showHistory());
    }

    private void applyTheme() {
        String theme = settings.getTheme();
        switch (theme) {
            case SettingsManager.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case SettingsManager.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    private boolean inputBarHidden = false;
    private boolean wideRender = false;

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        MenuItem toggleItem = menu.findItem(R.id.action_toggle_input);
        if (toggleItem != null) {
            toggleItem.setTitle(inputBarHidden ? R.string.show_input_bar : R.string.hide_input_bar);
        }
        MenuItem wideItem = menu.findItem(R.id.action_wide_render);
        if (wideItem != null) {
            wideItem.setChecked(wideRender);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_chat) {
            if (chatFragment != null) {
                chatFragment.newSession();
            }
            return true;
        } else if (id == R.id.action_select_model) {
            if (chatFragment != null) {
                chatFragment.selectModel();
            }
            return true;
        } else if (id == R.id.action_toggle_input) {
            inputBarHidden = !inputBarHidden;
            if (chatFragment != null) {
                chatFragment.setInputBarVisible(!inputBarHidden);
            }
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_wide_render) {
            wideRender = !wideRender;
            if (chatFragment != null) {
                chatFragment.setWideRender(wideRender);
            }
            invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ===== ChatFragment.Callback =====

    @Override
    public void onToolbarTitleChanged(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    // ===== HistoryFragment.HistoryHost =====

    @Override
    public void onOpenSession(long sessionId) {
        if (chatFragment != null) {
            chatFragment.loadSession(sessionId);
            settings.setCurrentSessionId(sessionId);
        }
        closeHistory();
    }

    @Override
    public void onNewSession() {
        if (chatFragment != null) {
            chatFragment.newSession();
            settings.setCurrentSessionId(-1);
        }
        closeHistory();
    }

    @Override
    public void onCloseHistory() {
        closeHistory();
    }

    /** 显示历史记录视图 */
    private void showHistory() {
        if (isWatchMode) {
            drawerContainer.setVisibility(View.VISIBLE);
            if (historyFragment != null) {
                historyFragment.refresh();
            }
        } else if (drawerLayout != null) {
            drawerLayout.openDrawer(R.id.drawer_container);
        }
    }

    /** 关闭历史记录视图 */
    private void closeHistory() {
        if (isWatchMode) {
            drawerContainer.setVisibility(View.GONE);
        } else if (drawerLayout != null) {
            drawerLayout.closeDrawers();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 手表模式：先关闭历史视图
        if (isWatchMode && drawerContainer != null
                && drawerContainer.getVisibility() == View.VISIBLE) {
            if (historyFragment != null && historyFragment.onBackPressed()) {
                return;
            }
            drawerContainer.setVisibility(View.GONE);
            return;
        }
        // 手机模式：先关闭抽屉
        if (!isWatchMode && drawerLayout != null
                && drawerLayout.isDrawerOpen(R.id.drawer_container)) {
            if (historyFragment != null && historyFragment.onBackPressed()) {
                return;
            }
            drawerLayout.closeDrawers();
            return;
        }
        // 退出多选模式
        if (historyFragment != null && historyFragment.onBackPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (historyFragment != null) {
            historyFragment.refresh();
        }
    }
}
