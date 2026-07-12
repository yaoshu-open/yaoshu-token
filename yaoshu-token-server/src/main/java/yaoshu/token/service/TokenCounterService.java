package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import yaoshu.token.pojo.dto.ClaudeDTO;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.OpenAIImageDTO;
import yaoshu.token.pojo.dto.OpenAIResponsesRequest;
import yaoshu.token.pojo.dto.TokenCountMeta;

/**
 * Token 计数服务  * <p>
 * 核心职责：跨模态（文本/图像/音频）Token 计数，驱动计费扣费。
 * <p>
 * 计数策略：OpenAI 文本模型 → 使用 {@link TokenizerService}（jtokkit 精确 BPE 编码）；
 * 非 OpenAI 模型 / tiktoken 不可用时 → 使用 {@link TokenEstimatorService}（字符级规则估算）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCounterService {

    private final TokenizerService tokenizerService;

    // ======================== OpenAI 文本模型识别 (Go common/model.go) ========================

    /** OpenAI 文本模型前缀列表 */
    private static final List<String> OPENAI_TEXT_MODELS = Arrays.asList(
            "gpt-", "o1", "o3", "o4", "chatgpt"
    );

    /**
     * 判断是否为 OpenAI 文本模型（可使用 tiktoken/jtokkit 精确计数）      */
    private static boolean isOpenAITextModel(String modelName) {
        if (modelName == null) return false;
        String lower = modelName.toLowerCase();
        for (String prefix : OPENAI_TEXT_MODELS) {
            if (lower.contains(prefix)) return true;
        }
        return false;
    }

    // ======================== Token 计数接口 ========================

    /**
     * 统计文本的 Token 数量      * <p>
     * 策略：OpenAI 文本模型 → jtokkit BPE 精确计数；其他模型 → 字符规则估算。
     *
     * @param text  待计数的文本
     * @param model 模型名（如 "gpt-4o"、"gemini-2.0-flash"）
     * @return Token 数量
     */
    public int countTextToken(String text, String model) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (isOpenAITextModel(model)) {
            return tokenizerService.countTokens(model, text);
        } else {
            // 非 OpenAI 模型使用估算，节省资源（与 Go 逻辑一致）
            return TokenEstimatorService.estimateTokenByModel(model, text);
        }
    }

    /**
     * 通用输入 Token 计数      * <p>
     * 支持 String / List&lt;String&gt; / List&lt;Object&gt; 多种输入类型。
     */
    @SuppressWarnings("unchecked")
    public int countTokenInput(Object input, String model) {
        if (input == null) return 0;
        if (input instanceof String text) {
            return countTextToken(text, model);
        }
        if (input instanceof List<?> list) {
            // 检查列表元素类型
            if (!list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof String) {
                    StringBuilder sb = new StringBuilder();
                    for (String s : (List<String>) list) {
                        sb.append(s);
                    }
                    return countTextToken(sb.toString(), model);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (Object item : list) {
                        sb.append(item);
                    }
                    return countTextToken(sb.toString(), model);
                }
            }
            return 0;
        }
        // 兜底：toString 后计数
        return countTextToken(input.toString(), model);
    }

    // ======================== 图像 / 音频 Token 计数 ========================

    /**
     * 图像 Token 计数（已知尺寸）。      * <p>
     * 实现 tile-based（4o/4.1/4.5/o1/o3 等）与 patch-based（4.1-mini/nano、o4-mini、gpt-5-mini/nano）
     * 两套算法，含 glm-4 特例与 low detail 短路。
     *
     * @param model  模型名
     * @param width  图像宽（像素），0 表示尺寸未知
     * @param height 图像高（像素），0 表示尺寸未知
     * @param detail 细节级别（low/high/auto，null/空按 high）
     * @return 图像 Token 数
     */
    public int countImageToken(String model, int width, int height, String detail) {
        String lowerModel = model == null ? "" : model.toLowerCase();

        // 特例：glm-4 系列固定值
        if (lowerModel.startsWith("glm-4")) {
            return 1047;
        }

        // 默认 4o/4.1/4.5 家族基准
        int baseTokens = 85;
        int tileTokens = 170;

        // patch-based 模型识别（32x32 patch，上限 1536，带倍率）
        boolean isPatchBased = false;
        double multiplier = 1.0;
        if (lowerModel.contains("gpt-4.1-mini")) {
            isPatchBased = true;
            multiplier = 1.62;
        } else if (lowerModel.contains("gpt-4.1-nano")) {
            isPatchBased = true;
            multiplier = 2.46;
        } else if (lowerModel.startsWith("o4-mini")) {
            isPatchBased = true;
            multiplier = 1.72;
        } else if (lowerModel.startsWith("gpt-5-mini")) {
            isPatchBased = true;
            multiplier = 1.62;
        } else if (lowerModel.startsWith("gpt-5-nano")) {
            isPatchBased = true;
            multiplier = 2.46;
        }

        // tile-based 模型的 base/tile token 取值
        if (!isPatchBased) {
            if (lowerModel.startsWith("gpt-4o-mini")) {
                baseTokens = 2833;
                tileTokens = 5667;
            } else if (lowerModel.startsWith("gpt-5-chat-latest")
                    || (lowerModel.startsWith("gpt-5") && !lowerModel.contains("mini") && !lowerModel.contains("nano"))) {
                baseTokens = 70;
                tileTokens = 140;
            } else if (lowerModel.startsWith("o1") || lowerModel.startsWith("o3") || lowerModel.startsWith("o1-pro")) {
                baseTokens = 75;
                tileTokens = 150;
            } else if (lowerModel.contains("computer-use-preview")) {
                baseTokens = 65;
                tileTokens = 129;
            } else if (lowerModel.contains("4.1") || lowerModel.contains("4o") || lowerModel.contains("4.5")) {
                baseTokens = 85;
                tileTokens = 170;
            }
        }

        // low detail 短路（仅 tile-based）
        if ("low".equalsIgnoreCase(detail) && !isPatchBased) {
            return baseTokens;
        }

        // 尺寸未知时按 3 倍 base 估算
        if (width <= 0 || height <= 0) {
            return 3 * baseTokens;
        }

        if (isPatchBased) {
            return computePatchBasedImageToken(width, height, multiplier);
        }
        return computeTileBasedImageToken(width, height, baseTokens, tileTokens);
    }

    /** patch-based 图像 Token 计算（32x32 patch，上限 1536） */
    private int computePatchBasedImageToken(int width, int height, double multiplier) {
        int rawPatchesW = ceilDiv(width, 32);
        int rawPatchesH = ceilDiv(height, 32);
        int rawPatches = rawPatchesW * rawPatchesH;
        if (rawPatches <= 1536) {
            return (int) Math.round(rawPatches * multiplier);
        }
        // 超过上限：等比缩放
        double area = (double) width * height;
        double r = Math.sqrt((double) (32 * 32 * 1536) / area);
        double wScaled = width * r;
        double hScaled = height * r;
        // 调整以适配缩放后整数 patch
        double adjW = Math.floor(wScaled / 32.0) / (wScaled / 32.0);
        double adjH = Math.floor(hScaled / 32.0) / (hScaled / 32.0);
        double adj = Math.min(adjW, adjH);
        if (!Double.isNaN(adj) && adj > 0) {
            r = r * adj;
        }
        wScaled = width * r;
        hScaled = height * r;
        int imageTokens = (int) (Math.ceil(wScaled / 32.0) * Math.ceil(hScaled / 32.0));
        if (imageTokens > 1536) {
            imageTokens = 1536;
        }
        return (int) Math.round(imageTokens * multiplier);
    }

    /** tile-based 图像 Token 计算（512px tile）*/
    private int computeTileBasedImageToken(int width, int height, int baseTokens, int tileTokens) {
        // 步骤1：限制在 2048x2048 内
        double maxSide = Math.max(width, height);
        double fitScale = maxSide > 2048 ? maxSide / 2048.0 : 1.0;
        int fitW = (int) Math.round(width / fitScale);
        int fitH = (int) Math.round(height / fitScale);

        // 步骤2：缩放使最短边恰为 768
        double minSide = Math.min(fitW, fitH);
        if (minSide == 0) {
            return baseTokens;
        }
        double shortScale = 768.0 / minSide;
        int finalW = (int) Math.round(fitW * shortScale);
        int finalH = (int) Math.round(fitH * shortScale);

        // 统计 512px tile 数
        int tilesW = ceilDiv(finalW, 512);
        int tilesH = ceilDiv(finalH, 512);
        int tiles = tilesW * tilesH;
        return tiles * tileTokens + baseTokens;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    /**
     * 音频 Token 计数（通用估算）。      * <p>
     * 注意：Realtime 会话的输入/输出音频应使用 {@link #countAudioTokenInput} / {@link #countAudioTokenOutput}，
     * 它们 的不同费率公式。      */
    public int countAudioToken(double durationSeconds) {
        return (int) Math.ceil(Math.ceil(durationSeconds) / 60.0 * 1000);
    }

    /**
     * Realtime 输入音频 Token 计数。      * <p>
     * Go 公式：int(duration / 60 * 100 / 0.06)
     */
    public int countAudioTokenInput(double durationSeconds) {
        return (int) (durationSeconds / 60.0 * 100.0 / 0.06);
    }

    /**
     * Realtime 输出音频 Token 计数。      * <p>
     * Go 公式：int(duration / 60 * 200 / 0.24)
     */
    public int countAudioTokenOutput(double durationSeconds) {
        return (int) (durationSeconds / 60.0 * 200.0 / 0.24);
    }

    /**
     * 视频 Token 计数（骨架）      */
    public int countVideoToken() {
        return 4096 * 2;
    }

    /**
     * 文件 Token 计数（骨架）      */
    public int countFileToken() {
        return 4096;
    }

    // ======================== 快速计费 Token 元数据（CountToken 关闭时的备选路径） ========================

    /**
     * 快速构建计费用 TokenCountMeta（不解析消息文本）      * <p>
     * 当 CountToken 和敏感词检查均关闭时，跳过完整的 GetTokenCountMeta()（避免构建庞大的 CombineText），
     * 仅从请求体中提取 max_tokens 作为计费估算依据。
     *
     * @param request 请求对象（GeneralOpenAIRequest / OpenAIResponsesRequest / ClaudeDTO / OpenAIImageDTO / Map）
     * @return TokenCountMeta（仅含 maxTokens 字段，CombineText 为空）
     */
    @SuppressWarnings("unchecked")
    public TokenCountMeta fastTokenCountMetaForPricing(Object request) {
        TokenCountMeta meta = new TokenCountMeta();
        meta.setTokenType("tokenizer");

        if (request == null) return meta;

        if (request instanceof GeneralOpenAIRequest r) {
            int maxCompletion = r.getMaxCompletionTokens() != null ? r.getMaxCompletionTokens() : 0;
            int maxT = r.getMaxTokens() != null ? r.getMaxTokens() : 0;
            meta.setMaxTokens(Math.max(maxCompletion, maxT));
            // 估算 prompt tokens（从 messages 提取文本粗估）
            meta.setEstimatedPromptTokens(estimatePromptTokensFromMessages(r.getMessages(), r.getModel()));
        } else if (request instanceof OpenAIResponsesRequest r) {
            meta.setMaxTokens(r.getMaxOutputTokens() != null ? r.getMaxOutputTokens() : 0);
        } else if (request instanceof ClaudeDTO.ClaudeRequest r) {
            meta.setMaxTokens(r.getMaxTokens() != null ? r.getMaxTokens() : 0);
            // Claude 格式：从 messages 估算
            meta.setEstimatedPromptTokens(estimatePromptTokensFromClaudeMessages(r.getMessages(), r.getModel()));
        } else if (request instanceof OpenAIImageDTO) {
            // 图像请求 pricing 依赖 ImagePriceRatio，Java 侧 OpenAIImageDTO 当前无 getTokenCountMeta()
            // 返回空 meta（CombineText 留空），由调用方按图像默认计费处理
        } else if (request instanceof Map m) {
            // 兜底：从 Map 中提取 max_tokens
            Object v = m.get("max_tokens");
            if (v instanceof Number n) meta.setMaxTokens(n.intValue());
            else {
                v = m.get("max_completion_tokens");
                if (v instanceof Number n) meta.setMaxTokens(n.intValue());
            }
            // 从 Map 中估算 prompt tokens
            Object messages = m.get("messages");
            List<?> messageList = messages instanceof List<?> mList ? mList : List.of();
            Object model = m.get("model");
            if (model instanceof String modelName && !modelName.isEmpty() && !messageList.isEmpty()) {
                // 有模型名时用精确计数
                meta.setEstimatedPromptTokens(countTokenInput(extractTextFromMessages(messageList), modelName));
            } else if (!messageList.isEmpty()) {
                // 无模型名时用字符级估算
                meta.setEstimatedPromptTokens(estimatePromptTokensFromMessageList(messageList));
            }
        }
        // 其他类型：最佳努力，CombineText 留空避免大内存分配
        return meta;
    }

    /**
     * 从 OpenAI messages 列表估算 prompt tokens。
     * 粗估策略：拼接所有 message content 文本，用字符级估算（≈4字符/token）。
     */
    @SuppressWarnings("unchecked")
    private int estimatePromptTokensFromMessages(List<?> messages, String model) {
        if (messages == null || messages.isEmpty()) return 0;
        String text = extractTextFromMessages(messages);
        if (text.isEmpty()) return 0;
        if (model != null && !model.isEmpty()) {
            return countTextToken(text, model);
        }
        // 无模型名时用通用估算（≈4字符/token）
        return text.length() / 4;
    }

    /**
     * 从 Claude messages 列表估算 prompt tokens。
     */
    private int estimatePromptTokensFromClaudeMessages(Object messages, String model) {
        if (messages == null) return 0;
        if (messages instanceof List<?> mList) {
            return estimatePromptTokensFromMessages(mList, model);
        }
        return 0;
    }

    /**
     * 从消息列表提取纯文本（兼容 OpenAI 格式的 content 字段）。
     */
    @SuppressWarnings("unchecked")
    private String extractTextFromMessages(List<?> messages) {
        StringBuilder sb = new StringBuilder();
        for (Object msg : messages) {
            if (msg instanceof Map<?, ?> m) {
                Object content = m.get("content");
                if (content instanceof String s) {
                    sb.append(s);
                } else if (content instanceof List<?> parts) {
                    for (Object part : parts) {
                        if (part instanceof Map<?, ?> p && p.get("text") instanceof String t) {
                            sb.append(t);
                        }
                    }
                }
                // 角色标识也计入（role:assistant/user 等约消耗几个 token）
                Object role = m.get("role");
                if (role instanceof String r) {
                    sb.append(r);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 从消息列表估算 prompt tokens（通用版，无模型名）。
     */
    private int estimatePromptTokensFromMessageList(List<?> messages) {
        String text = extractTextFromMessages(messages);
        return text.isEmpty() ? 0 : text.length() / 4;
    }
}
