package yaoshu.token.service.payment.waffopancake.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalog;
import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakeCatalogStore;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Waffo Pancake GraphQL 端点封装（POST /v1/graphql）。
 * <p>
 * 首期用途：catalog 探针查询（{@code stores(limit: 100) {...}}），双用途为凭证校验 + 商品列表。
 * 对齐 Go {@code ListWaffoPancakeCatalog} 实现。
 */
public class WaffoPancakeGraphqlResource {

    private static final String PATH_GRAPHQL = "/v1/graphql";

    /**
     * catalog 查询语句（limit: 100 必填，默认仅返回单店）。
     * 字段对齐 Go 实现：id / name / status / prodEnabled / onetimeProducts { id name status }
     */
    public static final String CATALOG_QUERY = "query {\n" +
            "    stores(limit: 100) {\n" +
            "        id\n" +
            "        name\n" +
            "        status\n" +
            "        prodEnabled\n" +
            "        onetimeProducts {\n" +
            "            id\n" +
            "            name\n" +
            "            status\n" +
            "        }\n" +
            "    }\n" +
            "}";

    private final WaffoPancakeApiClient apiClient;

    public WaffoPancakeGraphqlResource(WaffoPancakeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 查询 catalog（凭证探针 + 商品选择数据源）。
     * <p>
     * 成功响应 = 凭证有效；失败响应（401/网络错误等）= 凭证无效或网络问题。
     *
     * @param merchantId 商户 ID（MER_xxx）
     * @param privateKey RSA 私钥
     * @return catalog（含店铺与 active 状态的 onetimeProducts）
     * @throws WaffoPancakeApiException API 或 GraphQL 错误
     */
    public WaffoPancakeCatalog queryCatalog(String merchantId, PrivateKey privateKey) {
        JSONObject req = new JSONObject();
        req.put("query", CATALOG_QUERY);
        byte[] reqBytes = req.toJSONString().getBytes(StandardCharsets.UTF_8);

        String resp = apiClient.execute("POST", PATH_GRAPHQL, reqBytes, merchantId, privateKey);
        return parseCatalogResponse(resp);
    }

    private static WaffoPancakeCatalog parseCatalogResponse(String respJson) {
        JSONObject root = JSON.parseObject(respJson);
        // GraphQL 错误响应：{ data: null, errors: [{message, ...}] }
        JSONArray errors = root.getJSONArray("errors");
        if (errors != null && !errors.isEmpty()) {
            Object first = errors.get(0);
            String msg = first instanceof JSONObject ? ((JSONObject) first).getString("message") : String.valueOf(first);
            throw new WaffoPancakeApiException("Waffo Pancake GraphQL returned " + errors.size() + " errors: " + msg);
        }
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new WaffoPancakeApiException("Waffo Pancake GraphQL response missing 'data': " + respJson);
        }
        JSONArray storesArr = data.getJSONArray("stores");
        List<WaffoPancakeCatalogStore> stores = new ArrayList<>();
        if (storesArr != null) {
            for (int i = 0; i < storesArr.size(); i++) {
                JSONObject s = storesArr.getJSONObject(i);
                stores.add(WaffoPancakeCatalogStore.fromJson(s));
            }
        }
        return WaffoPancakeCatalog.builder().stores(stores).build();
    }
}
