package yaoshu.token.config;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * SSRF 防护校验  * <p>
 * 校验 URL 是否安全，防止 SSRF（Server-Side Request Forgery）攻击。
 * 支持域名白名单/黑名单、IP 白名单/黑名单、端口过滤、私有 IP 限制。
 */
@Slf4j
public final class SSRFProtectionCheck {

    private SSRFProtectionCheck() {
    }

    /** 私有 IPv4 网段 */
    private static final List<String> PRIVATE_IPV4_CIDRS = List.of(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8",
            "169.254.0.0/16", "172.16.0.0/12", "192.0.0.0/24", "192.0.2.0/24",
            "192.168.0.0/16", "198.18.0.0/15", "198.51.100.0/24", "203.0.113.0/24",
            "224.0.0.0/4", "240.0.0.0/4", "255.255.255.255/32"
    );

    /** 私有 IPv6 网段 */
    private static final List<String> PRIVATE_IPV6_CIDRS = List.of(
            "::/128", "::1/128", "::ffff:0:0/96", "64:ff9b::/96",
            "100::/64", "2001::/23", "2001:db8::/32", "fc00::/7",
            "fe80::/10", "ff00::/8"
    );

    /** SSRF 防护配置 */
    public record SSRFProtection(
            boolean allowPrivateIp,
            boolean domainFilterMode,   // true: 白名单, false: 黑名单
            List<String> domainList,
            boolean ipFilterMode,       // true: 白名单, false: 黑名单
            List<String> ipList,
            List<Integer> allowedPorts,
            boolean applyIPFilterForDomain
    ) {
    }

    /** 默认 SSRF 防护配置（白名单模式，空列表 = 不拦截任何域名） */
    public static final SSRFProtection DEFAULT = new SSRFProtection(
            false, true, List.of(), true, List.of(), List.of(), false
    );

    /**
     * 使用 FetchSetting 配置参数验证 URL 安全性
     */
    public static void validateURLWithFetchSetting(
            String urlStr, boolean enableSSRFProtection, boolean allowPrivateIp,
            boolean domainFilterMode, boolean ipFilterMode,
            List<String> domainList, List<String> ipList, List<String> allowedPorts,
            boolean applyIPFilterForDomain) {

        if (!enableSSRFProtection) {
            return;
        }

        // 解析端口范围
        List<Integer> allowedPortInts = parsePortRanges(allowedPorts);

        SSRFProtection protection = new SSRFProtection(
                allowPrivateIp, domainFilterMode, domainList,
                ipFilterMode, ipList, allowedPortInts, applyIPFilterForDomain);
        validateURL(protection, urlStr);
    }

    /**
     * 解析端口范围配置（支持 "80"、"8000-9000" 格式）
     */
    static List<Integer> parsePortRanges(List<String> portConfigs) {
        List<Integer> ports = new ArrayList<>();
        for (String config : portConfigs) {
            config = config.trim();
            if (config.isEmpty()) continue;

            if (config.contains("-")) {
                String[] parts = config.split("-");
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                if (start > end) throw new IllegalArgumentException("invalid port range: " + config);
                for (int port = start; port <= end; port++) {
                    ports.add(port);
                }
            } else {
                int port = Integer.parseInt(config);
                if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port: " + port);
                ports.add(port);
            }
        }
        return ports;
    }

    // ======================== 核心校验 ========================

    private static void validateURL(SSRFProtection p, String urlStr) {
        URI uri;
        try {
            uri = new URI(urlStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid URL format: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("unsupported protocol: " + scheme + " (only http/https allowed)");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("missing host in URL");
        }
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }

        // 端口校验
        if (!isAllowedPort(p.allowedPorts(), port)) {
            throw new IllegalArgumentException("port " + port + " is not allowed");
        }

        // 检查 host 是否为 IP 地址
        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("DNS resolution failed: " + host);
        }

        // 如果是点分 IP（非域名），跳过域名检查
        if (isLiteralIP(host)) {
            if (!isIPAccessAllowed(p, addr)) {
                throw new IllegalArgumentException("IP not allowed: " + host);
            }
            return;
        }

        // 域名过滤
        if (!isDomainAllowed(p, host)) {
            throw new IllegalArgumentException("domain not allowed: " + host);
        }

        // 对域名应用 IP 过滤
        if (p.applyIPFilterForDomain()) {
            if (!isIPAccessAllowed(p, addr)) {
                throw new IllegalArgumentException("domain " + host + " resolves to blocked IP: " + addr.getHostAddress());
            }
        }
    }

    /** 判断字符串是否为字面 IP 地址 */
    private static boolean isLiteralIP(String host) {
        return host.chars().allMatch(c -> c == '.' || c == ':' || Character.isDigit(c))
                && (host.contains(".") || host.contains(":"));
    }

    /** 端口是否在允许列表中 */
    private static boolean isAllowedPort(List<Integer> allowedPorts, int port) {
        if (allowedPorts.isEmpty()) return true;
        return allowedPorts.contains(port);
    }

    /** IP 是否允许访问 */
    private static boolean isIPAccessAllowed(SSRFProtection p, InetAddress addr) {
        // 私有 IP 检查
        if (addr.isSiteLocalAddress() || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress() || addr.isMulticastAddress()
                || addr.isAnyLocalAddress()) {
            if (!p.allowPrivateIp()) return false;
        }

        // IP CIDR 过滤
        boolean listed = isIPInList(addr, p.ipList());
        if (p.ipFilterMode()) {  // 白名单模式
            return listed;
        }
        return !listed;  // 黑名单模式
    }

    /** 域名是否允许 */
    private static boolean isDomainAllowed(SSRFProtection p, String domain) {
        boolean listed = isDomainListed(domain, p.domainList());
        if (p.domainFilterMode()) return listed;  // 白名单
        return !listed;  // 黑名单
    }

    /** 域名是否在列表中（支持通配符 *.example.com） */
    static boolean isDomainListed(String domain, List<String> list) {
        domain = domain.toLowerCase();
        for (String item : list) {
            item = item.toLowerCase().trim();
            if (item.isEmpty()) continue;
            if (item.startsWith("*.")) {
                String suffix = item.substring("*.".length());
                if (domain.endsWith("." + suffix) || domain.equals(suffix)) return true;
            } else if (domain.equals(item)) {
                return true;
            }
        }
        return false;
    }

    /** IP 是否在 CIDR 列表中 */
    static boolean isIPInList(InetAddress addr, List<String> cidrList) {
        if (cidrList.isEmpty()) return false;
        for (String cidr : cidrList) {
            try {
                String[] parts = cidr.split("/");
                InetAddress netAddr = InetAddress.getByName(parts[0]);
                byte[] netBytes = netAddr.getAddress();
                byte[] addrBytes = addr.getAddress();
                if (netBytes.length != addrBytes.length) continue;

                int prefix = Integer.parseInt(parts[1]);
                int fullBytes = prefix / 8;
                int remainingBits = prefix % 8;

                boolean match = true;
                for (int i = 0; i < fullBytes; i++) {
                    if (netBytes[i] != addrBytes[i]) { match = false; break; }
                }
                if (match && remainingBits > 0 && fullBytes < netBytes.length) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    match = (netBytes[fullBytes] & mask) == (addrBytes[fullBytes] & mask);
                }
                if (match) return true;
            } catch (Exception e) {
                log.debug("Failed to parse CIDR: {}", cidr, e);
            }
        }
        return false;
    }
}
