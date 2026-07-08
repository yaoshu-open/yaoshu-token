package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务字符串处理服务  * <p>
 * 提供 Sunday 搜索算法、AC 自动机多模式匹配、字符串去重等业务字符串处理能力。
 * 注意：通用字符串工具（如判空、拼接、截取等）由 yue-library StrUtils 覆盖，不在此处重新实现。
 */
@Slf4j
public final class StrService {

    private StrService() {
    }

    // ======================== Sunday 搜索算法 ========================

    /**
     * Sunday 字符串匹配算法      *
     * @param text    待搜索文本
     * @param pattern 模式串
     * @return 是否匹配
     */
    public static boolean sundaySearch(String text, String pattern) {
        if (text == null || pattern == null || pattern.isEmpty()) return false;
        int n = text.length();
        int m = pattern.length();
        if (m > n) return false;

        // 构建偏移表
        Map<Character, Integer> offset = new HashMap<>();
        for (int i = 0; i < m; i++) {
            offset.put(pattern.charAt(i), m - i);
        }

        int i = 0;
        while (i <= n - m) {
            int j = 0;
            while (j < m && text.charAt(i + j) == pattern.charAt(j)) {
                j++;
            }
            if (j == m) return true;

            if (i + m < n) {
                char next = text.charAt(i + m);
                Integer val = offset.get(next);
                if (val != null) {
                    i += val;
                } else {
                    i += m + 1;
                }
            } else {
                break;
            }
        }
        return false;
    }

    // ======================== 字符串去重 ========================

    /**
     * 字符串数组去重（保持原顺序）      */
    public static List<String> removeDuplicate(List<String> list) {
        if (list == null || list.isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(list);
        result.addAll(seen);
        return result;
    }

    // ======================== AC 自动机 ========================

    /** AC 自动机缓存（按字典内容哈希 → AC 机器） */
    private static final ConcurrentHashMap<String, AcMachine> acCache = new ConcurrentHashMap<>();

    /**
     * AC 自动机节点
     */
    private static class AcNode {
        final Map<Character, AcNode> children = new HashMap<>();
        AcNode fail;
        List<String> outputs = new ArrayList<>();
    }

    /**
     * AC 自动机封装
     */
    private static class AcMachine {
        final AcNode root = new AcNode();

        void build(List<String> dict) {
            // 构建 Trie
            for (String word : dict) {
                String lower = word.toLowerCase().trim();
                if (lower.isEmpty()) continue;
                AcNode node = root;
                for (char c : lower.toCharArray()) {
                    node = node.children.computeIfAbsent(c, k -> new AcNode());
                }
                node.outputs.add(lower);
            }
            // 构建 fail 指针（BFS）
            Queue<AcNode> queue = new LinkedList<>();
            root.fail = root;
            for (AcNode child : root.children.values()) {
                child.fail = root;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                AcNode current = queue.poll();
                for (Map.Entry<Character, AcNode> entry : current.children.entrySet()) {
                    char c = entry.getKey();
                    AcNode child = entry.getValue();
                    AcNode failNode = current.fail;
                    while (failNode != root && !failNode.children.containsKey(c)) {
                        failNode = failNode.fail;
                    }
                    if (failNode.children.containsKey(c) && failNode.children.get(c) != child) {
                        child.fail = failNode.children.get(c);
                    } else {
                        child.fail = root;
                    }
                    child.outputs.addAll(child.fail.outputs);
                    queue.add(child);
                }
            }
        }

        /**
         * 多模式搜索，返回命中的词列表
         *
         * @param text           搜索文本
         * @param stopImmediately 命中即停止
         * @return 命中的敏感词列表
         */
        List<String> multiPatternSearch(String text, boolean stopImmediately) {
            List<String> hits = new ArrayList<>();
            AcNode node = root;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                while (node != root && !node.children.containsKey(c)) {
                    node = node.fail;
                }
                if (node.children.containsKey(c)) {
                    node = node.children.get(c);
                } else {
                    node = root;
                }
                if (!node.outputs.isEmpty()) {
                    hits.addAll(node.outputs);
                    if (stopImmediately) break;
                }
            }
            return hits;
        }

        /**
         * 多模式搜索（带位置），用于敏感词替换
         *
         * @return 命中列表，每项包含 [词, 起始位置]
         */
        List<Object[]> multiPatternSearchWithPos(String text, boolean stopImmediately) {
            List<Object[]> hits = new ArrayList<>();
            AcNode node = root;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                while (node != root && !node.children.containsKey(c)) {
                    node = node.fail;
                }
                if (node.children.containsKey(c)) {
                    node = node.children.get(c);
                } else {
                    node = root;
                }
                if (!node.outputs.isEmpty()) {
                    for (String word : node.outputs) {
                        int startPos = i - word.length() + 1;
                        hits.add(new Object[]{word, startPos});
                    }
                    if (stopImmediately) break;
                }
            }
            return hits;
        }
    }

    /**
     * 计算字典哈希键（用于缓存）      */
    private static String acKey(List<String> dict) {
        if (dict == null || dict.isEmpty()) return "";
        List<String> normalized = new ArrayList<>();
        for (String w : dict) {
            w = w.toLowerCase().trim();
            if (!w.isEmpty()) normalized.add(w);
        }
        if (normalized.isEmpty()) return "";
        Collections.sort(normalized);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String w : normalized) {
                md.update((byte) 0);
                md.update(w.getBytes(StandardCharsets.UTF_8));
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            return String.join("|", normalized);
        }
    }

    /**
     * 获取或构建 AC 自动机（带缓存）      */
    private static AcMachine getOrBuildAC(List<String> dict) {
        String key = acKey(dict);
        if (key.isEmpty()) return null;

        AcMachine cached = acCache.get(key);
        if (cached != null) return cached;

        AcMachine machine = new AcMachine();
        machine.build(dict);
        AcMachine existing = acCache.putIfAbsent(key, machine);
        return existing != null ? existing : machine;
    }

    /**
     * AC 自动机搜索（找到第一个匹配即停止，用于敏感词检测）      *
     * @param findText       搜索文本
     * @param dict           敏感词字典
     * @param stopImmediately 是否命中即停止
     * @return [是否命中, 命中的词列表]
     */
    public static Object[] acSearch(String findText, List<String> dict, boolean stopImmediately) {
        if (dict == null || dict.isEmpty()) return new Object[]{false, Collections.emptyList()};
        if (findText == null || findText.isEmpty()) return new Object[]{false, Collections.emptyList()};

        AcMachine machine = getOrBuildAC(dict);
        if (machine == null) return new Object[]{false, Collections.emptyList()};

        List<String> hits = machine.multiPatternSearch(findText, stopImmediately);
        if (!hits.isEmpty()) {
            return new Object[]{true, new ArrayList<>(new LinkedHashSet<>(hits))};
        }
        return new Object[]{false, Collections.emptyList()};
    }

    /**
     * AC 自动机搜索（带位置信息），用于敏感词替换      *
     * @param findText       搜索文本
     * @param dict           敏感词字典
     * @param stopImmediately 是否命中即停止
     * @return 命中的词+位置列表，每项为 [词, 起始位置]；未命中返回空列表
     */
    public static List<Object[]> acSearchWithPos(String findText, List<String> dict, boolean stopImmediately) {
        if (dict == null || dict.isEmpty()) return Collections.emptyList();
        if (findText == null || findText.isEmpty()) return Collections.emptyList();

        AcMachine machine = getOrBuildAC(dict);
        if (machine == null) return Collections.emptyList();

        return machine.multiPatternSearchWithPos(findText, stopImmediately);
    }

    // ======================== 日志预览与脱敏 ========================

    /** 日志内容截断上限（字符数） */
    private static final int LOCAL_LOG_CONTENT_LIMIT = 2048;

    /**
     * 日志内容预览截断      * <p>
     * 非 debug 模式下截断超长日志内容，避免日志膨胀。
     */
    public static String localLogPreview(String content) {
        if (yaoshu.token.constant.CommonConstants.debugEnabled || content.length() <= LOCAL_LOG_CONTENT_LIMIT) {
            return content;
        }
        return content.substring(0, LOCAL_LOG_CONTENT_LIMIT)
                + "... [truncated, original_length=" + content.length()
                + ", limit=" + LOCAL_LOG_CONTENT_LIMIT + "]";
    }

    /**
     * 规范化计费偏好值      * <p>
     * 合法值: subscription_first, wallet_first, subscription_only, wallet_only。
     * 不合法时默认返回 "subscription_first"。
     */
    public static String normalizeBillingPreference(String pref) {
        if (pref == null) return "subscription_first";
        String trimmed = pref.trim();
        return switch (trimmed) {
            case "subscription_first", "wallet_first", "subscription_only", "wallet_only" -> trimmed;
            default -> "subscription_first";
        };
    }

    /**
     * 邮箱脱敏（仅保留域名）      * <p>
     * "user@example.com" → "***@example.com"，空字符串 → "***masked***"
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "***masked***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex == -1) {
            return "***masked***";
        }
        return "***@" + email.substring(atIndex + 1);
    }

    // ======================== 工具方法 ========================

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
