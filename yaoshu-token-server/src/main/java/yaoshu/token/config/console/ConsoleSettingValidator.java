package yaoshu.token.config.console;

import ai.yue.library.base.convert.Convert;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 控制台设置校验器  *
 * <p>核心方法 {@link #validateConsoleSettings(String, String)}  * 按 settingType 分发到 ApiInfo / Announcements / FAQ / UptimeKumaGroups 四类列表校验。
 */
public final class ConsoleSettingValidator {

    /** URL 校验正则*/
    private static final Pattern URL_REGEX = Pattern.compile(
            "^https?://(?:(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?|"
                    + "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))"
                    + "(?::[0-9]{1,5})?(?:/.*)?$");

    /** 危险字符黑名单 */
    private static final List<String> DANGEROUS_CHARS = Arrays.asList(
            "<script", "<iframe", "javascript:", "onload=", "onerror=", "onclick=");

    /** 颜色白名单 */
    private static final Set<String> VALID_COLORS = new HashSet<>(Arrays.asList(
            "blue", "green", "cyan", "purple", "pink",
            "red", "orange", "amber", "yellow", "lime",
            "light-green", "teal", "light-blue", "indigo",
            "violet", "grey", "slate"));

    /** Slug 字符白名单*/
    private static final Pattern SLUG_REGEX = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /** 公告类型白名单*/
    private static final Set<String> VALID_ANNOUNCEMENT_TYPES = new HashSet<>(Arrays.asList(
            "default", "ongoing", "success", "warning", "error"));

    private ConsoleSettingValidator() {
    }

    // ---------------------------- 简单工具方法（旧版保留） ----------------------------

    /** 校验 URL 是否合法 */
    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /** 校验端口范围 */
    public static boolean isValidPort(int port) {
        return port > 0 && port <= 65535;
    }

    /** 校验邮件地址格式 */
    public static boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) return false;
        return email.contains("@") && email.contains(".");
    }

    /** 校验 JSON 字符串 */
    public static boolean isValidJson(String json) {
        if (json == null || json.isEmpty()) return true;
        try {
            Convert.toJSONObject(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------- 核心入口 ----------------------------

    /**
     * 控制台设置统一校验入口。      *
     * @param settingsStr JSON 字符串（数组）
     * @param settingType ApiInfo / Announcements / FAQ / UptimeKumaGroups
     * @throws IllegalArgumentException 校验不通过时抛出，message 与 Go 错误信息一致
     */
    public static void validateConsoleSettings(String settingsStr, String settingType) {
        if (settingsStr == null || settingsStr.isEmpty()) {
            return;
        }
        switch (settingType) {
            case "ApiInfo":
                validateApiInfo(settingsStr);
                return;
            case "Announcements":
                validateAnnouncements(settingsStr);
                return;
            case "FAQ":
                validateFaq(settingsStr);
                return;
            case "UptimeKumaGroups":
                validateUptimeKumaGroups(settingsStr);
                return;
            default:
                throw new IllegalArgumentException("未知的设置类型：" + settingType);
        }
    }

    // ---------------------------- 私有：分类校验 ----------------------------

    /** 解析 JSON 数组为 List<Map> */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseJsonArray(String jsonStr, String typeName) {
        try {
            Object parsed = Convert.toJSONArray(jsonStr).toJavaList(Map.class);
            return (List<Map<String, Object>>) parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException(typeName + "格式错误：" + e.getMessage());
        }
    }

    private static void validateUrl(String urlStr, int index, String itemType) {
        if (!URL_REGEX.matcher(urlStr).matches()) {
            throw new IllegalArgumentException("第" + index + "个" + itemType + "的URL格式不正确");
        }
    }

    private static void checkDangerousContent(String content, int index, String itemType) {
        if (content == null) return;
        String lower = content.toLowerCase();
        for (String dangerous : DANGEROUS_CHARS) {
            if (lower.contains(dangerous)) {
                throw new IllegalArgumentException("第" + index + "个" + itemType + "包含不允许的内容");
            }
        }
    }

    /** 校验 ApiInfo */
    private static void validateApiInfo(String apiInfoStr) {
        List<Map<String, Object>> list = parseJsonArray(apiInfoStr, "API信息");
        if (list.size() > 50) {
            throw new IllegalArgumentException("API信息数量不能超过50个");
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> item = list.get(i);
            int idx = i + 1;
            String url = requireString(item.get("url"), idx, "API信息", "URL");
            String route = requireString(item.get("route"), idx, "API信息", "线路描述");
            String description = requireString(item.get("description"), idx, "API信息", "说明");
            String color = requireString(item.get("color"), idx, "API信息", "颜色");

            validateUrl(url, idx, "API信息");

            if (url.length() > 500) {
                throw new IllegalArgumentException("第" + idx + "个API信息的URL长度不能超过500字符");
            }
            if (route.length() > 100) {
                throw new IllegalArgumentException("第" + idx + "个API信息的线路描述长度不能超过100字符");
            }
            if (description.length() > 200) {
                throw new IllegalArgumentException("第" + idx + "个API信息的说明长度不能超过200字符");
            }
            if (!VALID_COLORS.contains(color)) {
                throw new IllegalArgumentException("第" + idx + "个API信息的颜色值不合法");
            }
            checkDangerousContent(description, idx, "API信息");
            checkDangerousContent(route, idx, "API信息");
        }
    }

    /** 校验 Announcements */
    private static void validateAnnouncements(String announcementsStr) {
        List<Map<String, Object>> list = parseJsonArray(announcementsStr, "系统公告");
        if (list.size() > 100) {
            throw new IllegalArgumentException("系统公告数量不能超过100个");
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> item = list.get(i);
            int idx = i + 1;
            String content = requireString(item.get("content"), idx, "公告", "内容");

            Object publishDateAny = item.get("publishDate");
            if (publishDateAny == null) {
                throw new IllegalArgumentException("第" + idx + "个公告缺少发布日期字段");
            }
            String publishDateStr = publishDateAny instanceof String ? (String) publishDateAny : "";
            if (publishDateStr.isEmpty()) {
                throw new IllegalArgumentException("第" + idx + "个公告的发布日期不能为空");
            }
            try {
                // RFC3339（与 Go time.RFC3339 等价）
                OffsetDateTime.parse(publishDateStr);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("第" + idx + "个公告的发布日期格式错误");
            }

            Object typeAny = item.get("type");
            if (typeAny instanceof String) {
                String typeStr = (String) typeAny;
                if (!typeStr.isEmpty() && !VALID_ANNOUNCEMENT_TYPES.contains(typeStr)) {
                    throw new IllegalArgumentException("第" + idx + "个公告的类型值不合法");
                }
            }

            if (content.length() > 500) {
                throw new IllegalArgumentException("第" + idx + "个公告的内容长度不能超过500字符");
            }
            Object extraAny = item.get("extra");
            if (extraAny instanceof String && ((String) extraAny).length() > 200) {
                throw new IllegalArgumentException("第" + idx + "个公告的说明长度不能超过200字符");
            }
        }
    }

    /** 校验 FAQ */
    private static void validateFaq(String faqStr) {
        List<Map<String, Object>> list = parseJsonArray(faqStr, "FAQ信息");
        if (list.size() > 100) {
            throw new IllegalArgumentException("FAQ数量不能超过100个");
        }
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> item = list.get(i);
            int idx = i + 1;
            String question = requireString(item.get("question"), idx, "FAQ", "问题");
            String answer = requireString(item.get("answer"), idx, "FAQ", "答案");
            if (question.length() > 200) {
                throw new IllegalArgumentException("第" + idx + "个FAQ的问题长度不能超过200字符");
            }
            if (answer.length() > 1000) {
                throw new IllegalArgumentException("第" + idx + "个FAQ的答案长度不能超过1000字符");
            }
        }
    }

    /** 校验 UptimeKumaGroups */
    private static void validateUptimeKumaGroups(String groupsStr) {
        List<Map<String, Object>> list = parseJsonArray(groupsStr, "Uptime Kuma分组配置");
        if (list.size() > 20) {
            throw new IllegalArgumentException("Uptime Kuma分组数量不能超过20个");
        }
        Set<String> nameSet = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> item = list.get(i);
            int idx = i + 1;
            String categoryName = requireString(item.get("categoryName"), idx, "分组", "分类名称");
            if (nameSet.contains(categoryName)) {
                throw new IllegalArgumentException("第" + idx + "个分组的分类名称与其他分组重复");
            }
            nameSet.add(categoryName);
            String url = requireString(item.get("url"), idx, "分组", "URL");
            String slug = requireString(item.get("slug"), idx, "分组", "Slug");
            Object descAny = item.get("description");
            String description = descAny instanceof String ? (String) descAny : "";

            validateUrl(url, idx, "分组");

            if (categoryName.length() > 50) {
                throw new IllegalArgumentException("第" + idx + "个分组的分类名称长度不能超过50字符");
            }
            if (url.length() > 500) {
                throw new IllegalArgumentException("第" + idx + "个分组的URL长度不能超过500字符");
            }
            if (slug.length() > 100) {
                throw new IllegalArgumentException("第" + idx + "个分组的Slug长度不能超过100字符");
            }
            if (description.length() > 200) {
                throw new IllegalArgumentException("第" + idx + "个分组的描述长度不能超过200字符");
            }
            if (!SLUG_REGEX.matcher(slug).matches()) {
                throw new IllegalArgumentException("第" + idx + "个分组的Slug只能包含字母、数字、下划线和连字符");
            }
            checkDangerousContent(description, idx, "分组");
            checkDangerousContent(categoryName, idx, "分组");
        }
    }

    /** 必填字符串字段提取与校验 */
    private static String requireString(Object value, int idx, String itemType, String fieldName) {
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("第" + idx + "个" + itemType + "缺少" + fieldName + "字段");
        }
        return (String) value;
    }
}
