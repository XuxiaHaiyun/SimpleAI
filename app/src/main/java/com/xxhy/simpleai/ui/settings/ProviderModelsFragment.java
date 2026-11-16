package com.xxhy.simpleai.ui.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.ModelConfigDao;
import com.xxhy.simpleai.data.model.ApiConfig;
import com.xxhy.simpleai.data.model.ModelConfig;
import com.xxhy.simpleai.network.ApiClient;
import com.xxhy.simpleai.registry.ModelRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 提供商详情页 - 模型 Tab。
 * 支持添加模型、获取在线模型列表、点开模型详细配置。
 *
 * 模仿 rikkahub 的 ModelList：切换到本 Tab 时自动获取在线模型列表，
 * 并通过 ModelRegistry 自动判断模型能力（推理/视觉/工具）。
 */
public class ProviderModelsFragment extends Fragment {

    private RecyclerView recycler;
    private TextView textEmpty;
    private MaterialButton btnFetchOnline;
    private MaterialButton btnAddModel;
    private View rootView;

    private ModelConfigDao dao;
    private ModelAdapter adapter;
    private List<ModelConfig> models = new ArrayList<>();

    /** 是否已对本配置执行过自动获取（避免重复请求） */
    private boolean autoFetchAttempted = false;
    /** 上一次自动获取时的配置 ID，配置切换后重新允许自动获取 */
    private long autoFetchConfigId = -1L;

    interface Host {
        ApiConfig getConfig();
    }

    private Host host;

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof Host) {
            host = (Host) context;
        } else {
            throw new IllegalStateException("Activity must implement Host");
        }
        dao = new ModelConfigDao(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_provider_models, container, false);
        recycler = rootView.findViewById(R.id.recycler_models);
        textEmpty = rootView.findViewById(R.id.text_empty);
        btnFetchOnline = rootView.findViewById(R.id.btn_fetch_online);
        btnAddModel = rootView.findViewById(R.id.btn_add_model);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ModelAdapter();
        recycler.setAdapter(adapter);

        btnAddModel.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ModelEditActivity.class);
            intent.putExtra(ModelEditActivity.EXTRA_API_CONFIG_ID, host.getConfig().getId());
            startActivity(intent);
        });

        btnFetchOnline.setOnClickListener(v -> fetchOnlineModels(false));
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadModels();
        // 模仿 rikkahub：首次进入模型 Tab 自动获取在线模型列表
        maybeAutoFetchOnlineModels();
    }

    /**
     * 自动获取在线模型列表（仅在每个配置首次进入时触发一次）。
     * 需要配置已保存且包含 Base URL 与 API Key。
     */
    private void maybeAutoFetchOnlineModels() {
        ApiConfig config = host.getConfig();
        if (config == null) return;
        long configId = config.getId();
        // 配置切换后重置自动获取标记
        if (configId != autoFetchConfigId) {
            autoFetchAttempted = false;
            autoFetchConfigId = configId;
        }
        if (autoFetchAttempted) return;
        if (configId <= 0) return;
        if (TextUtils.isEmpty(config.getBaseUrl())
                || TextUtils.isEmpty(config.getApiKeyEncrypted())) {
            return;
        }
        autoFetchAttempted = true;
        fetchOnlineModels(true);
    }

    private void loadModels() {
        long configId = host.getConfig().getId();
        if (configId <= 0) {
            models.clear();
            adapter.notifyDataSetChanged();
            updateEmpty();
            return;
        }
        models = dao.queryByConfig(configId);
        adapter.notifyDataSetChanged();
        updateEmpty();
    }

    private void updateEmpty() {
        textEmpty.setVisibility(models.isEmpty() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(models.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * 获取在线模型列表。
     *
     * @param silent 是否静默模式（自动触发时为 true，不弹错误对话框，仅 Toast）
     */
    private void fetchOnlineModels(boolean silent) {
        ApiConfig config = host.getConfig();
        if (config.getId() <= 0) {
            Toast.makeText(getContext(), R.string.setting_provider_page_save, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(config.getBaseUrl()) || TextUtils.isEmpty(config.getApiKeyEncrypted())) {
            Toast.makeText(getContext(), R.string.setting_provider_page_test_fail,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        btnFetchOnline.setEnabled(false);
        btnFetchOnline.setText(R.string.fetching_models);
        ApiClient.get().fetchModels(config, new ApiClient.ModelsCallback() {
            @Override
            public void onSuccess(List<String> modelIds) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnFetchOnline.setEnabled(true);
                    btnFetchOnline.setText(R.string.fetch_online_models);
                    if (modelIds.isEmpty()) {
                        if (!silent) {
                            Toast.makeText(getContext(), R.string.fetch_models_fail,
                                    Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }
                    // 批量插入不存在的模型，并通过 ModelRegistry 自动判断能力
                    int added = 0;
                    for (String id : modelIds) {
                        if (dao.queryByModelId(config.getId(), id) == null) {
                            ModelConfig m = new ModelConfig();
                            m.setApiConfigId(config.getId());
                            m.setModelId(id);
                            m.setDisplayName(id);
                            // 自动推断模态与能力（推理/视觉/工具）
                            ModelRegistry.applyTo(m);
                            dao.insert(m);
                            added++;
                        }
                    }
                    loadModels();
                    if (!silent || added > 0) {
                        Toast.makeText(getContext(),
                                getString(R.string.fetch_models_success, modelIds.size()),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnFetchOnline.setEnabled(true);
                    btnFetchOnline.setText(R.string.fetch_online_models);
                    if (silent) {
                        // 自动获取失败时仅 Toast 提示，不弹对话框
                        Toast.makeText(getContext(),
                                getString(R.string.fetch_models_fail, message),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        new AlertDialog.Builder(getContext())
                                .setMessage(getString(R.string.fetch_models_fail, message))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                });
            }
        });
    }

    /** 模型列表适配器 */
    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_model_config, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ModelConfig m = models.get(position);
            holder.textName.setText(m.getDisplayLabel());
            holder.textId.setText(m.getModelId());
            StringBuilder badges = new StringBuilder();
            if (m.isVisionSupported()) badges.append(getString(R.string.setting_provider_page_vision)).append("  ");
            if (m.isReasoningSupported()) badges.append(getString(R.string.ability_reasoning)).append("  ");
            if (m.isToolSupported()) badges.append(getString(R.string.ability_tool));
            holder.textBadges.setText(badges.toString().trim());
            holder.btnEdit.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), ModelEditActivity.class);
                intent.putExtra(ModelEditActivity.EXTRA_MODEL_ID, m.getId());
                intent.putExtra(ModelEditActivity.EXTRA_API_CONFIG_ID, m.getApiConfigId());
                startActivity(intent);
            });
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setMessage(R.string.confirm_delete_model)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            dao.delete(m.getId());
                            loadModels();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView textName;
            TextView textId;
            TextView textBadges;
            View btnEdit;
            View btnDelete;

            VH(@NonNull View itemView) {
                super(itemView);
                textName = itemView.findViewById(R.id.text_model_name);
                textId = itemView.findViewById(R.id.text_model_id);
                textBadges = itemView.findViewById(R.id.text_model_badges);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
