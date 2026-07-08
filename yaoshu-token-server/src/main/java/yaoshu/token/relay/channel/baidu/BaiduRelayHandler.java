package yaoshu.token.relay.channel.baidu;

import jakarta.servlet.http.HttpServletResponse;
import yaoshu.token.pojo.dto.Usage;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.helper.RelayCommonHelper;
import yaoshu.token.relay.helper.StreamScanner;

import java.io.InputStream;

/** 百度文心响应处理（委托 OpenAI 模式） */
public final class BaiduRelayHandler {
    private BaiduRelayHandler() {}
    public static Usage baiduStreamHandler(RelayInfo info, InputStream inputStream) throws Exception {
        HttpServletResponse r = info.getResponse();
        if (r == null || inputStream == null) return new Usage();
        StreamScanner.scan(inputStream, info, d -> { RelayCommonHelper.stringData(r, d); return true; }, r);
        RelayCommonHelper.done(r); return new Usage();
    }
}
