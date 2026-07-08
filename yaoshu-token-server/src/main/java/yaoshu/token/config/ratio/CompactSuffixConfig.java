package yaoshu.token.config.ratio;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 紧凑后缀配置  */
public final class CompactSuffixConfig {

    private CompactSuffixConfig() {
    }

    private static final CopyOnWriteArrayList<String> compactSuffixes = new CopyOnWriteArrayList<>();

    public static List<String> getCompactSuffixes() {
        return List.copyOf(compactSuffixes);
    }

    public static void update(List<String> suffixes) {
        compactSuffixes.clear();
        if (suffixes != null) compactSuffixes.addAll(suffixes);
    }

    /** 检查模型后缀是否匹配任一紧凑后缀 */
    public static boolean matchesAny(String modelName) {
        if (modelName == null) return false;
        return compactSuffixes.stream().anyMatch(modelName::endsWith);
    }
}
