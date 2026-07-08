package yaoshu.token.constant;

/**
 * Redis 缓存键名常量  * <p>
 * 键名格式必须与原 Go 项目完全一致，否则缓存全部失效。
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {
    }

    /** 用户组缓存键格式：user_group:{userId} */
    public static final String USER_GROUP_KEY_FMT = "user_group:%d";
    /** 用户配额缓存键格式：user_quota:{userId} */
    public static final String USER_QUOTA_KEY_FMT = "user_quota:%d";
    /** 用户启用状态缓存键格式：user_enabled:{userId} */
    public static final String USER_ENABLED_KEY_FMT = "user_enabled:%d";
    /** 用户名缓存键格式：user_name:{userId} */
    public static final String USER_USERNAME_KEY_FMT = "user_name:%d";

    /** Token 字段名：剩余配额 */
    public static final String TOKEN_FIELD_REMAIN_QUOTA = "RemainQuota";
    /** Token 字段名：分组 */
    public static final String TOKEN_FIELD_GROUP = "Group";
}
