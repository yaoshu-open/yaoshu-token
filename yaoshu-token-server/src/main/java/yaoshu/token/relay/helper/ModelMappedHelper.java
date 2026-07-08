package yaoshu.token.relay.helper;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.relay.common.RelayInfo;

import java.util.*;

/**
 * 模型映射辅助  * <p>
 * 处理渠道模型映射：链式重定向、循环检测、紧凑后缀处理。
 */
@Slf4j
public final class ModelMappedHelper {

    private ModelMappedHelper() {
    }

    /** 紧凑模型后缀*/
    private static final String COMPACT_MODEL_SUFFIX = "-compact";

    /** 最大映射链深度，防止死循环 */
    private static final int MAX_MAPPING_DEPTH = 10;

    /**
     * 执行模型映射      */
    public static String apply(RelayInfo info, String modelMappingJson, Object request) throws Exception {
        if (info == null) return info != null ? info.getUpstreamModelName() : null;

        boolean isResponsesCompact = info.getRelayMode() == yaoshu.token.relay.constant.RelayModeEnum.RESPONSES_COMPACT;
        String originModelName = info.getOriginModelName();
        String mappingModelName = originModelName;

        // 紧凑模式：去除 -compact 后缀
        if (isResponsesCompact && mappingModelName != null && mappingModelName.endsWith(COMPACT_MODEL_SUFFIX)) {
            mappingModelName = mappingModelName.substring(0, mappingModelName.length() - COMPACT_MODEL_SUFFIX.length());
        }

        // 模型映射：支持链式重定向 + 循环检测
        if (modelMappingJson != null && !modelMappingJson.isEmpty() && !"{}".equals(modelMappingJson)) {
            String mapped = applyChainMapping(modelMappingJson, mappingModelName);
            if (mapped != null) {
                info.setModelMapped(true);
                mappingModelName = mapped;
            }
        }

        // 紧凑模式：重新拼接 suffix
        if (isResponsesCompact) {
            info.setUpstreamModelName(mappingModelName);
            info.setOriginModelName(mappingModelName + COMPACT_MODEL_SUFFIX);
        } else {
            info.setUpstreamModelName(mappingModelName);
        }

        return info.getUpstreamModelName();
    }

    /**
     * 链式映射 + 循环检测      */
    @SuppressWarnings("unchecked")
    private static String applyChainMapping(String modelMappingJson, String mappingModelName) {
        Map<String, Object> modelMap;
        try {
            modelMap = Convert.toJSONObject(modelMappingJson);
        } catch (Exception e) {
            log.debug("Failed to parse model mapping JSON: {}", e.getMessage());
            return null;
        }

        if (modelMap == null || modelMap.isEmpty()) return null;

        Set<String> visited = new HashSet<>();
        visited.add(mappingModelName);

        String current = mappingModelName;
        for (int depth = 0; depth < MAX_MAPPING_DEPTH; depth++) {
            Object mapped = modelMap.get(current);
            if (mapped == null) break;

            String nextModel = mapped.toString();
            if (nextModel.isEmpty() || nextModel.equals(current)) break;

            // 循环检测
            if (visited.contains(nextModel)) {
                log.warn("Model mapping loop detected: {} → {}", current, nextModel);
                break;
            }
            visited.add(nextModel);
            current = nextModel;
        }

        return current.equals(mappingModelName) ? null : current;
    }
}
