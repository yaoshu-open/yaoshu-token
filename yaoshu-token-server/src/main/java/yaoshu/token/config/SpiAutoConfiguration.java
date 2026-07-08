package yaoshu.token.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import yaoshu.token.relay.DefaultChannelHealthHandler;
import yaoshu.token.relay.DefaultChannelSelector;
import yaoshu.token.relay.DefaultRelayRetryStrategy;
import yaoshu.token.relay.NoOpModelListFilter;
import yaoshu.token.relay.NoOpPricingEnhancer;
import yaoshu.token.relay.NoOpRelayRequestInterceptor;
import yaoshu.token.service.ChannelManagementService;
import yaoshu.token.service.ChannelService;
import yaoshu.token.spi.ChannelHealthHandler;
import yaoshu.token.spi.ChannelSelector;
import yaoshu.token.spi.ModelListFilter;
import yaoshu.token.spi.PricingEnhancer;
import yaoshu.token.spi.RelayRequestInterceptor;
import yaoshu.token.spi.RelayRetryStrategy;

/**
 * SPI 自动装配 — 默认实现注册。
 * <p>
 * 所有默认 Bean 均标注 {@code @ConditionalOnMissingBean}：
 * 存在自定义实现时自动跳过默认 Bean；
 * 无自定义实现时无缝退化到默认实现。
 */
@Configuration
public class SpiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RelayRequestInterceptor.class)
    public RelayRequestInterceptor defaultRelayRequestInterceptor() {
        return new NoOpRelayRequestInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(RelayRetryStrategy.class)
    public RelayRetryStrategy defaultRelayRetryStrategy() {
        return new DefaultRelayRetryStrategy();
    }

    @Bean
    @ConditionalOnMissingBean(ChannelHealthHandler.class)
    public ChannelHealthHandler defaultChannelHealthHandler(ChannelService channelService,
                                                            ChannelManagementService channelManagementService) {
        return new DefaultChannelHealthHandler(channelService, channelManagementService);
    }

    @Bean
    @ConditionalOnMissingBean(ChannelSelector.class)
    public ChannelSelector defaultChannelSelector() {
        return new DefaultChannelSelector();
    }

    @Bean
    @ConditionalOnMissingBean(ModelListFilter.class)
    public ModelListFilter defaultModelListFilter() {
        return new NoOpModelListFilter();
    }

    @Bean
    @ConditionalOnMissingBean(PricingEnhancer.class)
    public PricingEnhancer defaultPricingEnhancer() {
        return new NoOpPricingEnhancer();
    }
}
