package com.xxhy.simpleai.ui.settings;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.model.ApiConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * API 配置列表适配器。
 * 模仿 rikkahub 的 ProviderItem：仅展示名称、URL、状态标签与模型数量，
 * 不展示提供商模型文本。
 */
public class ApiConfigAdapter extends RecyclerView.Adapter<ApiConfigAdapter.ConfigHolder> {

    public interface OnConfigActionListener {
        void onConfigClick(ApiConfig config);
        void onConfigSetActive(ApiConfig config);
        void onConfigEdit(ApiConfig config);
        void onConfigDelete(ApiConfig config);
    }

    private final List<ApiConfig> configs = new ArrayList<>();
    private final OnConfigActionListener listener;

    public ApiConfigAdapter(OnConfigActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConfigHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_api_config, parent, false);
        return new ConfigHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConfigHolder holder, int position) {
        holder.bind(configs.get(position));
    }

    @Override
    public int getItemCount() {
        return configs.size();
    }

    public void setConfigs(List<ApiConfig> list) {
        configs.clear();
        configs.addAll(list);
        notifyDataSetChanged();
    }

    class ConfigHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutRoot;
        TextView textName;
        TextView textUrl;
        TextView textModels;
        TextView textActive;
        TextView badgeDisabled;
        TextView badgeVision;
        TextView badgeReasoning;
        TextView badgeSearch;

        ConfigHolder(@NonNull View itemView) {
            super(itemView);
            layoutRoot = itemView.findViewById(R.id.layout_root);
            textName = itemView.findViewById(R.id.text_name);
            textUrl = itemView.findViewById(R.id.text_url);
            textModels = itemView.findViewById(R.id.text_models);
            textActive = itemView.findViewById(R.id.text_active);
            badgeDisabled = itemView.findViewById(R.id.badge_disabled);
            badgeVision = itemView.findViewById(R.id.badge_vision);
            badgeReasoning = itemView.findViewById(R.id.badge_reasoning);
            badgeSearch = itemView.findViewById(R.id.badge_search);
        }

        void bind(final ApiConfig config) {
            textName.setText(config.getName());
            textUrl.setText(config.getBaseUrl() + config.getApiPath());
            // 不展示提供商模型文本，改为展示模型数量（模仿 rikkahub ProviderItem）
            int modelCount = countModels(config.getModels());
            if (modelCount > 0) {
                textModels.setText(textModels.getContext().getString(
                        R.string.setting_provider_page_model_count, modelCount));
                textModels.setVisibility(View.VISIBLE);
            } else {
                textModels.setVisibility(View.GONE);
            }
            textActive.setVisibility(config.isActive() ? View.VISIBLE : View.GONE);
            // 能力标记：未启用时显示"启用"标签提示状态
            badgeDisabled.setVisibility(config.isEnabled() ? View.GONE : View.VISIBLE);
            badgeVision.setVisibility(config.isVisionSupported() ? View.VISIBLE : View.GONE);
            badgeReasoning.setVisibility(config.isReasoningSupported() ? View.VISIBLE : View.GONE);
            badgeSearch.setVisibility(config.isSearchEnabled() ? View.VISIBLE : View.GONE);

            layoutRoot.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConfigClick(config);
                }
            });

            layoutRoot.setOnLongClickListener(v -> {
                showPopupMenu(v, config);
                return true;
            });
        }

        /** 统计 models CSV 字符串中的模型数量 */
        private int countModels(String models) {
            if (models == null || models.trim().isEmpty()) return 0;
            int count = 0;
            for (String p : models.split(",")) {
                if (!p.trim().isEmpty()) count++;
            }
            return count;
        }

        private void showPopupMenu(View anchor, final ApiConfig config) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);
            popup.inflate(R.menu.menu_config_popup);
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.action_set_active) {
                        if (listener != null) listener.onConfigSetActive(config);
                        return true;
                    } else if (id == R.id.action_edit_config) {
                        if (listener != null) listener.onConfigEdit(config);
                        return true;
                    } else if (id == R.id.action_delete_config) {
                        if (listener != null) listener.onConfigDelete(config);
                        return true;
                    }
                    return false;
                }
            });
            popup.show();
        }
    }
}
