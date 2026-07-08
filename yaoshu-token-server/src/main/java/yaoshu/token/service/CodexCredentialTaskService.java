package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.pojo.entity.Channel;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Codex 凭证任务服务  * <p>
 * 管理 Codex OAuth 凭证的异步自动刷新任务。
 * <p>
 * 定时扫描所有 Codex 类型的渠道，检查 key 中的 expired 字段，
 * 距过期不足 24 小时的凭证自动刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodexCredentialTaskService {

    // 定时常量
    private static final Duration TICK_INTERVAL = Duration.ofMinutes(10);
    private static final Duration REFRESH_THRESHOLD = Duration.ofHours(24);
    private static final int BATCH_SIZE = 200;
    private static final Duration REFRESH_TIMEOUT = Duration.ofSeconds(15);

    private static final DateTimeFormatter RFC3339 = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private final ChannelMapper channelMapper;
    private final CodexCredentialRefreshService codexCredentialRefreshService;

    /**
     * 启动凭证刷新定时任务      */
    public void startCredentialRefreshTask(ScheduledExecutorService scheduler) {
        long periodSeconds = TICK_INTERVAL.getSeconds();
        log.info("starting codex credential auto-refresh task, tick={}s threshold={}h",
                periodSeconds, REFRESH_THRESHOLD.toHours());
        // 初始延迟 0，启动后立即执行一次
        scheduler.scheduleAtFixedRate(this::refreshExpiringCredentials,
                0, periodSeconds, TimeUnit.SECONDS);
    }

    /**
     * 刷新即将过期的凭证      */
    private void refreshExpiringCredentials() {
        if (!RUNNING.compareAndSet(false, true)) {
            return;
        }
        try {
            doRefresh();
        } catch (Exception e) {
            log.error("error refreshing codex credentials: {}", e.getMessage());
        } finally {
            RUNNING.set(false);
        }
    }

    /**
     * 实际刷新逻辑：分批查询 Codex 渠道 → 过滤过期 → 刷新
     */
    private void doRefresh() {
        LocalDateTime now = LocalDateTime.now();
        int scanned = 0;
        int refreshed = 0;
        int offset = 0;

        while (true) {
            // 分批查询 Codex 类型、状态为 ENABLED 或 AUTO_DISABLED 的渠道
            LambdaQueryWrapper<Channel> qw = new LambdaQueryWrapper<>();
            qw.eq(Channel::getType, ChannelConstants.CHANNEL_TYPE_CODEX)
                    .in(Channel::getStatus,
                            CommonConstants.CHANNEL_STATUS_ENABLED,
                            CommonConstants.CHANNEL_STATUS_AUTO_DISABLED)
                    .orderByAsc(Channel::getId)
                    .last("LIMIT " + BATCH_SIZE + " OFFSET " + offset);

            List<Channel> channels = channelMapper.selectList(qw);
            if (channels.isEmpty()) {
                break;
            }
            offset += BATCH_SIZE;

            for (Channel ch : channels) {
                scanned++;
                String rawKey = ch.getKey();
                if (rawKey == null || rawKey.trim().isEmpty()) {
                    continue;
                }

                CodexCredentialRefreshService.CodexOAuthKey oauthKey;
                try {
                    oauthKey = CodexCredentialRefreshService.parseOAuthKey(rawKey);
                } catch (Exception e) {
                    continue;
                }

                String refreshToken = oauthKey.getRefreshToken();
                if (refreshToken == null || refreshToken.trim().isEmpty()) {
                    continue;
                }

                // 检查是否需要刷新：expired 字段距当前时间不足 24 小时
                String expiredStr = oauthKey.getExpired();
                if (expiredStr != null && !expiredStr.trim().isEmpty()) {
                    try {
                        LocalDateTime expiredAt = LocalDateTime.parse(expiredStr.trim(), RFC3339);
                        if (expiredAt.isAfter(now) && Duration.between(now, expiredAt).compareTo(REFRESH_THRESHOLD) > 0) {
                            // 还未接近过期，跳过
                            continue;
                        }
                    } catch (Exception e) {
                        // 解析失败 → 视为需要刷新
                    }
                }

                // 执行刷新
                try {
                    CodexCredentialRefreshService.CodexOAuthKey newKey =
                            codexCredentialRefreshService.refreshChannelCredential(ch.getId(), false);
                    refreshed++;
                    log.info("codex credential auto-refresh: channel_id={} name={} refreshed, expires_at={}",
                            ch.getId(), ch.getName(), newKey.getExpired());
                } catch (Exception e) {
                    log.warn("codex credential auto-refresh: channel_id={} name={} refresh failed: {}",
                            ch.getId(), ch.getName(), e.getMessage());
                }
            }
        }

        if (refreshed > 0) {
            log.info("codex credential auto-refresh completed: scanned={} refreshed={}", scanned, refreshed);
        } else {
            log.debug("codex credential auto-refresh: scanned={} refreshed=0", scanned);
        }
    }
}
