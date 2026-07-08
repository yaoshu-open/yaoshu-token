package yaoshu.token.config.ratio;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 补全倍率配置  * 以及 GetCompletionRatio / GetCompletionRatioInfo / getHardcodedCompletionModelRatio 函数族。
 * <p>
 * 启动期注入默认值，OptionService 在 {@code CompletionRatio} key 写库后通过 update 全量覆盖。
 * <p>
 * 补全倍率有 3 类来源（按 Go 优先级）：
 * <ol>
 *   <li>包含 "/" 的 submodel 路径名（如 "deepseek-ai/DeepSeek-R1"）→ 优先读 completionRatioMap</li>
 *   <li>硬编码规则（{@link #getHardcodedCompletionModelRatio}）锁定的模型 → 直接返回硬编码值</li>
 *   <li>completionRatioMap 显式配置 → 返回配置值</li>
 *   <li>硬编码兜底（locked=false 的硬编码默认值，如 1.0 / 4.0）</li>
 * </ol>
 */
public final class CompletionRatioConfig {

    private CompletionRatioConfig() {
    }

    /** 默认补全倍率，与 Go defaultCompletionRatio 严格对齐 */
    private static final Map<String, Double> DEFAULT_COMPLETION_RATIO = Map.of(
            "gpt-4-gizmo-*", 2.0,
            "gpt-4o-gizmo-*", 3.0,
            "gpt-4-all", 2.0,
            "gpt-image-1", 8.0
    );

    private static final ConcurrentHashMap<String, Double> completionRatioMap = new ConcurrentHashMap<>(DEFAULT_COMPLETION_RATIO);

    /**
     * 获取补全倍率。      * <p>
     * 流程：
     * <pre>
     *   formatMatchingModelName(name)
     *     ↓
     *   if name 含 "/" → 先查 completionRatioMap，命中返回
     *     ↓
     *   getHardcodedCompletionModelRatio(name) → 返回 (ratio, contain)
     *     ↓
     *   if contain → 返回 hardcoded ratio
     *     ↓
     *   completionRatioMap 命中 → 返回配置 ratio
     *     ↓
     *   兜底返回 hardcoded ratio
     * </pre>
     */
    public static double getCompletionRatio(String modelName) {
        if (modelName == null) return 1.0;
        String name = ModelRatioConfig.formatMatchingModelName(modelName);

        // 1. 包含 "/" 的 submodel 路径名优先读 map
        if (name.contains("/")) {
            Double ratio = completionRatioMap.get(name);
            if (ratio != null) return ratio;
        }

        // 2. 硬编码规则
        HardcodedRatio hardcoded = getHardcodedCompletionModelRatio(name);
        if (hardcoded.contain) {
            return hardcoded.ratio;
        }

        // 3. completionRatioMap 显式配置覆盖
        Double mapRatio = completionRatioMap.get(name);
        if (mapRatio != null) return mapRatio;

        // 4. 兜底：硬编码 ratio（locked=false 的硬编码默认值）
        return hardcoded.ratio;
    }

    /** 补全倍率信息 */
    public static final class CompletionRatioInfo {
        public final double ratio;
        public final boolean locked;

        public CompletionRatioInfo(double ratio, boolean locked) {
            this.ratio = ratio;
            this.locked = locked;
        }
    }

    /**
     * 获取补全倍率信息（含 locked 标记）。      */
    public static CompletionRatioInfo getCompletionRatioInfo(String modelName) {
        if (modelName == null) return new CompletionRatioInfo(1.0, false);
        String name = ModelRatioConfig.formatMatchingModelName(modelName);

        if (name.contains("/")) {
            Double ratio = completionRatioMap.get(name);
            if (ratio != null) return new CompletionRatioInfo(ratio, false);
        }

        HardcodedRatio hardcoded = getHardcodedCompletionModelRatio(name);
        if (hardcoded.contain) {
            // Go 语义：硬编码 contain=true 即 Locked=true
            return new CompletionRatioInfo(hardcoded.ratio, true);
        }

        Double mapRatio = completionRatioMap.get(name);
        if (mapRatio != null) return new CompletionRatioInfo(mapRatio, false);

        return new CompletionRatioInfo(hardcoded.ratio, false);
    }

    /** 硬编码倍率结果（携带是否 locked 标记） */
    private static final class HardcodedRatio {
        final double ratio;
        /** true 时表示该模型的补全倍率被硬编码锁定，不允许 completionRatioMap 覆盖 */
        final boolean contain;

        HardcodedRatio(double ratio, boolean contain) {
            this.ratio = ratio;
            this.contain = contain;
        }
    }

    /**
     * 硬编码补全倍率规则。      * <p>
     * 严格按 Go 函数行为翻译，分支顺序与 Go 源对齐。返回值二元组：(ratio, locked)。
     */
    private static HardcodedRatio getHardcodedCompletionModelRatio(String name) {
        // 保留模型 (-all / -gizmo-*)
        boolean isReservedModel = name.endsWith("-all") || name.endsWith("-gizmo-*");
        if (isReservedModel) {
            return new HardcodedRatio(2.0, false);
        }

        // gpt- 前缀
        if (name.startsWith("gpt-")) {
            if (name.startsWith("gpt-4o")) {
                if ("gpt-4o-2024-05-13".equals(name)) {
                    return new HardcodedRatio(3.0, true);
                }
                if (name.startsWith("gpt-4o-mini-tts")) {
                    return new HardcodedRatio(20.0, false);
                }
                return new HardcodedRatio(4.0, false);
            }
            // gpt-5 系列
            if (name.startsWith("gpt-5")) {
                if (name.startsWith("gpt-5.5")) {
                    return new HardcodedRatio(6.0, true);
                }
                if (name.startsWith("gpt-5.4")) {
                    if (name.startsWith("gpt-5.4-nano")) {
                        return new HardcodedRatio(6.25, true);
                    }
                    return new HardcodedRatio(6.0, true);
                }
                return new HardcodedRatio(8.0, true);
            }
            // gpt-4.5-preview
            if (name.startsWith("gpt-4.5-preview")) {
                return new HardcodedRatio(2.0, true);
            }
            // gpt-4-turbo / gpt-4-1106 / gpt-4-1105
            if (name.startsWith("gpt-4-turbo") || name.endsWith("gpt-4-1106") || name.endsWith("gpt-4-1105")) {
                return new HardcodedRatio(3.0, true);
            }
            // 默认 gpt-4 系列倍率为 2
            return new HardcodedRatio(2.0, false);
        }

        // o1 / o3 推理模型
        if (name.startsWith("o1") || name.startsWith("o3")) {
            return new HardcodedRatio(4.0, true);
        }
        // chatgpt-4o-latest
        if ("chatgpt-4o-latest".equals(name)) {
            return new HardcodedRatio(3.0, true);
        }

        // Claude 系列
        if (name.contains("claude-3")) {
            return new HardcodedRatio(5.0, true);
        }
        if (name.contains("claude-sonnet-4") || name.contains("claude-opus-4") || name.contains("claude-haiku-4")) {
            return new HardcodedRatio(5.0, true);
        }

        // gpt-3.5
        if (name.startsWith("gpt-3.5")) {
            if ("gpt-3.5-turbo".equals(name) || name.endsWith("0125")) {
                return new HardcodedRatio(3.0, true);
            }
            if (name.endsWith("1106")) {
                return new HardcodedRatio(2.0, true);
            }
            return new HardcodedRatio(4.0 / 3.0, true);
        }
        // mistral
        if (name.startsWith("mistral-")) {
            return new HardcodedRatio(3.0, true);
        }

        // gemini 系列
        if (name.startsWith("gemini-")) {
            if (name.startsWith("gemini-1.5")) {
                return new HardcodedRatio(4.0, true);
            } else if (name.startsWith("gemini-2.0")) {
                return new HardcodedRatio(4.0, true);
            } else if (name.startsWith("gemini-2.5-pro")) {
                return new HardcodedRatio(8.0, false);
            } else if (name.startsWith("gemini-2.5-flash")) {
                if (name.startsWith("gemini-2.5-flash-preview")) {
                    if (name.endsWith("-nothinking")) {
                        return new HardcodedRatio(4.0, false);
                    }
                    return new HardcodedRatio(3.5 / 0.15, false);
                }
                if (name.startsWith("gemini-2.5-flash-lite")) {
                    return new HardcodedRatio(4.0, false);
                }
                return new HardcodedRatio(2.5 / 0.3, false);
            } else if (name.startsWith("gemini-robotics-er-1.5")) {
                return new HardcodedRatio(2.5 / 0.3, false);
            } else if (name.startsWith("gemini-3-pro")) {
                if (name.startsWith("gemini-3-pro-image")) {
                    return new HardcodedRatio(60.0, false);
                }
                return new HardcodedRatio(6.0, false);
            }
            return new HardcodedRatio(4.0, false);
        }

        // command 系列
        if (name.startsWith("command")) {
            switch (name) {
                case "command-r":
                    return new HardcodedRatio(3.0, true);
                case "command-r-plus":
                    return new HardcodedRatio(5.0, true);
                case "command-r-08-2024":
                    return new HardcodedRatio(4.0, true);
                case "command-r-plus-08-2024":
                    return new HardcodedRatio(4.0, true);
                default:
                    return new HardcodedRatio(4.0, false);
            }
        }

        // ERNIE 系列（仅官方上 4 倍率，开源模型供应商自行定价不强制对齐）
        if (name.startsWith("ERNIE-Speed-")) {
            return new HardcodedRatio(2.0, true);
        }
        if (name.startsWith("ERNIE-Lite-")) {
            return new HardcodedRatio(2.0, true);
        }
        if (name.startsWith("ERNIE-Character")) {
            return new HardcodedRatio(2.0, true);
        }
        if (name.startsWith("ERNIE-Functions")) {
            return new HardcodedRatio(2.0, true);
        }

        // Llama 系列
        switch (name) {
            case "llama2-70b-4096":
                return new HardcodedRatio(0.8 / 0.64, true);
            case "llama3-8b-8192":
                return new HardcodedRatio(2.0, true);
            case "llama3-70b-8192":
                return new HardcodedRatio(0.79 / 0.59, true);
            default:
                break;
        }

        return new HardcodedRatio(1.0, false);
    }

    public static Map<String, Double> getCompletionRatioMap() {
        return new ConcurrentHashMap<>(completionRatioMap);
    }

    public static Map<String, Double> getDefaultCompletionRatioMap() {
        return new ConcurrentHashMap<>(DEFAULT_COMPLETION_RATIO);
    }

    /**
     * 全量覆盖补全倍率。      * 采用 clear + putAll 语义。
     */
    public static void update(Map<String, Double> ratios) {
        completionRatioMap.clear();
        if (ratios != null && !ratios.isEmpty()) {
            completionRatioMap.putAll(ratios);
        }
    }

    /**
     * 单条更新补全倍率（SPI 扩展点，供动态定价回写计费链路）。
     * <p>
     * 注意：硬编码锁定的模型（locked=true）不受此方法影响，getCompletionRatio 仍返回硬编码值。
     *
     * @param modelName 模型名（内部自动归一化）
     * @param ratio     倍率值
     */
    public static void putCompletionRatio(String modelName, double ratio) {
        if (modelName != null) {
            completionRatioMap.put(ModelRatioConfig.formatMatchingModelName(modelName), ratio);
        }
    }
}
