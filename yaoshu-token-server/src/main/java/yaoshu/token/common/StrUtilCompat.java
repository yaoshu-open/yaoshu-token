package yaoshu.token.common;

/**
 * Hutool v7 兼容层 —— 补充 v7 中移除的 {@code blankToDefault} / {@code nullToEmpty}。
 * <p>
 * Hutool v7 将 {@code StrUtil} 从 {@code cn.hutool.core.util} 迁至 {@code cn.hutool.v7.core.text}，
 * 并移除了 {@code blankToDefault}、{@code nullToEmpty} 等方法。本类提供等价实现，
 * 供迁移期间过渡使用。
 */
public final class StrUtilCompat {

    private StrUtilCompat() {
    }

    /**
     * 等价于 Hutool v5 {@code StrUtil.blankToDefault(str, defaultValue)}：
     * 若字符串为 {@code null} 或 blank（trim 后为空），返回默认值，否则返回原字符串。
     */
    public static String blankToDefault(String str, String defaultValue) {
        return (str != null && !str.isBlank()) ? str : defaultValue;
    }

    /**
     * 等价于 Hutool v5 {@code StrUtil.nullToEmpty(str)}：
     * 若字符串为 {@code null}，返回空字符串 {@code ""}，否则返回原字符串。
     */
    public static String nullToEmpty(String str) {
        return str != null ? str : "";
    }

    /**
     * 等价于 Hutool v5 {@code StrUtil.nullToDefault(str, defaultValue)}：
     * 若字符串为 {@code null}，返回默认值，否则返回原字符串。
     */
    public static String nullToDefault(String str, String defaultValue) {
        return str != null ? str : defaultValue;
    }

    /**
     * 等价于 Hutool v5 {@code StrUtil.emptyToNull(str)}：
     * 若字符串为 {@code null} 或 empty（长度为 0），返回 {@code null}，否则返回原字符串。
     */
    public static String emptyToNull(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        return str;
    }
}
