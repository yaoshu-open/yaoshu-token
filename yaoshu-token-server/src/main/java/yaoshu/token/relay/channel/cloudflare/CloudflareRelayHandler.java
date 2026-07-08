package yaoshu.token.relay.channel.cloudflare;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

/**
 * Cloudflare Workers AI 响应处理  */
@Slf4j
public final class CloudflareRelayHandler { private CloudflareRelayHandler() {}
    public static Usage cloudflareStreamHandler(RelayInfo info, byte[] body) throws Exception {
        HttpServletResponse r = info.getResponse();
        if (r != null && body != null) { r.getOutputStream().write(body); r.getOutputStream().flush(); }
        return new Usage();
    }
}