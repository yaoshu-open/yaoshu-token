package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 系统日志服务  * <p>
 * 提供 SysLog / SysError / FatalLog 等系统级日志输出
 */
@Slf4j
public final class SysLogService {

    private SysLogService() {
    }

    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd - HH:mm:ss");

    private static String formatTime() {
        return LocalDateTime.now().format(LOG_TIME_FORMAT);
    }

    /** 系统信息日志 */
    public static void sysLog(String msg) {
        log.info("[SYS] {} | {}", formatTime(), msg);
    }

    /** 系统错误日志 */
    public static void sysError(String msg) {
        log.error("[SYS] {} | {}", formatTime(), msg);
    }

    /** 致命错误日志（记录后退出进程） */
    public static void fatalLog(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg);
        }
        log.error("[FATAL] {} | {}", formatTime(), sb);
        System.exit(1);
    }
}
