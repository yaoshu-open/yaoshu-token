package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 敏感词检测服务  * <p>
 * 提供消息内容检测、文本检测、敏感词替换等能力。
 * 底层 AC 自动机由 {@link StrService} 提供。
 */
@Slf4j
public final class SensitiveService {

    private SensitiveService() {
    }

    /** 敏感词替换符 */
    private static final String REPLACE_MASK = "**###**";

    // ======================== 消息级检测 ========================

    /**
     * 检查消息列表是否包含敏感词      *
     * @param messages 消息列表（每项为 Map，含 content 字段）
     * @param dict     敏感词字典
     * @return [命中的词列表, null=无, error=有]
     */
    @SuppressWarnings("unchecked")
    public static Object[] checkSensitiveMessages(List<Map<String, Object>> messages, List<String> dict) {
        if (messages == null || messages.isEmpty()) return new Object[]{null, null};
        if (dict == null || dict.isEmpty()) return new Object[]{null, null};

        for (Map<String, Object> message : messages) {
            // 解析 content 数组（Go message.ParseContent()）
            Object contentObj = message.get("content");
            List<Map<String, Object>> contentItems = null;
            if (contentObj instanceof List) {
                contentItems = (List<Map<String, Object>>) contentObj;
            }
            // 简单文本：直接从 text 字段检测
            Object textObj = message.get("content");
            if (textObj instanceof String text) {
                Object[] result = sensitiveWordContains(text, dict);
                if ((boolean) result[0]) {
                    return new Object[]{(List<String>) result[1], "sensitive words detected"};
                }
                continue;
            }
            if (contentItems == null) continue;

            for (Map<String, Object> item : contentItems) {
                String type = (String) item.get("type");
                if ("image_url".equals(type)) {
                    String imageUrl = extractImageUrl(item.get("image_url"));
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        continue;
                    }
                    Object[] result = sensitiveWordContains(imageUrl, dict);
                    if ((boolean) result[0]) {
                        return new Object[]{(List<String>) result[1], "sensitive words detected"};
                    }
                    continue;
                }
                String text = (String) item.get("text");
                if (text == null || text.isEmpty()) continue;

                Object[] result = sensitiveWordContains(text, dict);
                if ((boolean) result[0]) {
                    return new Object[]{(List<String>) result[1], "sensitive words detected"};
                }
            }
        }
        return new Object[]{null, null};
    }

    /**
     * 检查文本是否包含敏感词      */
    public static Object[] checkSensitiveText(String text, List<String> dict) {
        return sensitiveWordContains(text, dict);
    }

    // ======================== 核心检测/替换 ========================

    /**
     * 检查文本是否包含敏感词      */
    @SuppressWarnings("unchecked")
    public static Object[] sensitiveWordContains(String text, List<String> sensitiveWords) {
        if (sensitiveWords == null || sensitiveWords.isEmpty()) {
            return new Object[]{false, Collections.emptyList()};
        }
        if (text == null || text.isEmpty()) {
            return new Object[]{false, Collections.emptyList()};
        }
        String checkText = text.toLowerCase();
        return StrService.acSearch(checkText, sensitiveWords, true);
    }

    /**
     * 敏感词替换（用 **###** 替换）      *
     * @param text             原始文本
     * @param sensitiveWords   敏感词字典
     * @param returnImmediately 是否命中即返回
     * @return [是否包含, 命中的词列表, 替换后的文本]
     */
    @SuppressWarnings("unchecked")
    public static Object[] sensitiveWordReplace(String text, List<String> sensitiveWords, boolean returnImmediately) {
        if (sensitiveWords == null || sensitiveWords.isEmpty()) {
            return new Object[]{false, Collections.emptyList(), text};
        }
        if (text == null || text.isEmpty()) {
            return new Object[]{false, Collections.emptyList(), text};
        }

        // 小写文本用于 AC 搜索（保留原始文本用于替换）
        String checkText = text.toLowerCase();
        List<Object[]> hits = StrService.acSearchWithPos(checkText, sensitiveWords, returnImmediately);

        if (hits.isEmpty()) {
            return new Object[]{false, Collections.emptyList(), text};
        }

        // 按位置排序，执行替换
        hits.sort(Comparator.comparingInt(h -> (int) h[1]));

        List<String> hitWords = new ArrayList<>();
        StringBuilder builder = new StringBuilder(text.length() + hits.size() * REPLACE_MASK.length());
        int lastPos = 0;
        int skippedOverlapEnd = 0;

        for (Object[] hit : hits) {
            String word = (String) hit[0];
            int pos = (int) hit[1];
            int endPos = pos + word.length();

            // 跳过与上一命中位置重叠的部分
            if (pos < skippedOverlapEnd) continue;

            builder.append(text, lastPos, pos);
            builder.append(REPLACE_MASK);
            lastPos = endPos;
            skippedOverlapEnd = endPos;
            hitWords.add(word);
        }
        builder.append(text, lastPos, text.length());

        return new Object[]{true, hitWords, builder.toString()};
    }

    @SuppressWarnings("unchecked")
    private static String extractImageUrl(Object imageUrlObj) {
        if (imageUrlObj instanceof String imageUrl) {
            return imageUrl;
        }
        if (imageUrlObj instanceof Map<?, ?> imageUrlMap) {
            Object url = imageUrlMap.get("url");
            if (url instanceof String imageUrl) {
                return imageUrl;
            }
        }
        return null;
    }
}
