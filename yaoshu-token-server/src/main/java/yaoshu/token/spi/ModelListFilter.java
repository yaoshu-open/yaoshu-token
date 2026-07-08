package yaoshu.token.spi;

import java.util.List;
import java.util.Map;

/**
 * 模型列表过滤 — 模型列表输出前的过滤钩子。
 * <p>
 * 默认实现透传所有模型；可通过 SPI 扩展点覆盖此 Bean 实现自定义过滤策略（如精选模型白名单）。
 */
public interface ModelListFilter {

    /**
     * 过滤模型列表，返回过滤后的列表。
     *
     * @param models 原始模型列表，每个条目是 OpenAI 兼容格式 {id, object, created, owned_by}
     * @return 过滤后的列表
     */
    List<Map<String, Object>> filter(List<Map<String, Object>> models);
}
