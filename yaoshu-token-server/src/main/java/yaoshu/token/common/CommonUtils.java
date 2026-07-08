package yaoshu.token.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 通用工具函数  */
public final class CommonUtils {

    private CommonUtils() {
    }

    /**
     * 检测是否运行在容器环境中      * <p>
     * 检测方式：
     * 1. /.dockerenv 文件是否存在（Docker 容器标志）
     * 2. /proc/1/cgroup 是否包含 docker/containerd/kubepods/lxc
     * 3. 环境变量 KUBERNETES_SERVICE_HOST / DOCKER_CONTAINER / container
     * 4. /proc/1/comm 是否为容器运行时入口进程
     */
    public static boolean isRunningInContainer() {
        // Method 1: Check for .dockerenv file
        if (Files.exists(Path.of("/.dockerenv"))) {
            return true;
        }

        // Method 2: Check /proc/1/cgroup
        try {
            String cgroup = Files.readString(Path.of("/proc/1/cgroup"));
            if (cgroup.contains("docker") || cgroup.contains("containerd")
                    || cgroup.contains("kubepods") || cgroup.contains("/lxc/")) {
                return true;
            }
        } catch (IOException ignored) {
            // 非 Linux 环境或无权访问
        }

        // Method 3: Check environment variables
        List<String> containerEnvVars = List.of("KUBERNETES_SERVICE_HOST", "DOCKER_CONTAINER", "container");
        for (String envVar : containerEnvVars) {
            if (System.getenv(envVar) != null && !System.getenv(envVar).isEmpty()) {
                return true;
            }
        }

        // Method 4: Check /proc/1/comm
        try {
            String comm = Files.readString(Path.of("/proc/1/comm")).trim();
            if (!"init".equals(comm) && !"systemd".equals(comm)) {
                if (comm.contains("docker") || comm.contains("containerd") || comm.contains("runc")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // 非 Linux 环境
        }

        return false;
    }

    /**
     * 秒数 → 中文可读时长      * <p>
     * 例：3661 → "1 小时 1 分钟 1 秒"
     */
    public static String seconds2Time(int num) {
        if (num <= 0) return "0 秒";

        StringBuilder sb = new StringBuilder();

        if (num / 31104000 > 0) {
            sb.append(num / 31104000).append(" 年 ");
            num %= 31104000;
        }
        if (num / 2592000 > 0) {
            sb.append(num / 2592000).append(" 个月 ");
            num %= 2592000;
        }
        if (num / 86400 > 0) {
            sb.append(num / 86400).append(" 天 ");
            num %= 86400;
        }
        if (num / 3600 > 0) {
            sb.append(num / 3600).append(" 小时 ");
            num %= 3600;
        }
        if (num / 60 > 0) {
            sb.append(num / 60).append(" 分钟 ");
            num %= 60;
        }
        if (num > 0 || sb.isEmpty()) {
            sb.append(num).append(" 秒");
        }

        return sb.toString().trim();
    }
}
