package com.xxhy.simpleai.ui.chat;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xxhy.simpleai.R;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.image.ImagesPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

/**
 * Markdown 渲染工具，基于 Markwon。
 * 支持代码块、列表、表格、删除线、链接、LaTeX 公式、图片。
 */
public class MarkwonHelper {

    private static volatile MarkwonHelper instance;

    private final Markwon markwon;

    private MarkwonHelper(Context context) {
        final Context appContext = context.getApplicationContext();
        final Resources res = appContext.getResources();
        final int codeBg = res.getColor(R.color.code_bg);
        final int codeText = res.getColor(R.color.code_text);

        markwon = Markwon.builder(appContext)
                .usePlugin(io.noties.markwon.core.CorePlugin.create())
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(TablePlugin.create(appContext))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(ImagesPlugin.create())
                .usePlugin(JLatexMathPlugin.create(14f, new JLatexMathPlugin.BuilderConfigure() {
                    @Override
                    public void configureBuilder(@NonNull JLatexMathPlugin.Builder builder) {
                        builder.inlinesEnabled(true);
                        builder.errorHandler(new JLatexMathPlugin.ErrorHandler() {
                            @Nullable
                            @Override
                            public android.graphics.drawable.Drawable handleError(
                                    @NonNull String latex, @NonNull Throwable error) {
                                return null;
                            }
                        });
                    }
                }))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.codeBackgroundColor(codeBg)
                                .codeTextColor(codeText)
                                .codeTextSize(13)
                                .codeTypeface(Typeface.MONOSPACE)
                                .blockMargin(24);
                    }

                    @Override
                    public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                        builder.linkResolver((view, link) -> {
                            try {
                                view.getContext().startActivity(
                                        new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                            } catch (Exception ignored) {
                            }
                        });
                    }
                })
                .build();
    }

    public static MarkwonHelper get(Context context) {
        if (instance == null) {
            synchronized (MarkwonHelper.class) {
                if (instance == null) {
                    instance = new MarkwonHelper(context);
                }
            }
        }
        return instance;
    }

    /** 渲染 Markdown 到 TextView */
    public void render(android.widget.TextView textView, String markdown) {
        if (TextUtils.isEmpty(markdown)) {
            textView.setText("");
            return;
        }
        // 确保链接可点击
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        markwon.setMarkdown(textView, markdown);
    }

    public Markwon getMarkwon() {
        return markwon;
    }
}
