package yaoshu.token.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import yaoshu.token.service.SetupService;

/**
 * 系统启动初始化器（auto 模式）  * <p>
 * 应用启动完成后执行：若 setups 表无记录且模式为 auto（默认），自动创建 root 账号。
 * 失败不阻断启动（可手动通过 POST /api/setup 完成）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class RootUserInitializer implements ApplicationRunner {

    private final SetupService setupService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            setupService.initializeAuto();
        } catch (Exception e) {
            log.error("[Setup] 启动初始化失败，系统将继续启动（可手动通过 POST /api/setup 完成）", e);
        }
    }
}
