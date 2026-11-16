package com.xxhy.simpleai.ui.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.xxhy.simpleai.R;
import com.xxhy.simpleai.data.model.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.os.Handler;
import android.os.Looper;

/**
 * 聊天消息列表适配器。区分用户与 AI 消息气泡。
 *
 * AI 消息支持：
 * - 思考过程（reasoning）折叠/展开
 * - 流式输出实时刷新
 * - 完成后 Markdown 渲染
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_ASSISTANT = 2;

    public interface OnMessageActionListener {
        void onRetry(Message message);
    }

    private final List<Message> messages = new ArrayList<>();
    private final Context context;
    private final OnMessageActionListener listener;
    /** 记录展开 reasoning 的消息 id（持久状态，避免刷新时丢失） */
    private final Set<Long> expandedReasoningIds = new HashSet<>();
    /** 宽屏渲染模式：为表格和代码块提供更大宽度 */
    private boolean wideRender = false;

    /** 流式更新节流：避免每个 delta 都触发 notifyItemChanged 导致渲染抖动 */
    private final Handler streamHandler = new Handler(Looper.getMainLooper());
    private static final long STREAM_THROTTLE_MS = 80; // 约 12fps，平衡流畅度与性能
    private long lastStreamUpdateTime = 0;
    private boolean streamUpdatePending = false;

    public MessageAdapter(Context context, OnMessageActionListener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).getId();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserHolder(inflater.inflate(R.layout.item_message_user, parent, false));
        }
        return new AssistantHolder(inflater.inflate(R.layout.item_message_ai, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        if (holder instanceof UserHolder) {
            ((UserHolder) holder).bind(msg);
        } else if (holder instanceof AssistantHolder) {
            ((AssistantHolder) holder).bind(msg);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }
        // 流式增量更新：仅刷新文本与思考内容，避免重绑点击监听器导致闪烁
        Message msg = messages.get(position);
        if (holder instanceof AssistantHolder) {
            AssistantHolder ah = (AssistantHolder) holder;
            ah.updateStreamText(msg);
        } else if (holder instanceof UserHolder) {
            ((UserHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void setMessages(List<Message> list) {
        messages.clear();
        messages.addAll(list);
        notifyDataSetChanged();
    }

    public void addMessage(Message msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    /** 更新流式消息内容（追加增量后刷新，使用 payload 避免全量重绑） */
    public void updateStreamingMessage(Message msg) {
        int index = messages.indexOf(msg);
        if (index < 0) return;

        // 非流式消息（如完成/错误）立即刷新
        if (!msg.isStreaming()) {
            streamHandler.removeCallbacks(streamUpdateRunnable);
            streamUpdatePending = false;
            notifyItemChanged(index, "stream");
            return;
        }

        // 流式消息节流：限制刷新频率
        long now = System.currentTimeMillis();
        long elapsed = now - lastStreamUpdateTime;
        if (elapsed >= STREAM_THROTTLE_MS) {
            lastStreamUpdateTime = now;
            notifyItemChanged(index, "stream");
        } else if (!streamUpdatePending) {
            streamUpdatePending = true;
            streamHandler.postDelayed(streamUpdateRunnable, STREAM_THROTTLE_MS - elapsed);
        }
    }

    private final Runnable streamUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            streamUpdatePending = false;
            lastStreamUpdateTime = System.currentTimeMillis();
            // 刷新最后一条流式消息
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).isStreaming()) {
                    notifyItemChanged(i, "stream");
                    break;
                }
            }
        }
    };

    public Message getLastMessage() {
        if (messages.isEmpty()) {
            return null;
        }
        return messages.get(messages.size() - 1);
    }

    public void removeMessage(Message msg) {
        int index = messages.indexOf(msg);
        if (index >= 0) {
            messages.remove(index);
            notifyItemRemoved(index);
        }
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setWideRender(boolean wide) {
        this.wideRender = wide;
    }

    // ===== User ViewHolder =====

    class UserHolder extends RecyclerView.ViewHolder {
        TextView textContent;
        TextView textAttachments;

        UserHolder(@NonNull View itemView) {
            super(itemView);
            textContent = itemView.findViewById(R.id.text_content);
            textAttachments = itemView.findViewById(R.id.text_attachments);
        }

        void bind(Message msg) {
            String content = msg.getContent();
            if (content == null || content.isEmpty()) {
                content = "";
            }
            textContent.setText(content);

            // 显示附件标记
            if (msg.hasAttachments()) {
                String[] uris = msg.getAttachmentArray();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < uris.length; i++) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append("📎");
                }
                textAttachments.setText(sb.toString());
                textAttachments.setVisibility(View.VISIBLE);
            } else {
                textAttachments.setVisibility(View.GONE);
            }
        }
    }

    // ===== Assistant ViewHolder =====

    class AssistantHolder extends RecyclerView.ViewHolder {
        TextView textContent;
        HorizontalScrollView scrollContent;
        LinearLayout actionBar;
        ImageButton btnCopy;
        ImageButton btnRetry;
        LinearLayout reasoningContainer;
        TextView reasoningTitle;
        TextView reasoningToggle;
        TextView reasoningContent;

        /** 当前绑定的消息，供复用的监听器使用 */
        private Message boundMessage;

        AssistantHolder(@NonNull View itemView) {
            super(itemView);
            textContent = itemView.findViewById(R.id.text_content);
            scrollContent = itemView.findViewById(R.id.scroll_content);
            actionBar = itemView.findViewById(R.id.action_bar);
            btnCopy = itemView.findViewById(R.id.btn_copy);
            btnRetry = itemView.findViewById(R.id.btn_retry);
            reasoningContainer = itemView.findViewById(R.id.reasoning_container);
            reasoningTitle = itemView.findViewById(R.id.reasoning_title);
            reasoningToggle = itemView.findViewById(R.id.reasoning_toggle);
            reasoningContent = itemView.findViewById(R.id.reasoning_content);
            bindActionsOnce();
        }

        void bind(final Message msg) {
            boundMessage = msg;
            bindReasoning(msg);
            bindContent(msg);
        }

        /** 流式增量更新：仅刷新文本与思考内容，不重绑监听器 */
        void updateStreamText(Message msg) {
            boundMessage = msg;
            if (msg.isStreaming()) {
                // 流式期间：纯文本更新（避免 Markwon 重渲染导致高度跳变）
                String display = msg.getContent();
                if (TextUtils.isEmpty(display)) {
                    display = "...";
                }
                textContent.setText(display);
                actionBar.setVisibility(View.GONE);

                // 更新思考内容
                if (msg.hasReasoning()) {
                    reasoningContent.setText(msg.getReasoning());
                    if (reasoningContainer.getVisibility() != View.VISIBLE) {
                        reasoningContainer.setVisibility(View.VISIBLE);
                        reasoningContent.setVisibility(View.GONE);
                        reasoningToggle.setText(R.string.reasoning_expand);
                    }
                    reasoningTitle.setText(R.string.reasoning_thinking);
                }
            } else {
                // 流式完成：完整重绑（包括 reasoning 监听器和 Markdown 渲染）
                bind(msg);
            }
        }

        private void bindReasoning(final Message msg) {
            boolean hasReasoning = msg.hasReasoning();
            if (!hasReasoning) {
                reasoningContainer.setVisibility(View.GONE);
                return;
            }
            reasoningContainer.setVisibility(View.VISIBLE);

            // 流式输出时显示"思考中…"
            if (msg.isStreaming()) {
                reasoningTitle.setText(R.string.reasoning_thinking);
            } else {
                reasoningTitle.setText(R.string.reasoning_title);
            }

            // 折叠/展开状态
            final boolean expanded = expandedReasoningIds.contains(msg.getId());
            if (expanded) {
                MarkwonHelper.get(context).render(reasoningContent, msg.getReasoning());
            } else {
                reasoningContent.setText(msg.getReasoning());
            }
            reasoningContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
            reasoningToggle.setText(expanded ? R.string.reasoning_collapse
                    : R.string.reasoning_expand);
        }

        private void bindContent(Message msg) {
            String display = msg.getContent();
            if (TextUtils.isEmpty(display)) {
                display = msg.isStreaming() ? "..." : "";
            }
            if (msg.isStreaming()) {
                textContent.setText(display);
            } else {
                MarkwonHelper.get(context).render(textContent, display);
            }
            actionBar.setVisibility(msg.isStreaming() ? View.GONE : View.VISIBLE);

            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            if (wideRender) {
                // 宽屏模式：放开最大宽度限制，设置最小宽度让内容完整渲染
                textContent.setMaxWidth(Integer.MAX_VALUE);
                textContent.setMinWidth(screenWidth * 2);
            } else {
                // 正常模式：约束到屏幕宽度，文本正常换行，无横向滚动
                textContent.setMaxWidth(screenWidth);
                textContent.setMinWidth(0);
            }
        }

        /** 在 ViewHolder 创建时一次性绑定监听器，避免每次 bind 重复创建对象 */
        private void bindActionsOnce() {
            // 折叠/展开切换：通过 boundMessage 引用当前消息
            View.OnClickListener toggleListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Message msg = boundMessage;
                    if (msg == null) return;
                    if (expandedReasoningIds.contains(msg.getId())) {
                        expandedReasoningIds.remove(msg.getId());
                        reasoningContent.setVisibility(View.GONE);
                        reasoningToggle.setText(R.string.reasoning_expand);
                    } else {
                        expandedReasoningIds.add(msg.getId());
                        reasoningContent.setVisibility(View.VISIBLE);
                        reasoningToggle.setText(R.string.reasoning_collapse);
                    }
                }
            };
            reasoningTitle.setOnClickListener(toggleListener);
            reasoningToggle.setOnClickListener(toggleListener);

            btnCopy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Message msg = boundMessage;
                    if (msg == null) return;
                    ClipboardManager cm = (ClipboardManager) context
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("message", msg.getContent()));
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
                    }
                }
            });

            btnRetry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Message msg = boundMessage;
                    if (msg == null) return;
                    if (listener != null) {
                        listener.onRetry(msg);
                    }
                }
            });
        }
    }
}
