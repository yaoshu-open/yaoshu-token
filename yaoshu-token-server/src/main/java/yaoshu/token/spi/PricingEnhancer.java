package yaoshu.token.spi;

import yaoshu.token.pojo.vo.PricingVO;

import java.util.List;

/**
 * 定价快照增强 — 定价数据产出后的后处理钩子。
 * <p>
 * 默认实现透传所有定价数据；可通过 SPI 扩展点覆盖此 Bean 实现自定义增强策略（如动态定价）。
 */
public interface PricingEnhancer {

    /**
     * 增强定价列表，原地修改定价数据。
     *
     * @param pricingList 原始定价列表
     */
    void enhance(List<PricingVO> pricingList);
}
