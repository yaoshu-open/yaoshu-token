package yaoshu.token.service;

import com.fasterxml.jackson.core.type.TypeReference;
import ai.yue.library.base.convert.Convert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 充值分组比例服务  * <p>
 * 管理不同充值分组（default/vip/svip）的充值比例
 */
public final class TopupRatioService {

    private TopupRatioService() {
    }    /** 充值分组比例缓存（线程安全） */
    private static Map<String, Double> topupGroupRatio = new ConcurrentHashMap<>();
    private static final ReentrantReadWriteLock ratioLock = new ReentrantReadWriteLock();

    static {
        topupGroupRatio.put("default", 1.0);
        topupGroupRatio.put("vip", 1.0);
        topupGroupRatio.put("svip", 1.0);
    }

    /** 将充值分组比例序列化为 JSON 字符串 */
    public static String toJsonString() {
        ratioLock.readLock().lock();
        try {
            return Convert.toJSONString(topupGroupRatio);
        } catch (Exception e) {
            SysLogService.sysError("error marshalling topup group ratio: " + e.getMessage());
            return "{}";
        } finally {
            ratioLock.readLock().unlock();
        }
    }

    /** 从 JSON 字符串更新充值分组比例 */
    public static void updateByJsonString(String jsonStr) {
        ratioLock.writeLock().lock();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Double> newRatio = (Map<String, Double>) (Map<?, ?>) Convert.toJSONObject(jsonStr);
            topupGroupRatio.clear();
            topupGroupRatio.putAll(newRatio);
        } catch (Exception e) {
            SysLogService.sysError("error unmarshalling topup group ratio: " + e.getMessage());
        } finally {
            ratioLock.writeLock().unlock();
        }
    }

    /** 获取指定分组的充值比例，未找到时返回 1 */
    public static double getRatio(String name) {
        ratioLock.readLock().lock();
        try {
            Double ratio = topupGroupRatio.get(name);
            if (ratio == null) {
                SysLogService.sysError("topup group ratio not found: " + name);
                return 1.0;
            }
            return ratio;
        } finally {
            ratioLock.readLock().unlock();
        }
    }
}
