package com.xxhy.simpleai.ui.settings;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.xxhy.simpleai.App;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.model.ApiConfig;
import com.xxhy.simpleai.network.ApiClient;

/**
 * 提供商详情页 - 配置 Tab。
 * 包含：启用开关、名称、API Key、Base URL、API 路径、Response API、余额获取、测试连接、保存。
 */
public class ProviderConfigFragment extends Fragment {

    private MaterialButtonToggleGroup toggleProviderType;
    private SwitchMaterial switchEnabled;
    private SwitchMaterial switchResponseApi;
    private SwitchMaterial switchBalance;
    private LinearLayout balancePathContainer;
    private TextInputEditText editBalanceApiPath;
    private TextInputEditText editBalanceResultPath;
    private TextInputEditText editName;
    private TextInputEditText editApiKey;
    private TextInputEditText editBaseUrl;
    private TextInputEditText editApiPath;
    private MaterialButton btnTest;
    private MaterialButton btnSave;

    private View rootView;

    /** 宿主 Activity 提供配置对象与保存回调 */
    interface Host {
        ApiConfig getConfig();
        void onConfigSaved(ApiConfig config);
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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_provider_config, container, false);
        bindViews();
        fillForm();
        setupListeners();
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 返回时同步最新配置（例如其它 Tab 修改）
        fillForm();
    }

    private void bindViews() {
        toggleProviderType = rootView.findViewById(R.id.toggle_provider_type);
        switchEnabled = rootView.findViewById(R.id.switch_enabled);
        switchResponseApi = rootView.findViewById(R.id.switch_response_api);
        switchBalance = rootView.findViewById(R.id.switch_balance);
        balancePathContainer = rootView.findViewById(R.id.balance_path_container);
        editBalanceApiPath = rootView.findViewById(R.id.edit_balance_api_path);
        editBalanceResultPath = rootView.findViewById(R.id.edit_balance_result_path);
        editName = rootView.findViewById(R.id.edit_name);
        editApiKey = rootView.findViewById(R.id.edit_api_key);
        editBaseUrl = rootView.findViewById(R.id.edit_base_url);
        editApiPath = rootView.findViewById(R.id.edit_api_path);
        btnTest = rootView.findViewById(R.id.btn_test);
        btnSave = rootView.findViewById(R.id.btn_save);
    }

    private void fillForm() {
        ApiConfig config = host.getConfig();
        selectProviderType(config.getProviderType());
        switchEnabled.setChecked(config.isEnabled());
        switchResponseApi.setChecked(config.isUseResponseApi());
        switchBalance.setChecked(config.isBalanceEnabled());
        balancePathContainer.setVisibility(config.isBalanceEnabled() ? View.VISIBLE : View.GONE);
        editBalanceApiPath.setText(config.getBalanceApiPath());
        editBalanceResultPath.setText(config.getBalanceResultPath());
        editName.setText(config.getName());
        // 解密 API Key 显示明文
        if (config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isEmpty()) {
            try {
                String key = App.get().getSettingsManager().getCrypto()
                        .decrypt(config.getApiKeyEncrypted());
                editApiKey.setText(key);
            } catch (Exception e) {
                // 解密失败时可能是明文存储（加密降级），直接显示原值
                editApiKey.setText(config.getApiKeyEncrypted());
            }
        } else {
            editApiKey.setText("");
        }
        editBaseUrl.setText(config.getBaseUrl());
        editApiPath.setText(config.getApiPath());
    }

    private void setupListeners() {
        switchBalance.setOnCheckedChangeListener((b, checked) ->
                balancePathContainer.setVisibility(checked ? View.VISIBLE : View.GONE));

        btnTest.setOnClickListener(v -> doTestConnection());
        btnSave.setOnClickListener(v -> doSave());
    }

    private void selectProviderType(String type) {
        int id = R.id.btn_type_openai;
        if (ApiConfig.TYPE_CLAUDE.equals(type)) {
            id = R.id.btn_type_claude;
        } else if (ApiConfig.TYPE_GOOGLE.equals(type)) {
            id = R.id.btn_type_google;
        }
        toggleProviderType.check(id);
    }

    private String getSelectedProviderType() {
        int id = toggleProviderType.getCheckedButtonId();
        if (id == R.id.btn_type_claude) return ApiConfig.TYPE_CLAUDE;
        if (id == R.id.btn_type_google) return ApiConfig.TYPE_GOOGLE;
        return ApiConfig.TYPE_OPENAI;
    }

    /** 从表单收集最新配置（同步到 host 持有的对象） */
    public ApiConfig collectFromForm() {
        ApiConfig config = host.getConfig();
        config.setEnabled(switchEnabled.isChecked());
        config.setProviderType(getSelectedProviderType());
        config.setUseResponseApi(switchResponseApi.isChecked());
        config.setBalanceEnabled(switchBalance.isChecked());
        config.setBalanceApiPath(textOf(editBalanceApiPath, "/credits"));
        config.setBalanceResultPath(textOf(editBalanceResultPath, "data.total_usage"));
        config.setName(textOf(editName, ""));
        config.setBaseUrl(textOf(editBaseUrl, ""));
        config.setApiPath(textOf(editApiPath, "/v1/chat/completions"));
        // 加密 API Key
        String apiKey = textOf(editApiKey, "");
        if (!apiKey.isEmpty()) {
            try {
                config.setApiKeyEncrypted(App.get().getSettingsManager()
                        .getCrypto().encrypt(apiKey));
            } catch (Exception e) {
                // 加密失败时直接存储明文（兼容降级），避免丢失用户输入
                config.setApiKeyEncrypted(apiKey);
            }
        } else {
            // 用户清空了 API Key，需要清除旧的加密值
            config.setApiKeyEncrypted("");
        }
        return config;
    }

    private static String textOf(TextInputEditText et, String def) {
        String s = et.getText() == null ? "" : et.getText().toString().trim();
        return s.isEmpty() ? def : s;
    }

    private void doTestConnection() {
        ApiConfig cfg = collectFromForm();
        if (TextUtils.isEmpty(cfg.getBaseUrl())) {
            Toast.makeText(getContext(), R.string.base_url, Toast.LENGTH_SHORT).show();
            return;
        }
        btnTest.setEnabled(false);
        btnTest.setText(R.string.setting_provider_page_testing);
        ApiClient.get().testConnection(cfg, new ApiClient.TestCallback() {
            @Override
            public void onSuccess() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText(R.string.setting_provider_page_test_connection);
                    Toast.makeText(getContext(), R.string.setting_provider_page_test_pass, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    btnTest.setEnabled(true);
                    btnTest.setText(R.string.setting_provider_page_test_connection);
                    new AlertDialog.Builder(getContext())
                            .setMessage(getString(R.string.setting_provider_page_test_fail, message))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        });
    }

    private void doSave() {
        ApiConfig cfg = collectFromForm();
        if (TextUtils.isEmpty(cfg.getName())) {
            Toast.makeText(getContext(), R.string.config_name, Toast.LENGTH_SHORT).show();
            return;
        }
        host.onConfigSaved(cfg);
    }
}
