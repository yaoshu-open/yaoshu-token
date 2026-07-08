package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

/**
 * SMTP LOGIN 认证机制（Outlook/Exchange Online 兼容）  */
@Slf4j
public final class OutlookAuthService {

    private OutlookAuthService() {
    }

    /**
     * 判断是否为 Outlook 服务器（兼容多地区 outlook 和 ofb 邮箱）
     */
    public static boolean isOutlookServer(String server) {
        return server.contains("outlook") || server.contains("onmicrosoft");
    }
}
