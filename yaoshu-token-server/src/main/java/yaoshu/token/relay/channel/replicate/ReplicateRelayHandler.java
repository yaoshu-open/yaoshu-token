package yaoshu.token.relay.channel.replicate;

import jakarta.servlet.http.HttpServletResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

/** Replicate 图片响应处理 */
public final class ReplicateRelayHandler {
    private ReplicateRelayHandler() {}
    public static Usage replicateHandler(RelayInfo info, byte[] body) throws Exception {
        HttpServletResponse r = info.getResponse();
        if (r != null && body != null) { r.getOutputStream().write(body); r.getOutputStream().flush(); }
        return new Usage();
    }
}
