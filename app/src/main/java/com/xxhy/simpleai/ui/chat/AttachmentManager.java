package com.xxhy.simpleai.ui.chat;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件管理器：处理图片/文件的选取、Base64 编码与预览。
 * 兼容 Android 4.x，使用 content:// URI 与 Base64 数据 URL。
 */
public class AttachmentManager {

    /** 单张图片最大字节数（Base64 编码前） */
    private static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024; // 4MB

    /** 附件类型 */
    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_FILE = 1;

    /** 附件项 */
    public static class Attachment {
        public final int type;
        public final Uri uri;
        public final String displayName;
        public final String dataUrl; // 仅图片有，data:image/...;base64,...

        public Attachment(int type, Uri uri, String displayName, String dataUrl) {
            this.type = type;
            this.uri = uri;
            this.displayName = displayName;
            this.dataUrl = dataUrl;
        }

        public boolean isImage() {
            return type == TYPE_IMAGE;
        }
    }

    private final Context context;
    private final List<Attachment> attachments = new ArrayList<>();

    public AttachmentManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 添加图片附件（自动压缩并转 Base64 数据 URL） */
    public boolean addImage(Uri uri) {
        if (uri == null) return false;
        try {
            String dataUrl = encodeImageToDataUrl(uri);
            if (dataUrl == null) return false;
            String name = queryDisplayName(uri);
            if (name == null || name.isEmpty()) {
                name = "image_" + System.currentTimeMillis() + ".jpg";
            }
            attachments.add(new Attachment(TYPE_IMAGE, uri, name, dataUrl));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 添加文件附件（仅记录 URI 与名称，文本类附件可由调用方读取内容拼入消息） */
    public boolean addFile(Uri uri) {
        if (uri == null) return false;
        String name = queryDisplayName(uri);
        if (name == null || name.isEmpty()) {
            name = "file_" + System.currentTimeMillis();
        }
        attachments.add(new Attachment(TYPE_FILE, uri, name, null));
        return true;
    }

    /** 移除指定附件 */
    public void remove(int index) {
        if (index >= 0 && index < attachments.size()) {
            attachments.remove(index);
        }
    }

    public void clear() {
        attachments.clear();
    }

    public List<Attachment> getAll() {
        return new ArrayList<>(attachments);
    }

    public int size() {
        return attachments.size();
    }

    public boolean isEmpty() {
        return attachments.isEmpty();
    }

    /** 返回所有图片的数据 URL 列表（用于多模态请求） */
    public List<String> getImageDataUrls() {
        List<String> urls = new ArrayList<>();
        for (Attachment a : attachments) {
            if (a.isImage() && a.dataUrl != null) {
                urls.add(a.dataUrl);
            }
        }
        return urls;
    }

    /** 是否包含图片 */
    public boolean hasImage() {
        for (Attachment a : attachments) {
            if (a.isImage()) return true;
        }
        return false;
    }

    /** 读取文件内容为文本（仅对小文本文件适用） */
    public String readFileAsText(Uri uri, int maxBytes) {
        InputStream is = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int total = 0;
            int n;
            while ((n = is.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    return null; // 超出限制
                }
                baos.write(buffer, 0, n);
            }
            return baos.toString("UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 将图片压缩并编码为 data URL */
    private String encodeImageToDataUrl(Uri uri) {
        InputStream is = null;
        Bitmap bitmap = null;
        Bitmap scaled = null;
        try {
            is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) return null;

            // 压缩到合理尺寸
            int maxDim = 1024;
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            Bitmap source = bitmap;
            if (w > maxDim || h > maxDim) {
                float scale = Math.min((float) maxDim / w, (float) maxDim / h);
                int nw = Math.round(w * scale);
                int nh = Math.round(h * scale);
                scaled = Bitmap.createScaledBitmap(bitmap, nw, nh, true);
                source = scaled;
            }

            // JPEG 压缩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int quality = 80;
            source.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            byte[] bytes = baos.toByteArray();

            // 若超出限制，降低质量重试
            while (bytes.length > MAX_IMAGE_BYTES && quality > 30) {
                quality -= 20;
                baos.reset();
                source.compress(Bitmap.CompressFormat.JPEG, quality, baos);
                bytes = baos.toByteArray();
            }

            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            return "data:image/jpeg;base64," + base64;
        } catch (Exception e) {
            return null;
        } finally {
            // 释放原图与缩放图（若为不同对象）
            if (scaled != null && scaled != bitmap && !scaled.isRecycled()) {
                scaled.recycle();
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** 查询 URI 对应的显示名称 */
    private String queryDisplayName(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        // 回退：从 URI 末段提取
        String last = uri.getLastPathSegment();
        return last != null ? last : "unknown";
    }
}
