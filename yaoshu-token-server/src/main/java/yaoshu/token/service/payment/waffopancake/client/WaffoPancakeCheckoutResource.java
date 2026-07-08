package yaoshu.token.service.payment.waffopancake.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCheckoutSession;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCreateSessionParams;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * Waffo Pancake Checkout 端点封装（POST /v1/actions/checkout/create-session）。
 * <p>
 * 实现 Pancake「Authenticated Checkout」两步流程：
 * 1. POST /v1/actions/auth/issue-session-token（API Key 签名，body={productId, buyerIdentity}）→ 拿 buyer session token
 * 2. POST /v1/actions/checkout/create-session（Bearer token + 业务参数）→ 拿 checkoutUrl
 * <p>
 * 对齐 Go SDK {@code client.Checkout.Authenticated.Create(ctx, sdkParams)} 的一步封装行为。
 */
public class WaffoPancakeCheckoutResource {

    /** Issue session token 端点（API Key 签名） */
    private static final String PATH_ISSUE_TOKEN = "/v1/actions/auth/issue-session-token";
    /** Create checkout session 端点（Bearer token） */
    private static final String PATH_CREATE_SESSION = "/v1/actions/checkout/create-session";

    private final WaffoPancakeApiClient apiClient;

    public WaffoPancakeCheckoutResource(WaffoPancakeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 创建 authenticated checkout 会话（两步：issue token + create session）。
     *
     * @param params     checkout 入参（productId/buyerIdentity/orderMerchantExternalId 必填）
     * @param merchantId 商户 ID（MER_xxx）
     * @param privateKey RSA 私钥
     * @return checkout 会话（含 checkoutUrl）
     * @throws WaffoPancakeApiException API 调用失败
     */
    public WaffoPancakeCheckoutSession createAuthenticatedSession(WaffoPancakeCreateSessionParams params,
                                                                  String merchantId,
                                                                  PrivateKey privateKey) {
        if (params == null) {
            throw new WaffoPancakeApiException("Checkout params is null");
        }
        if (isBlank(params.getBuyerIdentity())) {
            throw new WaffoPancakeApiException("Missing buyer identity");
        }
        if (isBlank(params.getOrderMerchantExternalId())) {
            throw new WaffoPancakeApiException("Missing order merchant external id");
        }

        // 步骤 1: issue-session-token（API Key 签名）
        JSONObject tokenReq = new JSONObject();
        tokenReq.put("productId", params.getProductId());
        tokenReq.put("buyerIdentity", params.getBuyerIdentity());
        byte[] tokenReqBytes = tokenReq.toJSONString().getBytes(StandardCharsets.UTF_8);
        String tokenResp = apiClient.execute("POST", PATH_ISSUE_TOKEN, tokenReqBytes, merchantId, privateKey);
        String sessionToken = extractSessionToken(tokenResp);

        // 步骤 2: create-session（业务参数）
        JSONObject sessionReq = buildCreateSessionRequest(params);
        byte[] sessionReqBytes = sessionReq.toJSONString().getBytes(StandardCharsets.UTF_8);
        String sessionResp = apiClient.execute("POST", PATH_CREATE_SESSION, sessionReqBytes, merchantId, privateKey);
        return parseCheckoutSession(sessionResp, sessionToken);
    }

    private static String extractSessionToken(String respJson) {
        JSONObject root = JSON.parseObject(respJson);
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new WaffoPancakeApiException("issue-session-token response missing 'data': " + respJson);
        }
        String token = data.getString("token");
        if (isBlank(token)) {
            throw new WaffoPancakeApiException("issue-session-token response missing token: " + respJson);
        }
        return token;
    }

    private static JSONObject buildCreateSessionRequest(WaffoPancakeCreateSessionParams params) {
        JSONObject req = new JSONObject();
        req.put("productId", params.getProductId());
        req.put("productType", "onetime");  // 充值默认 onetime；订阅套餐也用 onetime 代订阅（RFC §3.5）
        req.put("currency", "USD");
        if (!isBlank(params.getBuyerEmail())) {
            req.put("buyerEmail", params.getBuyerEmail());
        }
        if (params.getExpiresInSeconds() != null && params.getExpiresInSeconds() > 0) {
            req.put("expiresInSeconds", params.getExpiresInSeconds());
        }
        if (!isBlank(params.getOrderMerchantExternalId())) {
            req.put("orderMerchantExternalId", params.getOrderMerchantExternalId());
        }
        if (params.getPriceSnapshot() != null) {
            JSONObject priceSnapshot = new JSONObject();
            priceSnapshot.put("amount", params.getPriceSnapshot().getAmount());
            priceSnapshot.put("taxCategory", params.getPriceSnapshot().getTaxCategory());
            req.put("priceSnapshot", priceSnapshot);
        }
        return req;
    }

    private static WaffoPancakeCheckoutSession parseCheckoutSession(String respJson, String sessionToken) {
        JSONObject root = JSON.parseObject(respJson);
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new WaffoPancakeApiException("create-session response missing 'data': " + respJson);
        }
        String sessionId = data.getString("sessionId");
        String checkoutUrl = data.getString("checkoutUrl");
        if (isBlank(sessionId) || isBlank(checkoutUrl)) {
            throw new WaffoPancakeApiException("create-session response missing sessionId/checkoutUrl: " + respJson);
        }
        return WaffoPancakeCheckoutSession.builder()
                .sessionId(sessionId)
                .checkoutUrl(checkoutUrl)
                .expiresAt(data.getString("expiresAt"))
                .token(sessionToken)
                .tokenExpiresAt(data.getString("tokenExpiresAt"))
                .build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 兼容遗留字段名访问（部分 Pancake 响应字段名可能是 session_id 蛇形）。
     */
    @SuppressWarnings("unused")
    private static String getStringFlexible(JSONObject json, String... candidates) {
        for (String key : candidates) {
            String v = json.getString(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    private static JSONArray getArrayFlexible(JSONObject json, String... candidates) {
        for (String key : candidates) {
            JSONArray v = json.getJSONArray(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
