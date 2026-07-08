package yaoshu.token.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 用户可用分组配置  */
public final class UserUsableGroupConfig {

    private UserUsableGroupConfig() {
    }

    private static final ConcurrentHashMap<String, String> USER_USABLE_GROUPS = new ConcurrentHashMap<>(Map.of(
            "default", "默认分组",
            "vip", "vip分组"
    ));

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /** 获取用户可用分组副本 */
    public static Map<String, String> getUserUsableGroupsCopy() {
        return new ConcurrentHashMap<>(USER_USABLE_GROUPS);
    }

    /** 获取分组描述 */
    public static String getUsableGroupDescription(String groupName) {
        return USER_USABLE_GROUPS.getOrDefault(groupName, groupName);
    }

    /** 更新分组（由 JSON 配置同步） */
    public static void updateFromMap(Map<String, String> groups) {
        lock.writeLock().lock();
        try {
            USER_USABLE_GROUPS.clear();
            if (groups != null) USER_USABLE_GROUPS.putAll(groups);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
