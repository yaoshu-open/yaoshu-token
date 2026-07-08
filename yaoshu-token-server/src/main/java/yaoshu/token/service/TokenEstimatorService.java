package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Token 估算服务  * <p>
 * 当 tiktoken/jtokkit 不可用或非 OpenAI 模型时，使用字符级规则估算 Token 数量。
 * 按厂商（OpenAI / Gemini / Claude）区分计费权重。
 * <p>
 * 所有方法均为 static。  */
@Slf4j
public final class TokenEstimatorService {

    private TokenEstimatorService() {
    }

    // ======================== 厂商枚举（Go Provider） ========================

    /** 模型厂商大类 */
    public enum Provider {
        OPENAI, GEMINI, CLAUDE, UNKNOWN
    }

    // ======================== 计费权重（Go multipliers） ========================

    /** 不同厂商的计费权重 */
    private record Multipliers(
            double word, double number, double cjk, double symbol,
            double mathSymbol, double urlDelim, double atSign,
            double emoji, double newline, double space, int basePad
    ) {
    }

    /** 厂商 → 权重映射 */
    private static final java.util.Map<Provider, Multipliers> MULTIPLIERS_MAP = java.util.Map.of(
            Provider.GEMINI, new Multipliers(1.15, 2.8, 0.68, 0.38, 1.05, 1.2, 2.5, 1.08, 1.15, 0.2, 0),
            Provider.CLAUDE, new Multipliers(1.13, 1.63, 1.21, 0.4, 4.52, 1.26, 2.82, 2.6, 0.89, 0.39, 0),
            Provider.OPENAI, new Multipliers(1.02, 1.55, 0.85, 0.4, 2.68, 1.0, 2.0, 2.12, 0.5, 0.42, 0)
    );

    /** 获取厂商对应的权重 */
    private static Multipliers getMultipliers(Provider provider) {
        return MULTIPLIERS_MAP.getOrDefault(provider, MULTIPLIERS_MAP.get(Provider.OPENAI));
    }

    // ======================== Token 估算入口 ========================

    /**
     * 根据模型名估算 Token 数量      *
     * @param model 模型名（如 "gpt-4o"、"gemini-2.0-flash"、"claude-3.5-sonnet"）
     * @param text  待计数的文本
     * @return 估算的 Token 数量
     */
    public static int estimateTokenByModel(String model, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String lowerModel = model.toLowerCase();
        if (lowerModel.contains("gemini")) {
            return estimateToken(Provider.GEMINI, text);
        } else if (lowerModel.contains("claude")) {
            return estimateToken(Provider.CLAUDE, text);
        } else {
            return estimateToken(Provider.OPENAI, text);
        }
    }

    /**
     * 按厂商估算 Token 数量      * <p>
     * 状态机逐字符分析：CJK 字 / Emoji 表情 / 拉丁字母词 / 数字串 / 数学符号 / URL 分隔符 / 空格换行。
     */
    public static int estimateToken(Provider provider, String text) {
        Multipliers m = getMultipliers(provider);
        double count = 0;

        // 状态机：None / Latin（字母词）/ Number（数字串）
        enum WordType { NONE, LATIN, NUMBER }
        WordType currentWordType = WordType.NONE;

        for (int i = 0; i < text.length(); i++) {
            int codePoint = text.codePointAt(i);
            // 处理代理对（emoji 等双 char 字符）
            if (Character.isSupplementaryCodePoint(codePoint)) {
                i++; // 跳过低代理项
            }

            // 1. 空格和换行符
            if (Character.isWhitespace(codePoint)) {
                currentWordType = WordType.NONE;
                if (codePoint == '\n' || codePoint == '\t') {
                    count += m.newline();
                } else {
                    count += m.space();
                }
                continue;
            }

            // 2. CJK（中日韩字符）—— 一字一 Token
            if (isCJK(codePoint)) {
                currentWordType = WordType.NONE;
                count += m.cjk();
                continue;
            }

            // 3. Emoji 表情
            if (isEmoji(codePoint)) {
                currentWordType = WordType.NONE;
                count += m.emoji();
                continue;
            }

            // 4. 拉丁字母 / 数字（英文单词）
            if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) {
                boolean isNum = Character.isDigit(codePoint);
                WordType newType = isNum ? WordType.NUMBER : WordType.LATIN;

                // 新词开始 或 字母↔数字切换 → 计一个新 Token
                if (currentWordType == WordType.NONE || currentWordType != newType) {
                    if (newType == WordType.NUMBER) {
                        count += m.number();
                    } else {
                        count += m.word();
                    }
                    currentWordType = newType;
                }
                continue;
            }

            // 5. 标点符号 / 特殊字符 → 按类型使用不同权重
            currentWordType = WordType.NONE;
            if (isMathSymbol(codePoint)) {
                count += m.mathSymbol();
            } else if (codePoint == '@') {
                count += m.atSign();
            } else if (isUrlDelimiter(codePoint)) {
                count += m.urlDelim();
            } else {
                count += m.symbol();
            }
        }

        return (int) Math.ceil(count) + m.basePad();
    }

    // ======================== Unicode 字符分类辅助方法 ========================

    /** 判断是否为 CJK 字符 */
    private static boolean isCJK(int codePoint) {
        return Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                // 日文假名 (0x3040–0x30FF)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                // 韩文 (0xAC00–0xD7A3)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3);
    }

    /** 判断是否为 Emoji */
    private static boolean isEmoji(int codePoint) {
        return (codePoint >= 0x1F300 && codePoint <= 0x1F9FF)   // Emoticons, Symbols, Pictographs
                || (codePoint >= 0x2600 && codePoint <= 0x26FF)  // Misc Symbols
                || (codePoint >= 0x2700 && codePoint <= 0x27BF)  // Dingbats
                || (codePoint >= 0x1F600 && codePoint <= 0x1F64F) // Emoticons
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) // Supplemental Symbols
                || (codePoint >= 0x1FA00 && codePoint <= 0x1FAFF); // Extended-A
    }

    /** 判断是否为数学符号 */
    private static boolean isMathSymbol(int codePoint) {
        // 基本数学运算符 (U+2200–U+22FF)
        if (codePoint >= 0x2200 && codePoint <= 0x22FF) return true;
        // 补充数学运算符 (U+2A00–U+2AFF)
        if (codePoint >= 0x2A00 && codePoint <= 0x2AFF) return true;
        // 数学字母数字符号 (U+1D400–U+1D7FF)
        if (codePoint >= 0x1D400 && codePoint <= 0x1D7FF) return true;
        // 离散数学符号：∑ ∫ ∂ √ ∞ ≤ ≥ ≠ ≈ ± × ÷ 等
        String mathSymbols = "∑∫∂√∞≤≥≠≈±×÷∈∉∋∌⊂⊃⊆⊇∪∩∧∨¬∀∃∄∅∆∇∝∟∠∡∢°′″‴⁺⁻⁼⁽⁾ⁿ₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎²³¹⁴⁵⁶⁷⁸⁹⁰";
        return mathSymbols.indexOf(codePoint) >= 0;
    }

    /** 判断是否为 URL 分隔符 */
    private static boolean isUrlDelimiter(int codePoint) {
        return codePoint == '/' || codePoint == ':' || codePoint == '?' || codePoint == '&'
                || codePoint == '=' || codePoint == ';' || codePoint == '#' || codePoint == '%';
    }
}
