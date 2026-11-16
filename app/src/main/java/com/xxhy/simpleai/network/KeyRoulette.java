package com.xxhy.simpleai.network;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * API Key 轮询器。参考 rikkahub KeyRoulette 设计。
 * 支持空格/逗号/换行分隔的多 key，随机选择一个使用。
 * 兼容 Android 4.x。
 */
public class KeyRoulette {

    private static final String SPLIT_REGEX = "[\\s,]+";

    /** 复用单个 Random 实例，避免每次调用都创建新对象（Random 线程安全） */
    private final Random random = new Random();

    /**
     * 从可能包含多个 key 的字符串中随机选一个。
     *
     * @param keys 多 key 字符串（空格/逗号/换行分隔）
     * @return 选中的 key
     */
    public String next(String keys) {
        List<String> list = split(keys);
        if (list.isEmpty()) {
            return keys == null ? "" : keys;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        // 简单随机轮询（Android 4 无 ThreadLocalRandom）
        return list.get(random.nextInt(list.size()));
    }

    /** 拆分多 key 字符串，去重去空 */
    public static List<String> split(String keys) {
        List<String> result = new ArrayList<>();
        if (keys == null || keys.trim().isEmpty()) {
            return result;
        }
        String[] parts = keys.split(SPLIT_REGEX);
        // 用 LinkedHashSet 保持顺序去重，比 LinkedHashMap 更轻量
        Set<String> seen = new LinkedHashSet<String>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty() && seen.add(t)) {
                result.add(t);
            }
        }
        return result;
    }

    /** 统计 key 数量 */
    public static int count(String keys) {
        return split(keys).size();
    }
}
