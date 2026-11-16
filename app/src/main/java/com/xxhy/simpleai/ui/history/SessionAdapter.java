package com.xxhy.simpleai.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.model.Session;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 历史会话列表适配器，支持单击打开、长按多选。
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionHolder> {

    public interface OnSessionActionListener {
        void onSessionClick(Session session);
        void onSessionRename(Session session);
        void onSessionExport(Session session);
        void onSessionDelete(Session session);
        void onMultiSelectModeChanged(boolean active, int selectedCount);
    }

    private final List<Session> sessions = new ArrayList<>();
    private final Set<Long> selectedIds = new HashSet<>();
    private final OnSessionActionListener listener;
    private boolean multiSelectMode = false;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public SessionAdapter(OnSessionActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SessionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionHolder holder, int position) {
        Session session = sessions.get(position);
        holder.bind(session);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    public void setSessions(List<Session> list) {
        sessions.clear();
        sessions.addAll(list);
        notifyDataSetChanged();
    }

    public void enterMultiSelectMode(long firstId) {
        multiSelectMode = true;
        selectedIds.clear();
        selectedIds.add(firstId);
        notifyDataSetChanged();
        notifyModeChanged();
    }

    public void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedIds.clear();
        notifyDataSetChanged();
        notifyModeChanged();
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public void selectAll() {
        for (Session s : sessions) {
            selectedIds.add(s.getId());
        }
        notifyDataSetChanged();
        notifyModeChanged();
    }

    public void deselectAll() {
        selectedIds.clear();
        notifyDataSetChanged();
        notifyModeChanged();
    }

    public List<Long> getSelectedIds() {
        return new ArrayList<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    private void toggleSelection(long id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (selectedIds.isEmpty()) {
            exitMultiSelectMode();
        } else {
            notifyDataSetChanged();
            notifyModeChanged();
        }
    }

    private void notifyModeChanged() {
        if (listener != null) {
            listener.onMultiSelectModeChanged(multiSelectMode, selectedIds.size());
        }
    }

    class SessionHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutRoot;
        TextView textTitle;
        TextView textTime;
        CheckBox checkbox;

        SessionHolder(@NonNull View itemView) {
            super(itemView);
            layoutRoot = itemView.findViewById(R.id.layout_root);
            textTitle = itemView.findViewById(R.id.text_title);
            textTime = itemView.findViewById(R.id.text_time);
            checkbox = itemView.findViewById(R.id.checkbox);
        }

        void bind(final Session session) {
            textTitle.setText(session.getTitle());
            textTime.setText(dateFormat.format(new Date(session.getUpdatedAt())));

            if (multiSelectMode) {
                checkbox.setVisibility(View.VISIBLE);
                checkbox.setChecked(selectedIds.contains(session.getId()));
                // 禁用 CheckBox 自身点击，让事件透传到 layoutRoot 统一处理
                checkbox.setClickable(false);
                checkbox.setFocusable(false);
            } else {
                checkbox.setVisibility(View.GONE);
            }

            layoutRoot.setBackgroundResource(selectedIds.contains(session.getId())
                    ? R.color.bg_selected : 0);

            layoutRoot.setOnClickListener(v -> {
                if (multiSelectMode) {
                    toggleSelection(session.getId());
                } else {
                    if (listener != null) {
                        listener.onSessionClick(session);
                    }
                }
            });

            layoutRoot.setOnLongClickListener(v -> {
                if (!multiSelectMode) {
                    enterMultiSelectMode(session.getId());
                } else {
                    toggleSelection(session.getId());
                }
                return true;
            });
        }
    }
}
