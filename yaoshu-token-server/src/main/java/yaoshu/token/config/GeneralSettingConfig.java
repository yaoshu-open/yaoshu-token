package yaoshu.token.config;

import lombok.Data;

/**
 * 额度展示类型枚举 + 通用设置  */
public final class GeneralSettingConfig {

    private GeneralSettingConfig() {
    }

    // ======================== 额度展示类型常量 ========================

    public static final String QUOTA_DISPLAY_USD = "USD";
    public static final String QUOTA_DISPLAY_CNY = "CNY";
    public static final String QUOTA_DISPLAY_TOKENS = "TOKENS";
    public static final String QUOTA_DISPLAY_CUSTOM = "CUSTOM";

    // ======================== 默认值 ========================

    /** 文档链接 */
    private static volatile String docsLink = "https://docs.token.yaoshu.cc";
    /** 是否启用心跳间隔 */
    private static volatile boolean pingIntervalEnabled;
    /** 心跳间隔（秒） */
    private static volatile int pingIntervalSeconds = 60;
    /** 额度展示类型（默认 CNY：1 USD ≈ 7 CNY，usdExchangeRate 默认 7.0） */
    private static volatile String quotaDisplayType = QUOTA_DISPLAY_CNY;
    /** 自定义货币符号 */
    private static volatile String customCurrencySymbol = "¤";
    /** 自定义货币与美元汇率（1 USD = X Custom） */
    private static volatile double customCurrencyExchangeRate = 1.0;

    // ======================== Getters/Setters ========================

    public static String getDocsLink() { return docsLink; }
    public static void setDocsLink(String v) { docsLink = v; }

    public static boolean isPingIntervalEnabled() { return pingIntervalEnabled; }
    public static void setPingIntervalEnabled(boolean v) { pingIntervalEnabled = v; }

    public static int getPingIntervalSeconds() { return pingIntervalSeconds; }
    public static void setPingIntervalSeconds(int v) { pingIntervalSeconds = v; }

    public static String getQuotaDisplayType() { return quotaDisplayType; }
    public static void setQuotaDisplayType(String v) { quotaDisplayType = v; }

    public static String getCustomCurrencySymbol() { return customCurrencySymbol; }
    public static void setCustomCurrencySymbol(String v) { customCurrencySymbol = v; }

    public static double getCustomCurrencyExchangeRate() { return customCurrencyExchangeRate; }
    public static void setCustomCurrencyExchangeRate(double v) { customCurrencyExchangeRate = v; }

    // ======================== 业务方法 ========================

    /** 是否以货币形式展示（非 TOKENS） */
    public static boolean isCurrencyDisplay() {
        return !QUOTA_DISPLAY_TOKENS.equals(quotaDisplayType);
    }

    /** 是否以人民币展示 */
    public static boolean isCNYDisplay() {
        return QUOTA_DISPLAY_CNY.equals(quotaDisplayType);
    }

    /** 获取当前货币符号 */
    public static String getCurrencySymbol() {
        switch (quotaDisplayType) {
            case QUOTA_DISPLAY_USD: return "$";
            case QUOTA_DISPLAY_CNY: return "¥";
            case QUOTA_DISPLAY_CUSTOM: return customCurrencySymbol != null && !customCurrencySymbol.isEmpty() ? customCurrencySymbol : "¤";
            default: return "";
        }
    }

    /** 1 USD = X currency 的 X */
    public static double getUsdToCurrencyRate(double usdToCny) {
        switch (quotaDisplayType) {
            case QUOTA_DISPLAY_USD: return 1;
            case QUOTA_DISPLAY_CNY: return usdToCny;
            case QUOTA_DISPLAY_CUSTOM: return customCurrencyExchangeRate > 0 ? customCurrencyExchangeRate : 1;
            default: return 1;
        }
    }
}
