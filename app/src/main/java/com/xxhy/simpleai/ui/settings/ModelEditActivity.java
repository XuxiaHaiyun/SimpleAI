package com.xxhy.simpleai.ui.settings;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.ModelConfigDao;
import com.xxhy.simpleai.data.model.ModelConfig;
import com.xxhy.simpleai.registry.ModelRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型详细配置页。顶部 Tab 切换「基本设置」与「高级设置」。
 * 基本设置：模型 ID、显示名称、类型、输入/输出模态、能力。
 * 高级设置：自定义 Header、自定义 Body、提供商重写。
 */
public class ModelEditActivity extends AppCompatActivity {

    public static final String EXTRA_MODEL_ID = "model_id";
    public static final String EXTRA_API_CONFIG_ID = "api_config_id";

    private MaterialToolbar toolbar;
    private TabLayout tabLayout;
    private View scrollBasic;
    private View scrollAdvanced;
    private MaterialButton btnSave;

    private TextInputEditText editModelId;
    private TextInputEditText editDisplayName;
    private MaterialButtonToggleGroup toggleType;
    private MaterialButtonToggleGroup toggleInput;
    private MaterialButtonToggleGroup toggleOutput;
    private MaterialButtonToggleGroup toggleAbilities;

    private LinearLayout headersContainer;
    private LinearLayout bodiesContainer;
    private MaterialButton btnAddHeader;
    private MaterialButton btnAddBody;
    private TextInputEditText editProviderOverwrite;

    private ModelConfigDao dao;
    private ModelConfig model;
    private boolean isNew;
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_edit);

        dao = new ModelConfigDao(this);

        toolbar = findViewById(R.id.toolbar);
        tabLayout = findViewById(R.id.tab_layout);
        scrollBasic = findViewById(R.id.scroll_basic);
        scrollAdvanced = findViewById(R.id.scroll_advanced);
        btnSave = findViewById(R.id.btn_save);

        editModelId = findViewById(R.id.edit_model_id);
        editDisplayName = findViewById(R.id.edit_model_display_name);
        toggleType = findViewById(R.id.toggle_model_type);
        toggleInput = findViewById(R.id.toggle_input_modalities);
        toggleOutput = findViewById(R.id.toggle_output_modalities);
        toggleAbilities = findViewById(R.id.toggle_abilities);

        headersContainer = findViewById(R.id.headers_container);
        bodiesContainer = findViewById(R.id.bodies_container);
        btnAddHeader = findViewById(R.id.btn_add_header);
        btnAddBody = findViewById(R.id.btn_add_body);
        editProviderOverwrite = findViewById(R.id.edit_provider_overwrite);

        findViewById(R.id.btn_auto_detect).setOnClickListener(v -> autoDetectAbilities());

        toolbar.setNavigationOnClickListener(v -> finish());

        long modelId = getIntent().getLongExtra(EXTRA_MODEL_ID, -1);
        long apiConfigId = getIntent().getLongExtra(EXTRA_API_CONFIG_ID, -1);
        if (modelId > 0) {
            model = dao.queryById(modelId);
            if (model == null) {
                model = new ModelConfig();
                model.setId(modelId);
                model.setApiConfigId(apiConfigId);
                isNew = true;
            } else {
                isNew = false;
            }
            toolbar.setTitle(isNew ? R.string.add_model : R.string.edit_config);
        } else {
            model = new ModelConfig();
            model.setApiConfigId(apiConfigId);
            isNew = true;
            toolbar.setTitle(R.string.add_model);
        }

        fillForm();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                boolean advanced = tab.getPosition() == 1;
                scrollBasic.setVisibility(advanced ? View.GONE : View.VISIBLE);
                scrollAdvanced.setVisibility(advanced ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        btnAddHeader.setOnClickListener(v -> addHeaderRow("", ""));
        btnAddBody.setOnClickListener(v -> addBodyRow("", ""));
        btnSave.setOnClickListener(v -> save());
    }

    private void fillForm() {
        editModelId.setText(model.getModelId());
        editDisplayName.setText(model.getDisplayName());
        selectType(model.getType());
        applyModalities(toggleInput, R.id.btn_input_text, R.id.btn_input_image,
                model.getInputModalities());
        applyModalities(toggleOutput, R.id.btn_output_text, R.id.btn_output_image,
                model.getOutputModalities());
        applyAbilities(model.getAbilities());
        editProviderOverwrite.setText(model.getProviderOverwrite());

        // 加载自定义 Header
        List<String[]> headers = parsePairs(model.getCustomHeaders());
        for (String[] h : headers) {
            addHeaderRow(h[0], h[1]);
        }
        // 加载自定义 Body
        List<String[]> bodies = parsePairs(model.getCustomBodies());
        for (String[] b : bodies) {
            addBodyRow(b[0], b[1]);
        }
    }

    private void selectType(String type) {
        int id = R.id.btn_type_chat;
        if (ModelConfig.TYPE_IMAGE.equals(type)) {
            id = R.id.btn_type_image;
        } else if (ModelConfig.TYPE_EMBEDDING.equals(type)) {
            id = R.id.btn_type_embedding;
        }
        toggleType.check(id);
    }

    private String getSelectedType() {
        int id = toggleType.getCheckedButtonId();
        if (id == R.id.btn_type_image) return ModelConfig.TYPE_IMAGE;
        if (id == R.id.btn_type_embedding) return ModelConfig.TYPE_EMBEDDING;
        return ModelConfig.TYPE_CHAT;
    }

    /** 多选模态按钮组：text / image */
    private void applyModalities(MaterialButtonToggleGroup group, int textId, int imageId, String csv) {
        boolean hasText = containsToken(csv, ModelConfig.MODALITY_TEXT);
        boolean hasImage = containsToken(csv, ModelConfig.MODALITY_IMAGE);
        // 先清空再勾选，确保自动识别能正确反映当前模型能力
        group.clearChecked();
        if (hasText) group.check(textId);
        if (hasImage) group.check(imageId);
    }

    private void applyAbilities(String csv) {
        toggleAbilities.clearChecked();
        if (containsToken(csv, ModelConfig.ABILITY_TOOL)) toggleAbilities.check(R.id.btn_ability_tool);
        if (containsToken(csv, ModelConfig.ABILITY_REASONING)) toggleAbilities.check(R.id.btn_ability_reasoning);
    }

    private String collectModalities(MaterialButtonToggleGroup group, int textId, int imageId) {
        List<String> parts = new ArrayList<>();
        if (group.getCheckedButtonIds().contains(textId)) parts.add(ModelConfig.MODALITY_TEXT);
        if (group.getCheckedButtonIds().contains(imageId)) parts.add(ModelConfig.MODALITY_IMAGE);
        return TextUtils.join(",", parts);
    }

    private String collectAbilities() {
        List<String> parts = new ArrayList<>();
        if (toggleAbilities.getCheckedButtonIds().contains(R.id.btn_ability_tool)) {
            parts.add(ModelConfig.ABILITY_TOOL);
        }
        if (toggleAbilities.getCheckedButtonIds().contains(R.id.btn_ability_reasoning)) {
            parts.add(ModelConfig.ABILITY_REASONING);
        }
        return TextUtils.join(",", parts);
    }

    private static boolean containsToken(String csv, String token) {
        if (csv == null || token == null) return false;
        for (String p : csv.split(",")) {
            if (token.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    /** 解析 JSON 数组为键值对列表 */
    private List<String[]> parsePairs(String json) {
        List<String[]> pairs = new ArrayList<>();
        if (TextUtils.isEmpty(json)) return pairs;
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    String k = el.getAsJsonObject().has("name")
                            ? el.getAsJsonObject().get("name").getAsString()
                            : (el.getAsJsonObject().has("key")
                                ? el.getAsJsonObject().get("key").getAsString() : "");
                    String v = el.getAsJsonObject().has("value")
                            ? el.getAsJsonObject().get("value").getAsString() : "";
                    pairs.add(new String[]{k, v});
                }
            }
        } catch (Exception ignored) {
        }
        return pairs;
    }

    private void addHeaderRow(String name, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_key_value_row, headersContainer, false);
        ((TextView) row.findViewById(R.id.text_label)).setText(R.string.header_name);
        ((EditText) row.findViewById(R.id.edit_key)).setText(name);
        ((EditText) row.findViewById(R.id.edit_value)).setText(value);
        row.findViewById(R.id.btn_remove).setOnClickListener(v -> headersContainer.removeView(row));
        headersContainer.addView(row);
    }

    private void addBodyRow(String key, String value) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_key_value_row, bodiesContainer, false);
        ((TextView) row.findViewById(R.id.text_label)).setText(R.string.body_key);
        ((EditText) row.findViewById(R.id.edit_key)).setText(key);
        ((EditText) row.findViewById(R.id.edit_value)).setText(value);
        row.findViewById(R.id.btn_remove).setOnClickListener(v -> bodiesContainer.removeView(row));
        bodiesContainer.addView(row);
    }

    /** 收集容器中的键值对为 JSON 数组字符串 */
    private String collectPairs(LinearLayout container, String keyField) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            EditText editKey = row.findViewById(R.id.edit_key);
            EditText editValue = row.findViewById(R.id.edit_value);
            String k = editKey.getText() == null ? "" : editKey.getText().toString().trim();
            String v = editValue.getText() == null ? "" : editValue.getText().toString().trim();
            if (k.isEmpty()) continue;
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty(keyField, k);
            obj.addProperty("value", v);
            arr.add(obj);
        }
        return arr.toString();
    }

    /** 根据当前输入的模型 ID 自动识别模态与能力（模仿 rikkahub ModelRegistry） */
    private void autoDetectAbilities() {
        String modelId = textOf(editModelId);
        if (TextUtils.isEmpty(modelId)) {
            editModelId.setError(getString(R.string.model_id));
            return;
        }
        ModelConfig tmp = new ModelConfig();
        tmp.setModelId(modelId);
        ModelRegistry.applyTo(tmp);
        // 应用到表单
        applyModalities(toggleInput, R.id.btn_input_text, R.id.btn_input_image,
                tmp.getInputModalities());
        applyModalities(toggleOutput, R.id.btn_output_text, R.id.btn_output_image,
                tmp.getOutputModalities());
        applyAbilities(tmp.getAbilities());
        Toast.makeText(this, R.string.model_auto_detect_done, Toast.LENGTH_SHORT).show();
    }

    private void save() {
        String modelId = textOf(editModelId);
        if (TextUtils.isEmpty(modelId)) {
            editModelId.setError(getString(R.string.model_id));
            return;
        }
        model.setModelId(modelId);
        model.setDisplayName(textOf(editDisplayName));
        model.setType(getSelectedType());
        model.setInputModalities(collectModalities(toggleInput, R.id.btn_input_text, R.id.btn_input_image));
        model.setOutputModalities(collectModalities(toggleOutput, R.id.btn_output_text, R.id.btn_output_image));
        model.setAbilities(collectAbilities());
        model.setCustomHeaders(collectPairs(headersContainer, "name"));
        model.setCustomBodies(collectPairs(bodiesContainer, "key"));
        model.setProviderOverwrite(textOf(editProviderOverwrite));

        if (isNew) {
            dao.insert(model);
        } else {
            dao.update(model);
        }
        Toast.makeText(this, R.string.model_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
