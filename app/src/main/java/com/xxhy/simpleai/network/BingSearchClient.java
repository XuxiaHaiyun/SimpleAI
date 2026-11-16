package com.xxhy.simpleai.network;

import android.os.Handler;
import android.os.Looper;

import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 内置 Bing 网络搜索工具。
 * 参考 rikkahub 的 BingSearchService 实现：
 * - 使用 www.bing.com（国际版，结果更稳定）
 * - 现代桌面浏览器 User-Agent
 * - 完整的请求头（Accept-Language、Referer、Cookie）
 * - 改进的 HTML 解析：匹配 <li class="b_algo"> → <h2><a> 标题 + .b_caption <p> 摘要
 *
 * 兼容 Android 4.x：使用 OkHttp 3.12.x + 正则解析（无需 Jsoup 依赖）。
 */
public class BingSearchClient {

    private static volatile BingSearchClient instance;
    private final OkHttpClient client;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 预编译正则，避免每次搜索重复编译（Pattern 线程安全） */
    private static final Pattern ALGO_PATTERN = Pattern.compile(
            "<li[^>]*class=\"b_algo\"[^>]*>(.*?)</li>", Pattern.DOTALL);
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<h2>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>\\s*</h2>",
            Pattern.DOTALL);
    private static final Pattern CAPTION_PATTERN = Pattern.compile(
            "class=\"b_caption\"[^>]*>.*?<p[^>]*>(.*?)</p>", Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "&amp;|&lt;|&gt;|&quot;|&#39;|&nbsp;");
    private static final int MAX_RESULTS = 8;

    private BingSearchClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static BingSearchClient get() {
        if (instance == null) {
            synchronized (BingSearchClient.class) {
                if (instance == null) {
                    instance = new BingSearchClient();
                }
            }
        }
        return instance;
    }

    public interface SearchCallback {
        void onSuccess(String formattedResults);
        void onError(String message);
    }

    /**
     * 执行搜索并格式化结果。
     * 使用 Bing 国际版（www.bing.com）HTML 解析，无需 API Key。
     */
    public void search(final String query, final SearchCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    String url = "https://www.bing.com/search?q=" + encoded;

                    // 构造 Accept-Language
                    Locale locale = Locale.getDefault();
                    String acceptLanguage = locale.getLanguage() + "-" + locale.getCountry()
                            + "," + locale.getLanguage();

                    Request request = new Request.Builder()
                            .url(url)
                            .header("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                                    + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                            .header("Accept",
                                    "text/html,application/xhtml+xml,application/xml;q=0.9,"
                                    + "image/webp,*/*;q=0.8")
                            .header("Accept-Language", acceptLanguage)
                            .header("Accept-Encoding", "gzip, deflate, sdch")
                            .header("Accept-Charset", "utf-8")
                            .header("Connection", "keep-alive")
                            .header("Referer", "https://www.bing.com/")
                            .header("Cookie", "SRCHHPGUSR=ULSR=1")
                            .build();

                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        notifyError(callback, "搜索失败: HTTP " + response.code());
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : "";
                    response.close();
                    String formatted = parseBingResults(body, query);
                    if (formatted == null || formatted.isEmpty()) {
                        formatted = "未找到相关搜索结果。";
                    }
                    notifySuccess(callback, formatted);
                } catch (Exception e) {
                    notifyError(callback, "搜索异常: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * 解析 Bing 搜索结果 HTML。
     * 匹配 <li class="b_algo"> 块中的 <h2><a href="...">标题</a></h2>
     * 和 <div class="b_caption"><p>摘要</p></div>
     * 使用正则而非 Jsoup，兼容 Android 4.x 且无额外依赖。
     */
    private String parseBingResults(String html, String query) {
        if (html == null || html.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("以下是关于「").append(query).append("」的网络搜索结果：\n\n");

        Matcher algoMatcher = ALGO_PATTERN.matcher(html);
        int count = 0;

        while (algoMatcher.find() && count < MAX_RESULTS) {
            String block = algoMatcher.group(1);

            // 提取标题和链接
            Matcher titleMatcher = TITLE_PATTERN.matcher(block);
            if (!titleMatcher.find()) continue;

            String link = titleMatcher.group(1);
            String title = stripTags(titleMatcher.group(2)).trim();
            if (title.isEmpty()) continue;

            // 提取摘要
            String snippet = "";
            Matcher captionMatcher = CAPTION_PATTERN.matcher(block);
            if (captionMatcher.find()) {
                snippet = stripTags(captionMatcher.group(1)).trim();
            }

            count++;
            sb.append(count).append(". ").append(title).append("\n");
            if (!snippet.isEmpty()) {
                sb.append("   ").append(snippet).append("\n");
            }
            if (link != null && !link.isEmpty()) {
                sb.append("   来源: ").append(link).append("\n");
            }
            sb.append("\n");
        }
        return count > 0 ? sb.toString() : null;
    }

    private String stripTags(String s) {
        if (s == null) return "";
        // 使用预编译 Pattern 替换标签
        s = TAG_PATTERN.matcher(s).replaceAll("");
        // 使用 Matcher.appendReplacement 一次性替换所有 HTML 实体
        Matcher m = ENTITY_PATTERN.matcher(s);
        StringBuffer out = new StringBuffer(s.length());
        while (m.find()) {
            String e = m.group();
            String repl;
            switch (e) {
                case "&amp;":  repl = "&";  break;
                case "&lt;":   repl = "<";  break;
                case "&gt;":   repl = ">";  break;
                case "&quot;": repl = "\""; break;
                case "&#39;":  repl = "'";  break;
                case "&nbsp;": repl = " ";  break;
                default:       repl = e;    break;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(repl));
        }
        m.appendTail(out);
        return out.toString();
    }

    private void notifySuccess(final SearchCallback cb, final String result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onSuccess(result);
            }
        });
    }

    private void notifyError(final SearchCallback cb, final String msg) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg);
            }
        });
    }
}
