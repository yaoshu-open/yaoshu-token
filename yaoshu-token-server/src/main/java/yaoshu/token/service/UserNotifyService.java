package yaoshu.token.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import ai.yue.library.base.convert.Convert;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.pojo.entity.User;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 用户通知服务  * <p>
 * 向用户发送通知，支持 4 种通知方式：
 * 1. email — 邮件通知（通过 EmailService）
 * 2. webhook — Webhook 通知（通过 WebhookService）
 * 3. bark — Bark 推送通知
 * 4. gotify — Gotify 自托管推送
 * <p>
 * 通知频率由 NotifyLimitService 控制（防止短时间重复通知）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserNotifyService {

    private final UserService userService;
    private final NotifyLimitService notifyLimitService;    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 通知方式常量*/
    public static final String NOTIFY_TYPE_EMAIL = "email";
    public static final String NOTIFY_TYPE_WEBHOOK = "webhook";
    public static final String NOTIFY_TYPE_BARK = "bark";
    public static final String NOTIFY_TYPE_GOTIFY = "gotify";

    /** 通知事件类型*/
    public static final String EVENT_QUOTA_EXCEED = "quota_exceed";
    public static final String EVENT_CHANNEL_UPDATE = "channel_update";
    public static final String EVENT_CHANNEL_TEST = "channel_test";

    /** 内容占位符*/
    private static final String CONTENT_VALUE_PARAM = "{{value}}";

    // ======================== 主入口 ========================

    /**
     * 通知上游模型变更关注者      * <p>
     * 遍历所有管理员用户（role >= AdminUser），对开启了 upstreamModelUpdateNotifyEnabled 的用户发送通知。
     *
     * @param channelName  渠道名称
     * @param addModels    新增模型列表
     * @param removeModels 移除模型列表
     */
    public void notifyUpstreamModelUpdateWatchers(String channelName,
                                                   List<String> addModels,
                                                   List<String> removeModels) {
        if ((addModels == null || addModels.isEmpty()) && (removeModels == null || removeModels.isEmpty())) {
            return;
        }
        List<User> adminUsers = userService.listAdminUsers();
        if (adminUsers == null || adminUsers.isEmpty()) {
            return;
        }

        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("Channel: ").append(channelName);
        if (addModels != null && !addModels.isEmpty()) {
            contentBuilder.append("\nAdded: ").append(String.join(", ", addModels));
        }
        if (removeModels != null && !removeModels.isEmpty()) {
            contentBuilder.append("\nRemoved: ").append(String.join(", ", removeModels));
        }

        for (User admin : adminUsers) {
            UserSettingDto setting = parseUserSetting(admin.getSetting());
            if (!setting.isUpstreamModelUpdateNotifyEnabled()) {
                continue;
            }
            try {
                notifyUser(admin.getId(), admin.getEmail(), setting,
                        new NotifyDto(EVENT_CHANNEL_UPDATE,
                                "Upstream Model Update Detected",
                                contentBuilder.toString(),
                                null));
            } catch (Exception e) {
                log.warn("failed to notify upstream model update to user {}: {}", admin.getId(), e.getMessage());
            }
        }
    }

    /**
     * 通知根用户      *
     * @param type    通知事件类型
     * @param title   标题
     * @param content 内容
     */
    public void notifyRootUser(String type, String title, String content) {
        User rootUser = userService.getRootUser();
        if (rootUser == null) {
            log.warn("root user not found, skip notification");
            return;
        }
        UserSettingDto setting = parseUserSetting(rootUser.getSetting());
        try {
            notifyUser(rootUser.getId(), rootUser.getEmail(), setting, new NotifyDto(type, title, content, null));
        } catch (Exception e) {
            SysLogService.sysLog("failed to notify root user: " + e.getMessage());
        }
    }

    /**
     * 通知指定用户      * <p>
     * 根据 userSetting.notifyType 选择通知方式，支持 email/webhook/bark/gotify。
     * 通知前检查频率限制（NotifyLimitService）。
     *
     * @param userId     用户 ID
     * @param userEmail  用户邮箱
     * @param userSetting 用户设置（从 User.setting JSON 解析）
     * @param notify     通知内容
     * @throws Exception 通知发送失败
     */
    public void notifyUser(int userId, String userEmail, UserSettingDto userSetting, NotifyDto notify) throws Exception {
        String notifyType = userSetting.getNotifyType();
        if (notifyType == null || notifyType.isEmpty()) {
            notifyType = NOTIFY_TYPE_EMAIL;
        }

        // 检查通知频率限制
        boolean canSend = notifyLimitService.checkNotificationLimit(userId, notify.getType());
        if (!canSend) {
            log.debug("notification limit exceeded for user {} type {}", userId, notifyType);
            return;
        }

        switch (notifyType) {
            case NOTIFY_TYPE_EMAIL -> {
                // 优先使用设置中的通知邮箱，为空则使用用户默认邮箱
                String email = userSetting.getNotificationEmail();
                if (email == null || email.isEmpty()) {
                    email = userEmail;
                }
                if (email == null || email.isEmpty()) {
                    log.debug("user {} has no email, skip", userId);
                    return;
                }
                sendEmailNotify(email, notify);
            }
            case NOTIFY_TYPE_WEBHOOK -> {
                String webhookUrl = userSetting.getWebhookUrl();
                if (webhookUrl == null || webhookUrl.isEmpty()) {
                    log.debug("user {} has no webhook url, skip", userId);
                    return;
                }
                sendWebhookNotify(webhookUrl, userSetting.getWebhookSecret(), notify);
            }
            case NOTIFY_TYPE_BARK -> {
                String barkUrl = userSetting.getBarkUrl();
                if (barkUrl == null || barkUrl.isEmpty()) {
                    log.debug("user {} has no bark url, skip", userId);
                    return;
                }
                sendBarkNotify(barkUrl, notify);
            }
            case NOTIFY_TYPE_GOTIFY -> {
                String gotifyUrl = userSetting.getGotifyUrl();
                String gotifyToken = userSetting.getGotifyToken();
                if (gotifyUrl == null || gotifyUrl.isEmpty() || gotifyToken == null || gotifyToken.isEmpty()) {
                    log.debug("user {} has no gotify url or token, skip", userId);
                    return;
                }
                sendGotifyNotify(gotifyUrl, gotifyToken, userSetting.getGotifyPriority(), notify);
            }
            default -> log.debug("unknown notify type {} for user {}", notifyType, userId);
        }
    }

    // ======================== 各通知方式实现 ========================

    /**
     * 发送邮件通知      */
    private void sendEmailNotify(String email, NotifyDto notify) {
        String content = processPlaceholders(notify.getContent(), notify.getValues());
        EmailService.sendEmail(notify.getTitle(), email, content);
    }

    /**
     * 发送 Webhook 通知      * <p>
     * 通过 WebhookService 发送，附带签名验证。
     */
    private void sendWebhookNotify(String webhookUrl, String webhookSecret, NotifyDto notify) {
        String content = processPlaceholders(notify.getContent(), notify.getValues());
        Map<String, Object> payload = WebhookService.buildWebhookPayload(notify.getType(), 0, null);
        payload.put("title", notify.getTitle());
        payload.put("content", content);
        WebhookService.sendWebhook(webhookUrl, payload);
    }

    /**
     * 发送 Bark 推送通知      * <p>
     * 替换 barkUrl 中的 {{title}} 和 {{content}} 模板变量后发送 GET 请求。
     */
    private void sendBarkNotify(String barkUrl, NotifyDto notify) {
        String content = processPlaceholders(notify.getContent(), notify.getValues());

        // 替换模板变量
        String finalUrl = barkUrl.replace("{{title}}", URLEncoder.encode(notify.getTitle(), StandardCharsets.UTF_8));
        finalUrl = finalUrl.replace("{{content}}", URLEncoder.encode(content, StandardCharsets.UTF_8));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "OneAPI-Bark-Notify/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("bark request failed with status code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("failed to send bark request: {}", e.getMessage());
        }
    }

    /**
     * 发送 Gotify 推送通知      * <p>
     * 构造 JSON payload 发送 POST 请求到 Gotify 服务器。
     */
    private void sendGotifyNotify(String gotifyUrl, String gotifyToken, int priority, NotifyDto notify) {
        String content = processPlaceholders(notify.getContent(), notify.getValues());

        // 确保 URL 以 /message 结尾
        String finalUrl = gotifyUrl.replaceAll("/+$", "") + "/message?token=" + URLEncoder.encode(gotifyToken, StandardCharsets.UTF_8);

        // Gotify 优先级范围 0-10，超出范围使用默认值 5
        int actualPriority = (priority < 0 || priority > 10) ? 5 : priority;

        try {
            Map<String, Object> payload = Map.of(
                    "title", notify.getTitle(),
                    "message", content,
                    "priority", actualPriority
            );
            byte[] body = Convert.toJSONString(payload).getBytes(java.nio.charset.StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "YaoshuToken-Notify/1.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("gotify request failed with status code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.error("failed to send gotify request: {}", e.getMessage());
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 处理内容中的占位符 {{value}}      */
    private String processPlaceholders(String content, List<Object> values) {
        if (content == null || values == null || values.isEmpty()) {
            return content;
        }
        String result = content;
        for (Object value : values) {
            result = result.replaceFirst(java.util.regex.Pattern.quote(CONTENT_VALUE_PARAM),
                    java.util.regex.Matcher.quoteReplacement(String.valueOf(value)));
        }
        return result;
    }

    /**
     * 解析用户设置 JSON      */
    public UserSettingDto parseUserSetting(String settingJson) {
        if (settingJson == null || settingJson.isEmpty()) {
            return new UserSettingDto();
        }
        try {
            return Convert.toJavaBean(settingJson, UserSettingDto.class);
        } catch (Exception e) {
            log.debug("failed to parse user setting: {}", e.getMessage());
            return new UserSettingDto();
        }
    }

    // ======================== 内部 POJO ========================

    /**
     * 用户设置 DTO      */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserSettingDto {
        private String notifyType;
        private Double quotaWarningThreshold;
        private String webhookUrl;
        private String webhookSecret;
        private String notificationEmail;
        private String barkUrl;
        private String gotifyUrl;
        private String gotifyToken;
        private int gotifyPriority = 5;
        private boolean upstreamModelUpdateNotifyEnabled;
        /** Go: AcceptUnsetRatioModel — 是否接受未设置价格的模型 */
        private boolean acceptUnsetRatioModel;
        /** Go: RecordIpLog — 是否记录请求和错误日志 IP */
        private boolean recordIpLog;
        /** Go: SidebarModules — 左侧边栏模块配置（JSON） */
        private String sidebarModules;
        private String billingPreference;
        private String language;
    }

    /**
     * 通知 DTO      */
    @Data
    public static class NotifyDto {
        private final String type;
        private final String title;
        private final String content;
        private final List<Object> values;

        public NotifyDto(String type, String title, String content, List<Object> values) {
            this.type = type;
            this.title = title;
            this.content = content;
            this.values = values;
        }
    }
}
