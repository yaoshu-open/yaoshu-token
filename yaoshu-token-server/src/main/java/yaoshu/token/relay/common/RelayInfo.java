package yaoshu.token.relay.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.web.socket.WebSocketSession;
import yaoshu.token.pojo.dto.*;

import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Relay 核心上下文，贯穿整个转发请求生命周期  * <p>
 * 持有用户/Token/渠道/计费/请求/响应等所有转发链路所需信息。
 * Go 通过嵌入结构体（Go struct embedding）实现字段组合，Java 使用组合字段替代。
 */
@Data
@Accessors(chain = true)
public class RelayInfo {

    // ======================== Token & 用户 ========================

    private int tokenId;
    private String tokenKey;
    private String tokenGroup;
    private int userId;
    /** 当前使用的分组（跨分组重试时会变动） */
    private String usingGroup;
    /** 用户所在分组 */
    private String userGroup;
    private boolean tokenUnlimited;

    // ======================== 时间追踪 ========================

    private LocalDateTime startTime;
    private LocalDateTime firstResponseTime;
    private boolean isFirstResponse = true;

    // ======================== 请求属性 ========================

    private boolean isStream;
    private boolean isGeminiBatchEmbedding;
    private boolean isPlayground;
    private boolean usePrice;
    private int relayMode;
    private String originModelName;
    private String requestURLPath;
    private Map<String, String> requestHeaders;
    private boolean shouldIncludeUsage;
    private boolean disablePing;
    private String inputAudioFormat;
    private String outputAudioFormat;
    private boolean audioUsage;
    private boolean isFirstRequest = true;
    private String reasoningEffort;
    private boolean isClaudeBetaQuery;
    private boolean isChannelTest;

    // ======================== 用户设置与配额 ========================

    private UserSetting userSetting;
    private String userEmail;
    private long userQuota;
    private String relayFormat;
    private int sendResponseCount;
    private int receivedResponseCount;
    private int finalPreConsumedQuota;
    /** 为 true 时禁用 BillingSession 信任额度旁路，强制预扣全额（异步任务使用） */
    private boolean forcePreConsume;

    // ======================== 计费 ========================

    /** BillingSession 生命周期管理接口，免费模型时为 null */
    private transient BillingSettler billing;
    /** 计费来源："" 或 "wallet" = 钱包，"subscription" = 订阅 */
    private String billingSource;
    private int subscriptionId;
    private long subscriptionPreConsumed;
    private long subscriptionPostDelta;
    private int subscriptionPlanId;
    private String subscriptionPlanTitle;
    /** 请求幂等 ID */
    private String requestId;
    private long subscriptionAmountTotal;
    private long subscriptionAmountUsedAfterPreConsume;

    // ======================== 重试 ========================

    private int retryIndex;
    private RelayException lastError;

    // ======================== Header 覆写 ========================

    private Map<String, Object> runtimeHeadersOverride;
    private boolean useRuntimeHeadersOverride;
    private List<String> paramOverrideAudit;

    /** 上游请求体字节大小（BodyStorage 包装时设置），0 表示由 net/http 自动决定 */
    private long upstreamRequestBodySize;

    // ======================== 价格数据 ========================

    private PriceData priceData;

    // ======================== 分层计费快照 ========================

    /** 分层计费规则快照（预扣时冻结），非 null 时计费模式为 tiered_expr */
    @Getter @Setter
    private yaoshu.token.billingexpr.BillingSnapshot tieredBillingSnapshot;
    @Getter @Setter
    private yaoshu.token.billingexpr.RequestInput billingRequestInput;

    // ======================== 请求对象 ========================

    /** AI API 请求体（OpenAI / Claude / Gemini 等）*/
    @Getter @Setter
    private Object request;

    // ======================== 请求格式转换链 ========================

    private List<String> requestConversionChain;
    /** 最终上游请求格式（由 adaptor 显式设置）；为空时回退到转换链最后一项 */
    private String finalRequestRelayFormat;

    // ======================== 流状态 ========================

    private StreamStatus streamStatus;

    // ======================== Go 嵌入结构体 —— 组合字段 ========================

    /* ThinkingContentInfo */
    private boolean isFirstThinkingContent = true;
    private boolean sendLastThinkingContent;
    private boolean hasSentThinkingContent;

    /* TokenCountMeta */
    private int estimatePromptTokens;

    /* ClaudeConvertInfo */
    private String lastMessagesType = "none";
    private int claudeIndex;
    private Usage claudeUsage = new Usage();
    private String claudeFinishReason;
    
    // ======================== 扩展数据（SPI 扩展点） ========================
    
    /**
     * 扩展数据存储，供 SPI 实现存储自定义状态（如免费额度标记等）
     * <p>
     * 开源版默认实现不使用此字段，SPI 扩展实现可通过 AOP 在此存储额外状态
     */
    private Map<String, Object> extraData;
    private boolean claudeDone;
    private int toolCallBaseIndex;
    private int toolCallMaxIndexOffset;

    /* RerankerInfo */
    private List<Object> rerankerDocuments;
    private boolean rerankerReturnDocuments;

    /* ChannelMeta */
    private int channelType;
    private int channelId;
    private boolean channelIsMultiKey;
    private int channelMultiKeyIndex;
    private String channelBaseUrl;
    private int apiType;
    private String apiVersion;
    private String apiKey;
    private String organization;
    private long channelCreateTime;
    private Map<String, Object> paramOverride;
    private Map<String, Object> headersOverride;
    private ChannelSettingsDTO channelSetting;
    private ChannelOtherSettingsDTO channelOtherSettings;
    private String upstreamModelName;
    private boolean isModelMapped;
    /** 是否支持流式选项 */
    private boolean supportStreamOptions;

    /* TaskRelayInfo */
    private String taskAction;
    private String originTaskID;
    private String publicTaskID;
    private boolean taskConsumeQuota;
    private Object lockedChannel; // model.Channel

    // ======================== 请求上下文（transient — 不入 JSON 序列化） ========================

    /** 客户端原始请求头（由 Handler 在调用 adaptor.doRequest 前设置） */
    @Getter @Setter
    private transient Map<String, String> clientHeaders;

    /** HttpServletResponse（由 Handler 在调用 adaptor.doResponse 前设置） */
    @Getter @Setter
    private transient HttpServletResponse response;

    /** Realtime 客户端 WebSocket 会话 */
    @Getter @Setter
    private transient WebSocketSession clientWsSession;

    /** Realtime 上游 WebSocket 会话（JDK 17 客户端） */
    @Getter @Setter
    private transient WebSocket targetWsSession;

    /** Realtime 会话累计 usage（使用完整 DTO Usage 以支持音频/详细 token 分解） */
    @Getter @Setter
    private transient yaoshu.token.pojo.dto.Usage realtimeUsage = new yaoshu.token.pojo.dto.Usage();

    /** Realtime 当前响应周期待结算 usage */
    @Getter @Setter
    private transient yaoshu.token.pojo.dto.Usage realtimePendingUsage = new yaoshu.token.pojo.dto.Usage();

    /** Realtime session.update 带来的工具定义 */
    @Getter @Setter
    private transient List<Object> realtimeTools;

    /** Realtime 连接关闭保护，避免重复结算/重复 close */
    @Getter @Setter
    private transient AtomicBoolean realtimeClosed = new AtomicBoolean(false);

    /** 性能采样用 outputTokens（计费结算时写入，供 RelayController 成功路径采样读取） */
    @Getter @Setter
    private transient long perfOutputTokens;

    // ======================== 工厂方法 ========================

    /**
     * 获取最终请求格式      */
    public String getFinalRequestRelayFormat() {
        if (finalRequestRelayFormat != null && !finalRequestRelayFormat.isEmpty()) {
            return finalRequestRelayFormat;
        }
        if (requestConversionChain != null && !requestConversionChain.isEmpty()) {
            return requestConversionChain.get(requestConversionChain.size() - 1);
        }
        return relayFormat;
    }

    /**
     * 初始化请求转换链      */
    public void initRequestConversionChain() {
        if (requestConversionChain != null && !requestConversionChain.isEmpty()) {
            return;
        }
        if (relayFormat == null || relayFormat.isEmpty()) {
            return;
        }
        requestConversionChain = new ArrayList<>();
        requestConversionChain.add(relayFormat);
    }

    /**
     * 追加请求转换      */
    public void appendRequestConversion(String format) {
        if (format == null || format.isEmpty()) {
            return;
        }
        if (requestConversionChain == null || requestConversionChain.isEmpty()) {
            requestConversionChain = new ArrayList<>();
            requestConversionChain.add(format);
            return;
        }
        String last = requestConversionChain.get(requestConversionChain.size() - 1);
        if (!last.equals(format)) {
            requestConversionChain.add(format);
        }
    }

    /**
     * 设置首次响应时间      */
    public void setFirstResponseTime() {
        if (isFirstResponse) {
            firstResponseTime = LocalDateTime.now();
            isFirstResponse = false;
        }
    }

    /**
     * 是否已发送过响应      */
    public boolean hasSendResponse() {
        return firstResponseTime != null && startTime != null && firstResponseTime.isAfter(startTime);
    }

    // ======================== 嵌套类型 ========================

    /**
     * 用法统计      */
    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("completion_tokens")
        private int completionTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;
    }

    /**
     * 用户设置      */
    @Data
    public static class UserSetting {
        private boolean shouldCheckPrompt;
        private boolean shouldCheckSensitive;
        private boolean shouldCheckQuota;
    }

    /**
     * Task 中转信息      */
    @Data
    public static class TaskRelayInfo {
        private String action;
        private String originTaskID;
        private String publicTaskID;
        private boolean consumeQuota;
        private Object lockedChannel;
    }

    /**
     * Task 提交请求体      */
    @Data
    public static class TaskSubmitReq {
        private String prompt;
        private String model;
        private String mode;
        private String image;
        private List<String> images;
        private String size;
        private int duration;
        private String seconds;
        @JsonProperty("input_reference")
        private String inputReference;
        private Map<String, Object> metadata;

        public String getPrompt() { return prompt; }
        public boolean hasImage() { return images != null && !images.isEmpty(); }
    }
}
