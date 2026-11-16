package com.xxhy.simpleai.registry;

import com.xxhy.simpleai.data.model.ModelConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 模型信息注册表。Java 移植自 rikkahub 的 ModelRegistry.kt。
 *
 * 根据模型 ID 自动推断：
 * - 输入/输出模态（文本/图片）
 * - 模型能力（工具调用 / 推理）
 *
 * 通过 token 序列匹配实现：将模型 ID 拆分为字母/数字/符号 token，
 * 然后按顺序匹配预定义的 token 序列，得分最高者胜出。
 */
public final class ModelRegistry {

    private ModelRegistry() {
    }

    // ===== 模态与能力常量 =====
    private static final String M_TEXT = ModelConfig.MODALITY_TEXT;
    private static final String M_IMAGE = ModelConfig.MODALITY_IMAGE;
    private static final String A_TOOL = ModelConfig.ABILITY_TOOL;
    private static final String A_REASONING = ModelConfig.ABILITY_REASONING;

    /** 模型定义：token 序列 + 输入模态 + 输出模态 + 能力 */
    private static class ModelDef {
        final String[] tokens;
        final String[] notTokens;
        final String exactId;
        final Set<String> inputModalities;
        final Set<String> outputModalities;
        final Set<String> abilities;

        ModelDef(String[] tokens, String[] notTokens, String exactId,
                 Set<String> in, Set<String> out, Set<String> ab) {
            this.tokens = tokens;
            this.notTokens = notTokens;
            this.exactId = exactId;
            this.inputModalities = in;
            this.outputModalities = out;
            this.abilities = ab;
        }

        /** 返回匹配得分（匹配的 token 数），不匹配返回 -1 */
        int matchScore(String modelId, List<String> tokens) {
            if (exactId != null) {
                return exactId.equalsIgnoreCase(modelId) ? 1000 + tokens.size() : -1;
            }
            if (this.tokens != null && !sequenceMatch(this.tokens, tokens)) {
                return -1;
            }
            if (notTokens != null && sequenceMatch(notTokens, tokens)) {
                return -1;
            }
            return this.tokens == null ? 0 : this.tokens.length;
        }
    }

    /** 顺序匹配：token 序列中的每个 spec 必须按顺序在 tokens 中出现 */
    private static boolean sequenceMatch(String[] specs, List<String> tokens) {
        if (specs == null || specs.length == 0) return false;
        int specIndex = 0;
        for (String token : tokens) {
            if (tokenMatches(specs[specIndex], token)) {
                specIndex++;
                if (specIndex == specs.length) return true;
            }
        }
        return false;
    }

    /** token spec 匹配单个 token：支持 "a|b" 表示或 */
    private static boolean tokenMatches(String spec, String token) {
        if (spec == null || token == null) return false;
        String[] options = spec.toLowerCase().split("\\|");
        String t = token.toLowerCase();
        for (String o : options) {
            String opt = o.trim();
            if (opt.isEmpty()) continue;
            // 支持简单正则：^xxx$ 形式
            if (opt.startsWith("^") || opt.endsWith("$")) {
                try {
                    if (java.util.regex.Pattern.compile(opt,
                            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(token).matches()) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            } else if (opt.equals(t)) {
                return true;
            }
        }
        return false;
    }

    // ===== 构造辅助方法 =====
    private static Set<String> setOf(String... arr) {
        return new LinkedHashSet<>(Arrays.asList(arr));
    }

    private static Set<String> textIn() {
        return setOf(M_TEXT, M_IMAGE);
    }

    private static Set<String> textOut() {
        return setOf(M_TEXT);
    }

    private static Set<String> imgOut() {
        return setOf(M_TEXT, M_IMAGE);
    }

    private static Set<String> tool() {
        return setOf(A_TOOL);
    }

    private static Set<String> reasoning() {
        return setOf(A_REASONING);
    }

    private static Set<String> toolReasoning() {
        return setOf(A_TOOL, A_REASONING);
    }

    private static ModelDef def(String[] tokens, Set<String> in, Set<String> out, Set<String> ab) {
        return new ModelDef(tokens, null, null, in, out, ab);
    }

    private static ModelDef defWithNot(String[] tokens, String[] notTokens,
                                       Set<String> in, Set<String> out, Set<String> ab) {
        return new ModelDef(tokens, notTokens, null, in, out, ab);
    }

    private static ModelDef exact(String id, Set<String> in, Set<String> out, Set<String> ab) {
        return new ModelDef(null, null, id, in, out, ab);
    }

    // ===== 预定义模型列表（移植自 rikkahub ModelRegistry） =====
    private static final List<ModelDef> ALL_MODELS = new ArrayList<ModelDef>() {{
        // OpenAI GPT 系列
        add(def(new String[]{"gpt", "4", "o"}, textIn(), textOut(), tool()));
        add(def(new String[]{"gpt", "4", "1"}, textIn(), textOut(), tool()));
        add(def(new String[]{"o", "^\\d+$"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "oss"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(defWithNot(new String[]{"gpt", "5"}, new String[]{"gpt", "5", "."},
                textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "1"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "2"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "3"}, textIn(), textOut(), tool()));
        add(def(new String[]{"gpt", "5", "4"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "4", "mini"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "4", "nano"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "5"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gpt", "5", "6"}, textIn(), textOut(), toolReasoning()));

        // Gemini 系列
        add(def(new String[]{"gemini", "2", "0", "flash"}, textIn(), textOut(), tool()));
        add(defWithNot(new String[]{"gemini", "2", "5", "flash"}, new String[]{"image"},
                textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gemini", "2", "5", "pro"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gemini", "2", "5", "flash", "image"}, textIn(), imgOut(), setOf()));
        add(def(new String[]{"gemini", "3", "pro", "image"}, textIn(), imgOut(), setOf()));
        add(def(new String[]{"nano", "banana"}, textIn(), imgOut(), setOf()));
        add(def(new String[]{"gemini", "3", "pro"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gemini", "3", "flash"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gemini", "3", "1", "pro", "preview"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"gemini", "3", "1", "flash", "image"}, textIn(), imgOut(), reasoning()));
        add(def(new String[]{"gemini", "3", "5"}, textIn(), textOut(), toolReasoning()));
        add(exact("gemini-flash-latest", textIn(), textOut(), toolReasoning()));
        add(exact("gemini-pro-latest", textIn(), textOut(), toolReasoning()));

        // Claude 系列
        add(def(new String[]{"claude", "3", "5", "sonnet"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "3", "7", "sonnet"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "4"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "4", "5"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "sonnet", "4", "6"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "opus", "4", "6"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "opus", "4", "7"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"claude", "opus", "4", "8"}, textIn(), textOut(), toolReasoning()));

        // DeepSeek 系列
        add(def(new String[]{"deepseek", "v", "3"}, setOf(M_TEXT), textOut(), tool()));
        add(def(new String[]{"deepseek", "chat"}, setOf(M_TEXT), textOut(), tool()));
        add(def(new String[]{"deepseek", "r", "1"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"deepseek", "reasoner"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"deepseek", "v", "4", "flash"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"deepseek", "v", "4", "pro"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"deepseek", "v", "3", "1"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"deepseek", "v", "3", "2"}, setOf(M_TEXT), textOut(), toolReasoning()));

        // Qwen 系列
        add(def(new String[]{"qwen", "3"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"qwen", "3", "5"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"qwen", "3", "6"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"qwen", "3", "7"}, textIn(), textOut(), toolReasoning()));

        // 豆包
        add(def(new String[]{"doubao", "1", "6"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"doubao", "1", "8"}, textIn(), textOut(), toolReasoning()));

        // Grok
        add(def(new String[]{"grok", "4"}, textIn(), textOut(), toolReasoning()));

        // Kimi
        add(def(new String[]{"kimi", "k", "2"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"kimi", "k", "2", "5"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"kimi", "k", "2", "6"}, textIn(), textOut(), toolReasoning()));

        // Step
        add(def(new String[]{"step", "3"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"step", "3", "7", "flash"}, textIn(), textOut(), toolReasoning()));

        // Intern
        add(def(new String[]{"intern", "s", "1"}, textIn(), textOut(), toolReasoning()));

        // GLM
        add(def(new String[]{"glm", "4", "5"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"glm", "4", "6"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"glm", "4", "7"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"glm", "5"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"glm", "5", "1"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"glm", "5", "2"}, textIn(), textOut(), toolReasoning()));

        // MiniMax
        add(def(new String[]{"minimax", "m", "2"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"minimax", "m", "2", "5"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"minimax", "m", "2", "7"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"minimax", "m", "3"}, textIn(), textOut(), toolReasoning()));

        // 小米 MiMo
        add(def(new String[]{"mimo", "v", "2"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"mimo", "v", "2", "pro"}, setOf(M_TEXT), textOut(), toolReasoning()));
        add(def(new String[]{"mimo", "v", "2", "5"}, textIn(), textOut(), toolReasoning()));
        add(def(new String[]{"mimo", "v", "2", "5", "pro"}, setOf(M_TEXT), textOut(), toolReasoning()));

        // Qwen MT 翻译模型
        add(def(new String[]{"qwen", "mt"}, setOf(M_TEXT), textOut(), setOf()));
    }};

    // ===== 公共 API =====

    /** 推断模型输入模态（逗号分隔） */
    public static String inputModalitiesOf(String modelId) {
        return joinModalities(resolveInputModalities(modelId));
    }

    /** 推断模型输出模态（逗号分隔） */
    public static String outputModalitiesOf(String modelId) {
        return joinModalities(resolveOutputModalities(modelId));
    }

    /** 推断模型能力（逗号分隔） */
    public static String abilitiesOf(String modelId) {
        Set<String> abilities = new LinkedHashSet<>();
        for (ModelDef def : resolveModels(modelId)) {
            abilities.addAll(def.abilities);
        }
        // 保持顺序：tool 在前，reasoning 在后
        List<String> ordered = new ArrayList<>();
        if (abilities.contains(A_TOOL)) ordered.add(A_TOOL);
        if (abilities.contains(A_REASONING)) ordered.add(A_REASONING);
        return TextUtilsJoin.join(",", ordered);
    }

    /** 是否支持图片输入 */
    public static boolean isVisionSupported(String modelId) {
        return resolveInputModalities(modelId).contains(M_IMAGE);
    }

    /** 是否为推理模型 */
    public static boolean isReasoningModel(String modelId) {
        return containsToken(abilitiesOf(modelId), A_REASONING);
    }

    /** 是否支持工具调用 */
    public static boolean isToolSupported(String modelId) {
        return containsToken(abilitiesOf(modelId), A_TOOL);
    }

    /** 根据模型 ID 应用自动推断的模态与能力到 ModelConfig */
    public static void applyTo(ModelConfig model) {
        if (model == null || model.getModelId() == null || model.getModelId().isEmpty()) {
            return;
        }
        String id = model.getModelId();
        model.setInputModalities(inputModalitiesOf(id));
        model.setOutputModalities(outputModalitiesOf(id));
        model.setAbilities(abilitiesOf(id));
    }

    // ===== 内部实现 =====

    private static List<ModelDef> resolveModels(String modelId) {
        if (modelId == null || modelId.isEmpty()) return Collections.emptyList();
        List<String> tokens = tokenize(modelId);
        Integer bestScore = null;
        List<ModelDef> matches = new ArrayList<>();
        for (ModelDef def : ALL_MODELS) {
            int score = def.matchScore(modelId, tokens);
            if (score < 0) continue;
            if (bestScore == null || score > bestScore) {
                bestScore = score;
                matches.clear();
                matches.add(def);
            } else if (score == bestScore) {
                matches.add(def);
            }
        }
        return matches;
    }

    private static Set<String> resolveInputModalities(String modelId) {
        Set<String> modalities = new LinkedHashSet<>();
        for (ModelDef def : resolveModels(modelId)) {
            modalities.addAll(def.inputModalities);
        }
        if (modalities.isEmpty()) {
            modalities.add(M_TEXT);
        }
        return modalities;
    }

    private static Set<String> resolveOutputModalities(String modelId) {
        Set<String> modalities = new LinkedHashSet<>();
        for (ModelDef def : resolveModels(modelId)) {
            modalities.addAll(def.outputModalities);
        }
        if (modalities.isEmpty()) {
            modalities.add(M_TEXT);
        }
        return modalities;
    }

    private static String joinModalities(Set<String> modalities) {
        // 保持顺序：text 在前，image 在后
        List<String> ordered = new ArrayList<>();
        if (modalities.contains(M_TEXT)) ordered.add(M_TEXT);
        if (modalities.contains(M_IMAGE)) ordered.add(M_IMAGE);
        return TextUtilsJoin.join(",", ordered);
    }

    private static boolean containsToken(String csv, String token) {
        if (csv == null || token == null) return false;
        for (String p : csv.split(",")) {
            if (token.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    /** 将模型 ID 拆分为字母/数字/符号 token（小写） */
    private static List<String> tokenize(String modelId) {
        List<String> tokens = new ArrayList<>();
        String input = modelId.toLowerCase();
        int index = 0;
        while (index < input.length()) {
            char ch = input.charAt(index);
            if (Character.isLetter(ch)) {
                int start = index;
                index++;
                while (index < input.length() && Character.isLetter(input.charAt(index))) {
                    index++;
                }
                tokens.add(input.substring(start, index));
            } else if (Character.isDigit(ch)) {
                int start = index;
                index++;
                while (index < input.length() && Character.isDigit(input.charAt(index))) {
                    index++;
                }
                tokens.add(input.substring(start, index));
            } else {
                tokens.add(String.valueOf(ch));
                index++;
            }
        }
        return tokens;
    }

    /** 简单的字符串连接工具，避免依赖 android.text.TextUtils */
    private static final class TextUtilsJoin {
        static String join(CharSequence delimiter, Iterable<String> parts) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (String p : parts) {
                if (!first) sb.append(delimiter);
                sb.append(p);
                first = false;
            }
            return sb.toString();
        }
    }
}
