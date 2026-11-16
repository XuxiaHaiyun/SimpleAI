package com.xxhy.simpleai.ui.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xxhy.simpleai.App;
import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.db.ApiConfigDao;
import com.xxhy.simpleai.data.db.MessageDao;
import com.xxhy.simpleai.data.db.ModelConfigDao;
import com.xxhy.simpleai.data.db.SessionDao;
import com.xxhy.simpleai.data.model.ApiConfig;
import com.xxhy.simpleai.data.model.Message;
import com.xxhy.simpleai.data.model.ModelConfig;
import com.xxhy.simpleai.data.model.Session;
import com.xxhy.simpleai.network.ApiClient;
import com.xxhy.simpleai.network.BingSearchClient;
import com.xxhy.simpleai.network.ChatStreamListener;
import com.xxhy.simpleai.network.ProviderAdapter;
import com.xxhy.simpleai.network.model.TokenUsage;
import com.xxhy.simpleai.util.ContextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

/**
 * 主聊天界面 Fragment。负责消息展示、流式发送、停止/重试。
 * 支持文件/图片上传、拍照、联网搜索、思考深度设置。
 */
public class ChatFragment extends Fragment implements MessageAdapter.OnMessageActionListener {

    public interface Callback {
        void onToolbarTitleChanged(String title);
    }

    private static final String ARG_SESSION_ID = "session_id";

    private static final int REQ_PICK_PHOTO = 1001;
    private static final int REQ_PICK_FILE = 1002;
    private static final int REQ_TAKE_PHOTO = 1003;

    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    private EditText inputText;
    private ImageButton btnSend;
    private TextView emptyView;

    // 工具栏按钮
    private ImageButton btnAttachPhoto;
    private ImageButton btnAttachCamera;
    private ImageButton btnAttachFile;
    private ImageButton btnWebSearch;
    private ImageButton btnThinkingDepth;
    private TextView textThinkingDepth;
    private View toolbarButtonsContainer;
    private ImageButton btnToggleToolbar;
    private boolean toolbarExpanded = true;
    private View bottomBar;
    private boolean wideRender = false;

    // 附件预览
    private LinearLayout attachmentPreviewContainer;
    private View attachmentPreviewScroll;

    private SessionDao sessionDao;
    private MessageDao messageDao;
    private ApiConfigDao apiConfigDao;
    private ModelConfigDao modelConfigDao;

    private Session currentSession;
    private ApiConfig currentConfig;
    private ModelConfig currentModelConfig;
    private Call currentCall;
    private boolean isGenerating = false;

    // 待加载的会话 ID（视图未就绪时暂存）
    private long pendingSessionId = -1L;

    private Callback callback;

    // 附件管理
    private AttachmentManager attachmentManager;
    private Uri cameraPhotoUri;

    // 联网搜索开关
    private boolean webSearchEnabled = false;

    // 思考深度
    private ProviderAdapter.ReasoningLevel thinkingDepth = ProviderAdapter.ReasoningLevel.AUTO;

    public static ChatFragment newInstance(long sessionId) {
        ChatFragment f = new ChatFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_SESSION_ID, sessionId);
        f.setArguments(args);
        return f;
    }

    public static ChatFragment newInstance() {
        return new ChatFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        ensureDaos(context);
        attachmentManager = new AttachmentManager(context);
        if (context instanceof Callback) {
            callback = (Callback) context;
        }
    }

    /** 延迟初始化 DAO（防止宿主在 onAttach 前调用 loadSession 等方法） */
    private void ensureDaos(Context context) {
        if (context == null) {
            context = getActivity();
        }
        if (context == null && App.get() != null) {
            context = App.get().getApplicationContext();
        }
        if (context == null) {
            return;
        }
        if (sessionDao == null) sessionDao = new SessionDao(context);
        if (messageDao == null) messageDao = new MessageDao(context);
        if (apiConfigDao == null) apiConfigDao = new ApiConfigDao(context);
        if (modelConfigDao == null) modelConfigDao = new ModelConfigDao(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        recyclerView = view.findViewById(R.id.recycler_messages);
        inputText = view.findViewById(R.id.input_text);
        btnSend = view.findViewById(R.id.btn_send);
        emptyView = view.findViewById(R.id.empty_view);

        btnAttachPhoto = view.findViewById(R.id.btn_attach_photo);
        btnAttachCamera = view.findViewById(R.id.btn_attach_camera);
        btnAttachFile = view.findViewById(R.id.btn_attach_file);
        btnWebSearch = view.findViewById(R.id.btn_web_search);
        btnThinkingDepth = view.findViewById(R.id.btn_thinking_depth);
        textThinkingDepth = view.findViewById(R.id.text_thinking_depth);
        toolbarButtonsContainer = view.findViewById(R.id.toolbar_buttons);
        btnToggleToolbar = view.findViewById(R.id.btn_toggle_toolbar);
        bottomBar = view.findViewById(R.id.bottom_bar);

        attachmentPreviewContainer = view.findViewById(R.id.attachment_preview_container);
        attachmentPreviewScroll = view.findViewById(R.id.attachment_preview);

        adapter = new MessageAdapter(getContext(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        // 关闭变更动画，避免流式输出时整行闪烁
        recyclerView.setItemAnimator(null);
        // 监听滚动：用户上滑时停止自动滚动，滑回底部时恢复
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (isGenerating) {
                    userAtBottom = isAtBottom();
                }
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isGenerating) {
                    stopGeneration();
                } else {
                    sendMessage();
                }
            }
        });

        setupToolbarButtons();

        // 加载会话
        Bundle args = getArguments();
        if (args != null && args.containsKey(ARG_SESSION_ID)) {
            loadSession(args.getLong(ARG_SESSION_ID));
        } else if (pendingSessionId > 0) {
            // 宿主在视图就绪前调用过 loadSession
            loadSession(pendingSessionId);
        } else {
            updateEmptyView();
            loadActiveConfig();
        }

        updateThinkingDepthLabel();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从设置返回后，若配置为空或已被删除，重新加载活动配置
        if (currentConfig == null || apiConfigDao.queryById(currentConfig.getId()) == null) {
            currentConfig = apiConfigDao.queryActive();
            if (currentConfig != null) {
                ensureModelsForConfig(currentConfig);
                if (currentSession != null) {
                    initModelForConfig(currentConfig);
                }
            }
            updateToolbarTitle();
        }
        // 兼容旧配置：若活动配置仅有 models CSV 字符串而无模型表记录，自动迁移
        ensureModelsForActiveConfig();
        // 重新加载模型配置（用户可能在设置中修改了模型配置）
        loadCurrentModelConfig();
        // 若 currentModelConfig 仍为空（冷启动未初始化），尝试初始化
        if (currentModelConfig == null && currentConfig != null) {
            initModelForConfig(currentConfig);
        }
        updateThinkingDepthLabel();
    }

    /**
     * 兼容旧版配置：当活动 ApiConfig 仅有 models CSV 字符串而无 ModelConfig 记录时，
     * 自动根据 CSV 创建模型记录（并通过 ModelRegistry 推断能力），避免无法聊天。
     */
    private void ensureModelsForActiveConfig() {
        if (currentConfig == null) return;
        if (TextUtils.isEmpty(currentConfig.getModels())) return;
        modelConfigDao.ensureModelsFromCsv(currentConfig.getId(), currentConfig.getModels());
    }

    private void setupToolbarButtons() {
        btnToggleToolbar.setOnClickListener(v -> {
            toolbarExpanded = !toolbarExpanded;
            toolbarButtonsContainer.setVisibility(toolbarExpanded ? View.VISIBLE : View.GONE);
            btnToggleToolbar.setImageResource(
                    toolbarExpanded ? android.R.drawable.arrow_down_float
                            : android.R.drawable.arrow_up_float);
        });

        btnAttachPhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.attach_photo)),
                    REQ_PICK_PHOTO);
        });

        btnAttachCamera.setOnClickListener(v -> takePhoto());

        btnAttachFile.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, getString(R.string.attach_file)),
                    REQ_PICK_FILE);
        });

        btnWebSearch.setOnClickListener(v -> {
            webSearchEnabled = !webSearchEnabled;
            btnWebSearch.setAlpha(webSearchEnabled ? 1.0f : 0.5f);
            Toast.makeText(getContext(),
                    webSearchEnabled ? R.string.web_search : R.string.web_search,
                    Toast.LENGTH_SHORT).show();
        });
        btnWebSearch.setAlpha(0.5f);

        btnThinkingDepth.setOnClickListener(v -> showThinkingDepthDialog());
    }

    /** 供 Activity 调用，控制输入栏可见性 */
    public void setInputBarVisible(boolean visible) {
        if (bottomBar != null) {
            bottomBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 供 Activity 调用，切换宽屏渲染模式 */
    public void setWideRender(boolean wide) {
        this.wideRender = wide;
        if (adapter != null) {
            adapter.setWideRender(wide);
            adapter.notifyDataSetChanged();
        }
    }

    /** 拍照：通过 FileProvider 创建临时文件 */
    private void takePhoto() {
        if (currentModelConfig == null || !currentModelConfig.isVisionSupported()) {
            Toast.makeText(getContext(), R.string.vision_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File photoFile = new File(getContext().getCacheDir(),
                    "photo_" + System.currentTimeMillis() + ".jpg");
            cameraPhotoUri = FileProvider.getUriForFile(getContext(),
                    getContext().getPackageName() + ".fileprovider", photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            startActivityForResult(intent, REQ_TAKE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法启动相机: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) return;

        if (requestCode == REQ_PICK_PHOTO) {
            if (data != null && data.getData() != null) {
                if (attachmentManager.addImage(data.getData())) {
                    refreshAttachmentPreview();
                } else {
                    Toast.makeText(getContext(), R.string.attachment_too_large,
                            Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQ_TAKE_PHOTO) {
            if (cameraPhotoUri != null) {
                if (attachmentManager.addImage(cameraPhotoUri)) {
                    refreshAttachmentPreview();
                } else {
                    Toast.makeText(getContext(), R.string.attachment_too_large,
                            Toast.LENGTH_SHORT).show();
                }
                cameraPhotoUri = null;
            }
        } else if (requestCode == REQ_PICK_FILE) {
            if (data != null && data.getData() != null) {
                if (attachmentManager.addFile(data.getData())) {
                    refreshAttachmentPreview();
                }
            }
        }
    }

    /** 刷新附件预览区 */
    private void refreshAttachmentPreview() {
        attachmentPreviewContainer.removeAllViews();
        List<AttachmentManager.Attachment> list = attachmentManager.getAll();
        if (list.isEmpty()) {
            attachmentPreviewScroll.setVisibility(View.GONE);
            return;
        }
        attachmentPreviewScroll.setVisibility(View.VISIBLE);
        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            AttachmentManager.Attachment a = list.get(i);
            View item = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_attachment_preview, attachmentPreviewContainer, false);
            TextView text = item.findViewById(R.id.text_name);
            text.setText(a.displayName);
            View btnRemove = item.findViewById(R.id.btn_remove);
            btnRemove.setOnClickListener(v -> {
                attachmentManager.remove(index);
                refreshAttachmentPreview();
            });
            attachmentPreviewContainer.addView(item);
        }
    }

    /** 思考深度选择对话框 */
    private void showThinkingDepthDialog() {
        if (currentModelConfig == null || !currentModelConfig.isReasoningSupported()) {
            Toast.makeText(getContext(), R.string.setting_provider_page_reasoning,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = {
                getString(R.string.thinking_depth_off),
                getString(R.string.thinking_depth_auto),
                getString(R.string.thinking_depth_low),
                getString(R.string.thinking_depth_medium),
                getString(R.string.thinking_depth_high)
        };
        ProviderAdapter.ReasoningLevel[] levels = {
                ProviderAdapter.ReasoningLevel.OFF,
                ProviderAdapter.ReasoningLevel.AUTO,
                ProviderAdapter.ReasoningLevel.LOW,
                ProviderAdapter.ReasoningLevel.MEDIUM,
                ProviderAdapter.ReasoningLevel.HIGH
        };
        int checked = 1; // 默认 AUTO
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] == thinkingDepth) {
                checked = i;
                break;
            }
        }
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.thinking_depth)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    thinkingDepth = levels[which];
                    updateThinkingDepthLabel();
                    dialog.dismiss();
                })
                .show();
    }

    private void updateThinkingDepthLabel() {
        if (currentModelConfig != null && currentModelConfig.isReasoningSupported()
                && thinkingDepth != ProviderAdapter.ReasoningLevel.AUTO) {
            textThinkingDepth.setVisibility(View.VISIBLE);
            int resId;
            switch (thinkingDepth) {
                case OFF: resId = R.string.thinking_depth_off; break;
                case LOW: resId = R.string.thinking_depth_low; break;
                case MEDIUM: resId = R.string.thinking_depth_medium; break;
                case HIGH: resId = R.string.thinking_depth_high; break;
                default: resId = R.string.thinking_depth_auto; break;
            }
            textThinkingDepth.setText(resId);
        } else {
            textThinkingDepth.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopGeneration();
    }

    // ===== 会话管理 =====

    public void loadSession(long sessionId) {
        ensureDaos(getActivity());
        // 视图未就绪时暂存，待 onViewCreated 加载
        if (adapter == null) {
            pendingSessionId = sessionId;
            return;
        }
        pendingSessionId = -1L;
        currentSession = sessionDao.queryById(sessionId);
        if (currentSession == null) {
            updateEmptyView();
            return;
        }
        List<Message> msgs = messageDao.queryBySession(sessionId);
        adapter.setMessages(msgs);
        updateEmptyView();
        scrollToBottom();

        if (currentSession.getApiConfigId() > 0) {
            currentConfig = apiConfigDao.queryById(currentSession.getApiConfigId());
        }
        if (currentConfig == null) {
            currentConfig = apiConfigDao.queryActive();
        }
        // 若会话模型为空或配置不匹配，使用记忆模型或第一个模型初始化
        if (currentConfig != null && (currentSession.getModel() == null
                || currentSession.getModel().isEmpty()
                || currentSession.getApiConfigId() != currentConfig.getId())) {
            ensureModelsForConfig(currentConfig);
            String model = App.get().getSettingsManager().getLastModel(currentConfig.getId());
            if (model != null) {
                // 验证模型是否存在
                ModelConfig mc = modelConfigDao.queryByModelId(currentConfig.getId(), model);
                if (mc == null) {
                    List<ModelConfig> cfgs = modelConfigDao.queryByConfig(currentConfig.getId());
                    model = cfgs.isEmpty() ? null : cfgs.get(0).getModelId();
                }
            }
            if (model == null) {
                List<ModelConfig> cfgs = modelConfigDao.queryByConfig(currentConfig.getId());
                model = cfgs.isEmpty() ? null : cfgs.get(0).getModelId();
            }
            if (model != null) {
                currentSession.setModel(model);
                currentSession.setApiConfigId(currentConfig.getId());
                sessionDao.updateModel(currentSession.getId(), currentConfig.getId(), model);
            }
        }
        loadCurrentModelConfig();
        updateToolbarTitle();
        updateThinkingDepthLabel();
    }

    /** 根据当前配置与会话模型加载模型配置（用于自定义 Header/Body） */
    private void loadCurrentModelConfig() {
        currentModelConfig = null;
        if (currentConfig == null || currentSession == null) return;
        String model = currentSession.getModel();
        if (model == null || model.isEmpty()) return;
        currentModelConfig = modelConfigDao.queryByModelId(currentConfig.getId(), model);
    }

    public void newSession() {
        stopGeneration();
        currentSession = null;
        adapter.setMessages(new ArrayList<Message>());
        updateEmptyView();
        loadActiveConfig();
        updateToolbarTitle();
        inputText.setText("");
        attachmentManager.clear();
        refreshAttachmentPreview();
    }

    private void loadActiveConfig() {
        currentConfig = apiConfigDao.queryActive();
        // 兼容旧配置：若活动配置仅有 models CSV 字符串而无模型表记录，自动迁移
        ensureModelsForActiveConfig();
        // 初始化模型配置（冷启动时确保 currentModelConfig 不为 null，使推理深度按钮可用）
        if (currentConfig != null) {
            initModelForConfig(currentConfig);
        }
    }

    /**
     * 为指定配置初始化模型：优先使用记忆的模型，其次使用会话关联的模型，最后使用第一个模型。
     * 同时更新 currentModelConfig 以确保推理深度等功能可用。
     */
    private void initModelForConfig(ApiConfig config) {
        if (config == null) return;

        // 确定要使用的模型
        String model = null;

        // 1. 优先使用会话关联的模型（仅当会话属于同一提供商时）
        if (currentSession != null && currentSession.getApiConfigId() == config.getId()
                && currentSession.getModel() != null && !currentSession.getModel().isEmpty()) {
            model = currentSession.getModel();
        }

        // 2. 其次使用上次记忆的模型
        if (model == null) {
            model = App.get().getSettingsManager().getLastModel(config.getId());
        }

        // 3. 验证模型是否仍存在（可能已被删除或更新）
        if (model != null) {
            ModelConfig mc = modelConfigDao.queryByModelId(config.getId(), model);
            if (mc == null) {
                model = null; // 模型不存在，回退到第一个
            }
        }

        // 4. 回退到第一个模型
        if (model == null) {
            List<ModelConfig> cfgs = modelConfigDao.queryByConfig(config.getId());
            if (!cfgs.isEmpty()) {
                model = cfgs.get(0).getModelId();
            }
        }

        // 更新会话和模型配置
        if (model != null && !model.isEmpty()) {
            if (currentSession != null) {
                if (currentSession.getModel() == null || currentSession.getModel().isEmpty()
                        || currentSession.getApiConfigId() != config.getId()) {
                    currentSession.setModel(model);
                    currentSession.setApiConfigId(config.getId());
                    sessionDao.updateModel(currentSession.getId(), config.getId(), model);
                }
            }
            currentModelConfig = modelConfigDao.queryByModelId(config.getId(), model);
        }
    }

    private void ensureSession() {
        if (currentSession == null) {
            // 兼容旧配置：确保模型表已存在记录，避免默认模型为空导致无法聊天
            ensureModelsForActiveConfig();
            currentSession = new Session();
            currentSession.setTitle(getString(R.string.new_chat));
            currentSession.setApiConfigId(currentConfig != null ? currentConfig.getId() : 0);
            // 使用 initModelForConfig 选择合适的默认模型（记忆模型 > 第一个模型）
            if (currentConfig != null) {
                List<ModelConfig> cfgs = modelConfigDao.queryByConfig(currentConfig.getId());
                if (!cfgs.isEmpty()) {
                    String model = App.get().getSettingsManager().getLastModel(currentConfig.getId());
                    if (model == null) {
                        model = cfgs.get(0).getModelId();
                    } else {
                        // 验证记忆的模型是否仍存在
                        boolean found = false;
                        for (ModelConfig c : cfgs) {
                            if (c.getModelId().equals(model)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            model = cfgs.get(0).getModelId();
                        }
                    }
                    currentSession.setModel(model);
                }
            }
            sessionDao.insert(currentSession);
            loadCurrentModelConfig();
        }
    }

    // ===== 发送消息 =====

    private void sendMessage() {
        String text = inputText.getText().toString().trim();
        boolean hasAttachment = !attachmentManager.isEmpty();

        if (TextUtils.isEmpty(text) && !hasAttachment) {
            Toast.makeText(getContext(), R.string.empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ApiClient.isNetworkAvailable(getContext())) {
            Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
            return;
        }
        // 若配置为空，尝试加载活动配置
        if (currentConfig == null) {
            currentConfig = apiConfigDao.queryActive();
        }
        if (currentConfig == null) {
            Toast.makeText(getContext(), R.string.no_active_config, Toast.LENGTH_LONG).show();
            return;
        }

        // 视觉能力校验（基于当前模型配置）
        if (attachmentManager.hasImage() && (currentModelConfig == null || !currentModelConfig.isVisionSupported())) {
            Toast.makeText(getContext(), R.string.vision_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }

        ensureSession();

        // 收集附件信息
        final List<String> imageDataUrls = attachmentManager.getImageDataUrls();
        StringBuilder attachmentsStore = new StringBuilder();
        for (AttachmentManager.Attachment a : attachmentManager.getAll()) {
            if (attachmentsStore.length() > 0) attachmentsStore.append("|");
            attachmentsStore.append(a.uri.toString());
        }

        // 保存用户消息
        Message userMsg = new Message(currentSession.getId(), Message.ROLE_USER, text);
        userMsg.setAttachments(attachmentsStore.toString());
        messageDao.insert(userMsg);
        adapter.addMessage(userMsg);

        // 自动生成标题
        if (getString(R.string.new_chat).equals(currentSession.getTitle())) {
            String title = text.length() > 30 ? text.substring(0, 30) + "…" : text;
            sessionDao.updateTitle(currentSession.getId(), title);
            currentSession.setTitle(title);
        }

        inputText.setText("");
        attachmentManager.clear();
        refreshAttachmentPreview();
        updateEmptyView();
        scrollToBottom();

        // 创建 AI 占位消息
        final Message aiMsg = new Message(currentSession.getId(), Message.ROLE_ASSISTANT, "");
        aiMsg.setStreaming(true);
        messageDao.insert(aiMsg);
        adapter.addMessage(aiMsg);
        scrollToBottom();

        startGenerating();

        // 构建上下文
        int contextLen = currentConfig.getContextLength() > 0
                ? currentConfig.getContextLength() : 20;
        List<Message> context = messageDao.queryRecent(currentSession.getId(), contextLen);
        context = ContextUtils.limitContext(context, contextLen);

        // 推理级别：若当前模型非推理模型，强制 OFF
        final ProviderAdapter.ReasoningLevel effectiveLevel =
                (currentModelConfig != null && currentModelConfig.isReasoningSupported()) ? thinkingDepth
                        : ProviderAdapter.ReasoningLevel.OFF;

        // 联网搜索：通过 tool_call 让模型主动搜索，不再预搜索
        doChat(context, effectiveLevel, imageDataUrls, webSearchEnabled, aiMsg);
    }

    /** 发起流式对话 */
    private void doChat(final List<Message> context,
                        final ProviderAdapter.ReasoningLevel level,
                        final List<String> imageDataUrls,
                        final boolean webSearchEnabled, final Message aiMsg) {
        // 使用 StringBuilder 缓冲流式增量，避免 O(n²) 字符串拼接
        final StringBuilder contentBuf = new StringBuilder();
        final StringBuilder reasoningBuf = new StringBuilder();
        currentCall = ApiClient.get().streamChat(currentConfig, currentModelConfig, currentSession.getModel(),
                context, level, imageDataUrls, webSearchEnabled, new ChatStreamListener() {
            @Override
            public void onStart() {
            }

            @Override
            public void onDelta(String delta) {
                contentBuf.append(delta);
                aiMsg.setContent(contentBuf.toString());
                adapter.updateStreamingMessage(aiMsg);
                scrollToBottomIfAtBottom();
            }

            @Override
            public void onReasoning(String delta) {
                reasoningBuf.append(delta);
                aiMsg.setReasoning(reasoningBuf.toString());
                adapter.updateStreamingMessage(aiMsg);
                scrollToBottomIfAtBottom();
            }

            @Override
            public void onComplete(String fullContent, TokenUsage usage, String finishReason) {
                aiMsg.setContent(fullContent);
                aiMsg.setStreaming(false);
                if (usage != null) {
                    aiMsg.setUsageJson(usage.toString());
                }
                if (finishReason != null) {
                    aiMsg.setFinishReason(finishReason);
                }
                if (currentSession.getModel() != null) {
                    aiMsg.setModel(currentSession.getModel());
                }
                messageDao.update(aiMsg);
                adapter.updateStreamingMessage(aiMsg);
                sessionDao.touch(currentSession.getId());
                stopGenerating();
            }

            @Override
            public void onError(String message) {
                contentBuf.append("\n\n⚠️ ").append(message);
                aiMsg.setContent(contentBuf.toString());
                aiMsg.setStreaming(false);
                messageDao.update(aiMsg);
                adapter.updateStreamingMessage(aiMsg);
                stopGenerating();
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void stopGeneration() {
        if (currentCall != null && !currentCall.isCanceled()) {
            currentCall.cancel();
        }
        stopGenerating();
    }

    private void startGenerating() {
        isGenerating = true;
        userAtBottom = true;
        btnSend.setImageResource(R.drawable.ic_stop);
    }

    private void stopGenerating() {
        isGenerating = false;
        btnSend.setImageResource(R.drawable.ic_send);
        currentCall = null;
    }

    // ===== 重试 =====

    @Override
    public void onRetry(Message message) {
        if (isGenerating) {
            Toast.makeText(getContext(), R.string.generating, Toast.LENGTH_SHORT).show();
            return;
        }
        messageDao.delete(message.getId());
        adapter.removeMessage(message);

        List<Message> msgs = adapter.getMessages();
        if (msgs.isEmpty()) {
            return;
        }
        Message lastUser = null;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i).isUser()) {
                lastUser = msgs.get(i);
                break;
            }
        }
        if (lastUser == null) {
            return;
        }

        final Message aiMsg = new Message(currentSession.getId(), Message.ROLE_ASSISTANT, "");
        aiMsg.setStreaming(true);
        messageDao.insert(aiMsg);
        adapter.addMessage(aiMsg);
        scrollToBottom();
        startGenerating();

        int contextLen = currentConfig.getContextLength() > 0
                ? currentConfig.getContextLength() : 20;
        List<Message> context = messageDao.queryRecent(currentSession.getId(), contextLen);
        context = ContextUtils.limitContext(context, contextLen);

        final ProviderAdapter.ReasoningLevel effectiveLevel =
                (currentModelConfig != null && currentModelConfig.isReasoningSupported()) ? thinkingDepth
                        : ProviderAdapter.ReasoningLevel.OFF;

        doChat(context, effectiveLevel, null, false, aiMsg);
    }

    // ===== 模型选择 =====

    public void selectModel() {
        // 优先使用当前激活的提供商，直接展示其模型
        ApiConfig active = currentConfig != null ? currentConfig : apiConfigDao.queryActive();
        if (active != null) {
            showModelSelectionForProvider(active);
            return;
        }

        // 没有激活的提供商，让用户选择一个
        final List<ApiConfig> providers = apiConfigDao.queryEnabled();
        if (providers.isEmpty()) {
            Toast.makeText(getContext(), R.string.no_active_config, Toast.LENGTH_LONG).show();
            return;
        }

        final String[] providerNames = new String[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            providerNames[i] = providers.get(i).getName();
        }

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.select_model)
                .setSingleChoiceItems(providerNames, 0, (providerDialog, providerWhich) -> {
                    providerDialog.dismiss();
                    final ApiConfig selectedProvider = providers.get(providerWhich);
                    // 选择后设为当前使用
                    apiConfigDao.setActive(selectedProvider.getId());
                    currentConfig = selectedProvider;
                    showModelSelectionForProvider(selectedProvider);
                })
                .show();
    }

    /** 显示指定提供商下的模型选择弹窗 */
    private void showModelSelectionForProvider(final ApiConfig provider) {
        ensureModelsForConfig(provider);
        List<ModelConfig> modelConfigs = modelConfigDao.queryByConfig(provider.getId());
        if (modelConfigs.isEmpty()) {
            Toast.makeText(getContext(), R.string.models_hint, Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] displayNames = new String[modelConfigs.size()];
        final String[] modelIds = new String[modelConfigs.size()];
        for (int i = 0; i < modelConfigs.size(); i++) {
            displayNames[i] = modelConfigs.get(i).getDisplayLabel();
            modelIds[i] = modelConfigs.get(i).getModelId();
        }

        // 确定默认选中项：优先使用会话中的模型 → 其次使用上次记忆的模型 → 最后第一个
        int checked = 0;
        String currentModel = currentSession != null ? currentSession.getModel() : "";
        if (currentModel != null && !currentModel.isEmpty()
                && currentConfig != null && currentConfig.getId() == provider.getId()) {
            // 同一提供商：优先使用当前会话的模型
            for (int i = 0; i < modelIds.length; i++) {
                if (modelIds[i].equals(currentModel)) {
                    checked = i;
                    break;
                }
            }
        } else {
            // 切换了提供商：使用记忆的模型
            String lastModel = App.get().getSettingsManager().getLastModel(provider.getId());
            if (lastModel != null) {
                for (int i = 0; i < modelIds.length; i++) {
                    if (modelIds[i].equals(lastModel)) {
                        checked = i;
                        break;
                    }
                }
            }
        }

        new AlertDialog.Builder(getContext())
                .setTitle(provider.getName())
                .setSingleChoiceItems(displayNames, checked, (modelDialog, modelWhich) -> {
                    String model = modelIds[modelWhich];
                    // 切换提供商
                    if (currentConfig == null || currentConfig.getId() != provider.getId()) {
                        currentConfig = provider;
                        apiConfigDao.setActive(provider.getId());
                    }
                    // 更新会话模型
                    if (currentSession != null) {
                        sessionDao.updateModel(currentSession.getId(), provider.getId(), model);
                        currentSession.setModel(model);
                        currentSession.setApiConfigId(provider.getId());
                    }
                    // 记忆该提供商选择的模型
                    App.get().getSettingsManager().setLastModel(provider.getId(), model);
                    loadCurrentModelConfig();
                    updateToolbarTitle();
                    updateThinkingDepthLabel();
                    modelDialog.dismiss();
                })
                .show();
    }

    /** 确保指定配置的模型表记录已存在 */
    private void ensureModelsForConfig(ApiConfig config) {
        if (config == null) return;
        if (config.getModels() != null && !config.getModels().isEmpty()) {
            modelConfigDao.ensureModelsFromCsv(config.getId(), config.getModels());
        }
    }

    // ===== 工具方法 =====

    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    private final android.os.Handler scrollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean scrollPending = false;
    /** 用户是否在列表底部（用于流式输出时判断是否自动滚动） */
    private boolean userAtBottom = true;

    private boolean isAtBottom() {
        LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (lm == null) return true;
        int lastVisible = lm.findLastCompletelyVisibleItemPosition();
        int total = adapter.getItemCount();
        return total == 0 || lastVisible >= total - 2;
    }

    private void scrollToBottom() {
        if (scrollPending) return;
        scrollPending = true;
        scrollHandler.postDelayed(() -> {
            scrollPending = false;
            if (adapter.getItemCount() > 0) {
                // 直接跳转到底部，不用 smoothScroll 避免动画与布局变化冲突导致上下震荡
                recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        }, 80);
    }

    /** 流式输出期间的自动滚动：仅在用户处于底部时才跟随 */
    private void scrollToBottomIfAtBottom() {
        if (!userAtBottom) return;
        if (scrollPending) return;
        scrollPending = true;
        scrollHandler.postDelayed(() -> {
            scrollPending = false;
            if (adapter.getItemCount() > 0) {
                recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        }, 80);
    }

    private void updateToolbarTitle() {
        if (callback != null) {
            String title;
            // 优先显示模型名称
            if (currentModelConfig != null && currentModelConfig.getDisplayName() != null
                    && !currentModelConfig.getDisplayName().isEmpty()) {
                title = currentModelConfig.getDisplayName();
            } else if (currentConfig != null && currentSession != null
                    && currentSession.getModel() != null && !currentSession.getModel().isEmpty()) {
                title = currentSession.getModel();
            } else if (currentConfig != null) {
                title = currentConfig.getName();
            } else {
                title = getString(R.string.app_name);
            }
            callback.onToolbarTitleChanged(title);
        }
    }

    public boolean isGenerating() {
        return isGenerating;
    }

    public Session getCurrentSession() {
        return currentSession;
    }
}
