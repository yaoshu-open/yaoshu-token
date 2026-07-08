package yaoshu.token.relay.channel.jimeng;

import jakarta.servlet.http.HttpServletResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;

/** 即梦图片响应处理 */
public final class JimengRelayHandler {
    private JimengRelayHandler() {}
    public static Usage jimengHandler(RelayInfo info, byte[] body) throws Exception {
        HttpServletResponse r = info.getResponse();
        if (r != null && body != null) { r.getOutputStream().write(body); r.getOutputStream().flush(); }
        return new Usage();
    }
}
