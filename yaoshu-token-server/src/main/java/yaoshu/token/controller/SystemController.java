package yaoshu.token.controller;

import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.config.GeneralSettingConfig;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.service.OptionService;
import yaoshu.token.service.SystemService;
import yaoshu.token.service.SetupService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统控制器  * <p>
 * 提供系统状态、法律条款、公告、首页内容等公开端点。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;
    private final OptionService optionService;
    private final SetupService setupService;

    /**
     * 获取系统状态      * <p>
     * 前端通过 /api/status 获取系统配置信息，包括：
     * - 系统名称、Logo、页脚等展示信息
     * - 各种功能开关（OAuth、注册、邮件验证等）
     * - chats 数组（用于侧边栏聊天链接）
     * - 配额展示相关配置
     */
    @GetMapping("/status")
    public Result<?> getStatus() {
        CommonConstants.optionMapLock.readLock().lock();
        try {
            Map<String, Object> status = new LinkedHashMap<>();

            // 版本与启动时间
            status.put("version", "v1.0");
            status.put("startTime", systemService.getStartTime());

            // 系统展示（Go: common.SystemName / Logo / Footer 等）
            status.put("systemName", getOption("SystemName"));
            status.put("logo", getOption("Logo"));
            status.put("footerHtml", getOption("Footer"));
            status.put("serverAddress", getOption("ServerAddress"));

            // 功能开关
            status.put("emailVerification", boolOption("EmailVerificationEnabled"));
            status.put("githubOauth", boolOption("GitHubOAuthEnabled"));
            status.put("githubClientId", getOption("GitHubClientId"));
            status.put("linuxdoOauth", boolOption("LinuxDOOAuthEnabled"));
            status.put("linuxdoClientId", getOption("LinuxDOClientId"));
            status.put("telegramOauth", boolOption("TelegramOAuthEnabled"));
            status.put("telegramBotName", getOption("TelegramBotName"));
            status.put("wechatQrCodeUrl", getOption("WeChatAccountQRCodeImageURL"));
            status.put("wechatLogin", boolOption("WeChatAuthEnabled"));
            status.put("turnstileCheck", boolOption("TurnstileCheckEnabled"));
            status.put("turnstileSiteKey", getOption("TurnstileSiteKey"));

            // 注册与登录
            status.put("registerEnabled", boolOption("RegisterEnabled"));
            status.put("passwordLoginEnabled", boolOption("PasswordLoginEnabled"));
            status.put("passwordRegisterEnabled", boolOption("PasswordRegisterEnabled"));
            status.put("passkeyLogin", boolOption("PasskeyLoginEnabled"));

            // 配额展示（兼容旧前端 display_in_currency + 新 quota_display_type）
            status.put("displayInCurrency", boolOption("DisplayInCurrencyEnabled"));
            status.put("quotaPerUnit", doubleOption("QuotaPerUnit", 500000.0));
            // quotaDisplayType：管理员配置的展示货币（USD/CNY/TOKENS/CUSTOM），对应前端 currency.ts getDisplayMeta 分支
            status.put("quotaDisplayType", GeneralSettingConfig.getQuotaDisplayType());

            // 功能模块
            status.put("enableDrawing", boolOption("DrawingEnabled"));
            status.put("enableTask", boolOption("TaskEnabled"));
            status.put("enableDataExport", boolOption("DataExportEnabled"));
            status.put("dataExportDefaultTime", getOption("DataExportDefaultTime"));
            status.put("defaultCollapseSidebar", boolOption("DefaultCollapseSidebar"));
            status.put("enableBatchUpdate", boolOption("AutomaticDisableChannelEnabled"));

            // 聊天链接（Go: setting.Chats → JSON 数组）
            status.put("chats", parseJsonArray(getOption("Chats")));

            // 文档链接
            status.put("docsLink", getOption("DocsLink"));

            // Midjourney
            status.put("mjNotifyEnabled", boolOption("MjNotifyEnabled"));

            // 支付
            status.put("usdExchangeRate", doubleOption("USDExchangeRate", 7.0));
            status.put("price", doubleOption("Price", 0.1));
            status.put("stripeUnitPrice", doubleOption("StripeUnitPrice", 0.1));

            // 模式
            status.put("demoSiteEnabled", boolOption("DemoSiteEnabled"));
            status.put("selfUseModeEnabled", boolOption("SelfUseModeEnabled"));
            status.put("defaultUseAutoGroup", boolOption("DefaultUseAutoGroup"));

            // 前端导航模块配置
            status.put("headerNavModules", parseJsonObject(getOption("HeaderNavModules")));
            status.put("sidebarModulesAdmin", parseJsonObject(getOption("SidebarModulesAdmin")));

            // 法律
            String userAgreement = systemService.getUserAgreement();
            String privacyPolicy = systemService.getPrivacyPolicy();
            status.put("userAgreementEnabled", userAgreement != null && !userAgreement.isEmpty());
            status.put("privacyPolicyEnabled", privacyPolicy != null && !privacyPolicy.isEmpty());

            // Setup 状态（setups 表有记录 = 已完成初始化）
            status.put("setup", setupService.isInitialized());

            // 控制台扩展内容（ApiInfo / FAQ），优先读 console_setting.* 回退 legacy key
            String apiInfoRaw = getOption("console_setting.api_info");
            if (apiInfoRaw.isEmpty()) apiInfoRaw = getOption("ApiInfo");
            status.put("apiInfo", parseJsonArray(apiInfoRaw));

            String faqRaw = getOption("console_setting.faq");
            if (faqRaw.isEmpty()) faqRaw = getOption("FAQ");
            status.put("faq", parseJsonArray(faqRaw));

            return R.success(status);
        } finally {
            CommonConstants.optionMapLock.readLock().unlock();
        }
    }

    /**
     * 获取用户协议      */
    @GetMapping("/legal/user_agreement")
    public Result<?> getUserAgreement() {
        return R.success(systemService.getUserAgreement());
    }

    /**
     * 获取隐私政策      */
    @GetMapping("/legal/privacy_policy")
    public Result<?> getPrivacyPolicy() {
        return R.success(systemService.getPrivacyPolicy());
    }

    /**
     * 获取公告      */
    @GetMapping("/notice")
    public Result<?> getNotice() {
        return R.success(systemService.getOptionValue("Notice"));
    }

    /**
     * 获取关于信息      */
    @GetMapping("/about")
    public Result<?> getAbout() {
        return R.success(systemService.getOptionValue("About"));
    }

    /**
     * 获取首页内容      */
    @GetMapping("/home_page_content")
    public Result<?> getHomePageContent() {
        return R.success(systemService.getOptionValue("HomePageContent"));
    }

    // ======================== 选项读取辅助方法 ========================

    /**
     * 从 OptionMap 读取字符串值
     */
    private String getOption(String key) {
        Map<String, String> optionMap = CommonConstants.optionMap;
        if (optionMap == null) {
            return "";
        }
        String value = optionMap.get(key);
        return value != null ? value : "";
    }

    /**
     * 从 OptionMap 读取布尔值
     */
    private boolean boolOption(String key) {
        return "true".equalsIgnoreCase(getOption(key));
    }

    /**
     * 从 OptionMap 读取 Double 值
     */
    private double doubleOption(String key, double defaultValue) {
        String value = getOption(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析 JSON 数组字符串
     */
    private Object parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            return Convert.toJSONArray(json);
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 解析 JSON 对象字符串
     */
    private Object parseJsonObject(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return Convert.toJSONObject(json);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
