package com.xxhy.simpleai.ui.history;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.SessionDao;
import com.xxhy.simpleai.data.model.Session;
import com.xxhy.simpleai.util.ExportHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 历史记录侧边栏 Fragment。支持搜索、多选、删除、导出。
 */
public class HistoryFragment extends Fragment implements SessionAdapter.OnSessionActionListener {

    public interface HistoryHost {
        void onOpenSession(long sessionId);
        void onNewSession();
        /** 手表模式下关闭历史记录视图返回聊天 */
        void onCloseHistory();
    }

    private MaterialToolbar toolbar;
    private SearchView searchView;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private LinearLayout bottomBar;
    private TextView textSelectionCount;
    private ImageButton btnSelectAll;
    private ImageButton btnExport;
    private ImageButton btnDelete;

    private SessionDao sessionDao;
    private SessionAdapter adapter;
    private HistoryHost host;

    /** 后台执行磁盘 I/O（导出），避免阻塞主线程 */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        sessionDao = new SessionDao(context);
        if (context instanceof HistoryHost) {
            host = (HistoryHost) context;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        toolbar = view.findViewById(R.id.toolbar_history);
        searchView = view.findViewById(R.id.search_view);
        recyclerView = view.findViewById(R.id.recycler_sessions);
        emptyView = view.findViewById(R.id.empty_view);
        bottomBar = view.findViewById(R.id.bottom_bar);
        textSelectionCount = view.findViewById(R.id.text_selection_count);
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnExport = view.findViewById(R.id.btn_export);
        btnDelete = view.findViewById(R.id.btn_delete);

        adapter = new SessionAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // 根据模式设置导航图标：手表=返回，手机=新建
        boolean isWatchMode = getResources().getBoolean(R.bool.is_watch_mode);
        if (isWatchMode) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
            toolbar.setNavigationContentDescription(R.string.back);
            toolbar.setNavigationOnClickListener(v -> {
                if (host != null) {
                    host.onCloseHistory();
                }
            });
            // 手表模式通过菜单项提供新建
            toolbar.inflateMenu(R.menu.menu_history_watch);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_new_session && host != null) {
                    host.onNewSession();
                    return true;
                }
                return false;
            });
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_add);
            toolbar.setNavigationContentDescription(R.string.new_session);
            toolbar.setNavigationOnClickListener(v -> {
                if (host != null) {
                    host.onNewSession();
                }
            });
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                loadSessions(newText);
                return true;
            }
        });

        btnSelectAll.setOnClickListener(v -> {
            if (adapter.isMultiSelectMode()) {
                if (adapter.getSelectedCount() == adapter.getItemCount()) {
                    adapter.deselectAll();
                } else {
                    adapter.selectAll();
                }
                updateSelectionCount();
            }
        });

        btnExport.setOnClickListener(v -> showExportDialog());

        btnDelete.setOnClickListener(v -> showDeleteConfirm());

        loadSessions(null);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    public void refresh() {
        if (searchView != null) {
            loadSessions(searchView.getQuery().toString());
        }
    }

    private void loadSessions(String keyword) {
        List<Session> list;
        if (TextUtils.isEmpty(keyword)) {
            list = sessionDao.queryAll();
        } else {
            list = sessionDao.search(keyword);
        }
        adapter.setSessions(list);
        emptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ===== SessionAdapter 回调 =====

    @Override
    public void onSessionClick(Session session) {
        if (host != null) {
            host.onOpenSession(session.getId());
        }
    }

    @Override
    public void onSessionRename(final Session session) {
        final android.widget.EditText input = new android.widget.EditText(getContext());
        input.setText(session.getTitle());
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.rename)
                .setView(input)
                .setPositiveButton(R.string.confirm, (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(title)) {
                        sessionDao.updateTitle(session.getId(), title);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onSessionExport(final Session session) {
        showExportFormatDialog(new long[]{session.getId()});
    }

    @Override
    public void onSessionDelete(final Session session) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.confirm_delete_sessions)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    sessionDao.delete(session.getId());
                    refresh();
                    Toast.makeText(getContext(), R.string.session_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void onMultiSelectModeChanged(boolean active, int selectedCount) {
        bottomBar.setVisibility(active ? View.VISIBLE : View.GONE);
        updateSelectionCount();
    }

    private void updateSelectionCount() {
        int count = adapter.getSelectedCount();
        textSelectionCount.setText(count + " / " + adapter.getItemCount());
    }

    // ===== 批量操作 =====

    private void showDeleteConfirm() {
        final List<Long> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) {
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.confirm_delete)
                .setMessage(R.string.confirm_delete_sessions)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    sessionDao.deleteAll(ids);
                    adapter.exitMultiSelectMode();
                    refresh();
                    Toast.makeText(getContext(), R.string.session_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showExportDialog() {
        final List<Long> ids = adapter.getSelectedIds();
        if (ids.isEmpty()) {
            return;
        }
        showExportFormatDialog(toArray(ids));
    }

    private void showExportFormatDialog(final long[] ids) {
        String[] items = {
                getString(R.string.export_json),
                getString(R.string.export_markdown),
                getString(R.string.export_txt)
        };
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.export)
                .setItems(items, (d, which) -> {
                    final int format = which;
                    // 使用 ExecutorService 替代已废弃的 AsyncTask，避免阻塞主线程
                    ioExecutor.execute(() -> {
                        String path = null;
                        try {
                            List<Long> idList = new ArrayList<>(ids.length);
                            for (long id : ids) {
                                idList.add(id);
                            }
                            path = new ExportHelper(getContext()).export(idList, format);
                        } catch (Exception ignored) {
                        }
                        final String result = path;
                        mainHandler.post(() -> {
                            if (result != null) {
                                Toast.makeText(getContext(),
                                        getString(R.string.export_success, ids.length)
                                                + "\n" + result,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getContext(), R.string.export_fail,
                                        Toast.LENGTH_SHORT).show();
                            }
                            if (adapter != null && adapter.isMultiSelectMode()) {
                                adapter.exitMultiSelectMode();
                            }
                        });
                    });
                })
                .show();
    }

    private long[] toArray(List<Long> list) {
        long[] arr = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** 退出多选模式（供 Activity 的返回键调用） */
    public boolean onBackPressed() {
        if (adapter != null && adapter.isMultiSelectMode()) {
            adapter.exitMultiSelectMode();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 释放后台线程资源
        ioExecutor.shutdown();
    }
}
