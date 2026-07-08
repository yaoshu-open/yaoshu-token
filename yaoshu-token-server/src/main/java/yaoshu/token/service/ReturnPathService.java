package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

/**
 * 返回路径服务  * <p>
 * 处理 OAuth 回调中的 return_to 路径（安全校验 + 重定向）。
 */
@Slf4j
public class ReturnPathService {

    /** 允许的返回路径前缀 */
    private static final String ALLOWED_PREFIX = "/";

    /**
     * 验证返回路径是否安全      *
     * @param returnPath 返回路径
     * @return 是否安全
     */
    public boolean validateReturnPath(String returnPath) {
        if (returnPath == null || returnPath.isEmpty()) return false;
        // 防止开放重定向
        if (returnPath.startsWith("//") || returnPath.startsWith("http://") || returnPath.startsWith("https://")) {
            return false;
        }
        return returnPath.startsWith(ALLOWED_PREFIX);
    }

    /**
     * 构建安全的返回路径      */
    public String buildReturnPath(String returnPath, String defaultPath) {
        if (validateReturnPath(returnPath)) {
            return returnPath;
        }
        return defaultPath != null ? defaultPath : "/";
    }
}
