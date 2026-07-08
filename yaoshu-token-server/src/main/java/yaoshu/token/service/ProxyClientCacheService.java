package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理客户端缓存统一治理入口。  * <p>
 * Go 在 service 层维护全局 proxyClients map，并在 7 处调用 ResetProxyClientCache 清理。
 * Java 端原本由 ChannelManagementService / CodexOAuthService / CodexUsageService 各自独立持有
 * proxyClients Map，且无任何清理逻辑——导致管理员修改渠道代理 / Codex 凭证刷新后仍使用旧 HttpClient。
 * <p>
 * 本 Bean 注入三处 Service，通过委托调用各自内部的清空方法实现统一 reset；调用方在对应写库 / 凭证刷新
 * 路径上调用 {@link #reset()}。
 * <p>
 * 注意：{@code ChannelManagementService} 的依赖链（{@code → CodexCredentialRefreshService → ProxyClientCacheService}）
 * 与本 Bean 形成环，故用 {@code @Lazy} 延迟解析，构造期只持有代理引用，首次 {@link #reset()} 调用时才真正
 * 注入实例（详见 Bug-001 缺陷追踪）。
 */
@Slf4j
@Service
public class ProxyClientCacheService {

    private final ChannelManagementService channelManagementService;
    private final CodexOAuthService codexOAuthService;
    private final CodexUsageService codexUsageService;

    public ProxyClientCacheService(@Lazy ChannelManagementService channelManagementService,
                                   CodexOAuthService codexOAuthService,
                                   CodexUsageService codexUsageService) {
        this.channelManagementService = channelManagementService;
        this.codexOAuthService = codexOAuthService;
        this.codexUsageService = codexUsageService;
    }

    /**
     * 清空三处独立 proxyClients 缓存。      */
    public void reset() {
        try { channelManagementService.resetProxyClientCache(); } catch (Exception e) {
            log.warn("清空 ChannelManagementService.proxyClients 失败: {}", e.getMessage());
        }
        try { codexOAuthService.resetProxyClientCache(); } catch (Exception e) {
            log.warn("清空 CodexOAuthService.proxyClients 失败: {}", e.getMessage());
        }
        try { codexUsageService.resetProxyClientCache(); } catch (Exception e) {
            log.warn("清空 CodexUsageService.proxyClients 失败: {}", e.getMessage());
        }
        log.info("ProxyClientCache 已通过 3 个 Service 的 reset 方法执行清理");
    }
}

