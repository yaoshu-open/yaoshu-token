package yaoshu.token.config;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * <p>
 * 提供与 Go 的 SafeSendBool/SafeSendString 等价的线程安全发送操作
 */
@Slf4j
public final class AsyncConfig {

    private AsyncConfig() {
    }

    /**
     * 安全发送 boolean 到通道（通道已关闭则静默返回 true）
     */
    public static boolean safeSendBool(BlockingQueue<Boolean> ch, boolean value) {
        try {
            ch.put(value);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /**
     * 安全发送 String 到通道（通道已关闭则静默返回 true）
     */
    public static boolean safeSendString(BlockingQueue<String> ch, String value) {
        try {
            ch.put(value);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    /**
     * 带超时的安全发送 String 到通道
     */
    public static boolean safeSendStringTimeout(BlockingQueue<String> ch, String value, int timeoutSeconds) {
        try {
            return ch.offer(value, timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
