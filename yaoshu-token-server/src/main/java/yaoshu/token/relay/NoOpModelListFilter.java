package yaoshu.token.relay;

import yaoshu.token.spi.ModelListFilter;

import java.util.List;
import java.util.Map;

/**
 * 模型列表过滤默认实现 — 空实现（透传所有模型）。
 * <p>
 * 可通过 SPI 扩展点覆盖此 Bean 实现自定义过滤策略（如精选模型白名单）。
 */
public class NoOpModelListFilter implements ModelListFilter {

    @Override
    public List<Map<String, Object>> filter(List<Map<String, Object>> models) {
        return models;
    }
}
