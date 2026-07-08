package yaoshu.token.service;

import cn.hutool.v7.core.text.StrUtil;
import yaoshu.token.common.StrUtilCompat;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ai.yue.library.base.util.I18nUtils;
import org.springframework.stereotype.Service;
import yaoshu.token.constant.CommonConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 易支付协议服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class EpayService {

    private final OptionService optionService;

    /** options 表 key：自定义回调地址 */
    private static final String KEY_CUSTOM_CALLBACK_ADDRESS = "CustomCallbackAddress";
    /** options 表 key：服务器地址 */
    private static final String KEY_SERVER_ADDRESS = "ServerAddress";
    /** options 表 key：易支付商户 ID */
    private static final String KEY_EPAY_ID = "EpayId";
    /** options 表 key：易支付商户密钥 */
    private static final String KEY_EPAY_KEY = "EpayKey";
    /** options 表 key：易支付网关地址 */
    private static final String KEY_PAY_ADDRESS = "PayAddress";

    private static final String PURCHASE_PATH = "/submit.php";
    private static final String SIGN_TYPE_MD5 = "MD5";
    public static final String STATUS_TRADE_SUCCESS = "TRADE_SUCCESS";

    /**
     * 获取支付回调地址。      * 优先使用 CustomCallbackAddress，为空时回退到 ServerAddress。
     */
    public String getCallbackAddress() {
        String custom = optionService.getValue(KEY_CUSTOM_CALLBACK_ADDRESS);
        if (StrUtil.isNotBlank(custom)) {
            return custom.trim();
        }
        String serverAddress = optionService.getValue(KEY_SERVER_ADDRESS);
        return serverAddress == null ? "" : serverAddress.trim();
    }

    /**
     * 控制台回跳地址使用 ServerAddress，并做主题路径映射。
     */
    public String buildConsoleReturnUrl(String suffix) {
        String serverAddress = StrUtilCompat.blankToDefault(optionService.getValue(KEY_SERVER_ADDRESS), "").trim();
        if (serverAddress.isEmpty()) {
            return CommonConstants.themeAwarePath(suffix);
        }
        return StrUtil.removeSuffix(serverAddress, "/") + CommonConstants.themeAwarePath(suffix);
    }

    public String buildCallbackUrl(String path) {
        String callbackAddress = getCallbackAddress();
        if (callbackAddress.isEmpty()) {
            throw new RuntimeException(I18nUtils.get("payment.callback_url_not_configured"));
        }
        return StrUtil.removeSuffix(callbackAddress, "/") + path;
    }

    public boolean isConfigured() {
        return StrUtil.isNotBlank(optionService.getValue(KEY_PAY_ADDRESS))
                && StrUtil.isNotBlank(optionService.getValue(KEY_EPAY_ID))
                && StrUtil.isNotBlank(optionService.getValue(KEY_EPAY_KEY));
    }

    public PurchaseRequest buildPurchaseRequest(String paymentMethod,
                                                String tradeNo,
                                                String name,
                                                String money,
                                                String notifyUrl,
                                                String returnUrl) {
        if (!isConfigured()) {
            throw new RuntimeException(I18nUtils.get("payment.admin_not_configured"));
        }
        Map<String, String> requestParams = new LinkedHashMap<>();
        requestParams.put("pid", optionService.getValue(KEY_EPAY_ID));
        requestParams.put("type", paymentMethod);
        requestParams.put("out_trade_no", tradeNo);
        requestParams.put("notify_url", notifyUrl);
        requestParams.put("name", name);
        requestParams.put("money", money);
        requestParams.put("device", "pc");
        requestParams.put("sign_type", SIGN_TYPE_MD5);
        requestParams.put("return_url", returnUrl);
        requestParams.put("sign", "");
        return new PurchaseRequest(buildPurchaseUrl(), generateSignedParams(requestParams));
    }

    public VerifyResult verify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        if (params.isEmpty()) {
            return new VerifyResult("", "", "", "", "", false);
        }
        return verify(params);
    }

    public VerifyResult verify(Map<String, String> params) {
        Map<String, String> safeParams = new LinkedHashMap<>(params);
        String sign = safeParams.get("sign");
        String expectedSign = generateSignedParams(new LinkedHashMap<>(safeParams)).get("sign");
        return new VerifyResult(
                StrUtilCompat.nullToEmpty(safeParams.get("type")),
                StrUtilCompat.nullToEmpty(safeParams.get("trade_no")),
                StrUtilCompat.nullToEmpty(safeParams.get("out_trade_no")),
                StrUtilCompat.nullToEmpty(safeParams.get("name")),
                StrUtilCompat.nullToEmpty(safeParams.get("trade_status")),
                StrUtil.equals(sign, expectedSign)
        );
    }

    public Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            params.put(entry.getKey(), values == null || values.length == 0 ? "" : values[0]);
        }
        return params;
    }

    private String buildPurchaseUrl() {
        String payAddress = StrUtilCompat.blankToDefault(optionService.getValue(KEY_PAY_ADDRESS), "").trim();
        if (payAddress.isEmpty()) {
            throw new RuntimeException(I18nUtils.get("payment.gateway_url_not_configured"));
        }
        return StrUtil.removeSuffix(payAddress, "/") + PURCHASE_PATH;
    }

    private Map<String, String> generateSignedParams(Map<String, String> params) {
        String key = optionService.getValue(KEY_EPAY_KEY);
        if (StrUtil.isBlank(key)) {
            throw new RuntimeException(I18nUtils.get("payment.secret_key_not_configured"));
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramKey = entry.getKey();
            String value = entry.getValue();
            if ("sign".equals(paramKey) || "sign_type".equals(paramKey) || StrUtil.isBlank(value)) {
                continue;
            }
            filtered.put(paramKey, value);
        }
        List<Map.Entry<String, String>> entries = new ArrayList<>(filtered.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, String> entry = entries.get(i);
            if (i > 0) {
                builder.append('&');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        params.put("sign", md5(builder + key));
        params.put("sign_type", SIGN_TYPE_MD5);
        return params;
    }

    private String md5(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(I18nUtils.get("payment.md5_failed"), e);
        }
    }

    @Data
    @AllArgsConstructor
    public static class PurchaseRequest {
        private String url;
        private Map<String, String> params;
    }

    @Data
    @AllArgsConstructor
    public static class VerifyResult {
        private String type;
        private String tradeNo;
        private String serviceTradeNo;
        private String name;
        private String tradeStatus;
        private boolean verifyStatus;
    }
}
