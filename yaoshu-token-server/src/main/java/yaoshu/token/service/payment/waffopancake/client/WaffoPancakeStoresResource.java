package yaoshu.token.service.payment.waffopancake.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * Waffo Pancake Stores 端点封装（POST /v1/actions/store/create-store）。
 * <p>
 * 对齐 Go SDK {@code client.Stores.Create(ctx, pancake.CreateStoreParams{Name: ...})}。
 * 仅实现 create-store（Go pair 流程唯一调用的 stores 写操作；update-store / delete-store
 * Go 源码无调用，遵循翻译铁律不超前实现）。
 */
public class WaffoPancakeStoresResource {

    /** 创建门店端点（注意单数 store，非 stores） */
    private static final String PATH_CREATE_STORE = "/v1/actions/store/create-store";

    private final WaffoPancakeApiClient apiClient;

    public WaffoPancakeStoresResource(WaffoPancakeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 创建门店（对齐 Go CreateWaffoPancakePrimaryStore）。
     *
     * @param name       门店名称（1-48 字符，Pancake 自动 trim）
     * @param merchantId 商户 ID（MER_xxx）
     * @param privateKey RSA 私钥
     * @return 新建门店 ID（STO_xxx）
     * @throws WaffoPancakeApiException API 调用失败或响应缺失 store.id
     */
    public String createStore(String name, String merchantId, PrivateKey privateKey) {
        JSONObject req = new JSONObject();
        req.put("name", name);
        byte[] reqBytes = req.toJSONString().getBytes(StandardCharsets.UTF_8);

        String resp = apiClient.execute("POST", PATH_CREATE_STORE, reqBytes, merchantId, privateKey);
        return parseStoreId(resp);
    }

    private static String parseStoreId(String respJson) {
        JSONObject root = JSON.parseObject(respJson);
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new WaffoPancakeApiException("create-store response missing 'data': " + respJson);
        }
        JSONObject store = data.getJSONObject("store");
        if (store == null) {
            throw new WaffoPancakeApiException("create-store response missing 'data.store': " + respJson);
        }
        String storeId = store.getString("id");
        if (storeId == null || storeId.isBlank()) {
            throw new WaffoPancakeApiException("create-store response missing store.id: " + respJson);
        }
        return storeId;
    }
}
