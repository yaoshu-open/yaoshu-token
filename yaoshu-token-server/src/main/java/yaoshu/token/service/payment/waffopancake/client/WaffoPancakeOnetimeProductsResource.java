package yaoshu.token.service.payment.waffopancake.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;

/**
 * Waffo Pancake OnetimeProducts 端点封装。
 * <p>
 * 对齐 Go SDK {@code client.OnetimeProducts.Create / Publish}。仅实现 create-product +
 * publish-product（Go pair / productForPlan 流程调用的两个写操作；update-product / updateStatus
 * Go 源码无调用，遵循翻译铁律不超前实现）。
 * <p>
 * prices 对齐 Go SDK 只传 amount + taxCategory（Go 业务代码未设 taxIncluded，SDK 已验证可用）。
 */
public class WaffoPancakeOnetimeProductsResource {

    /** 创建商品端点（注意单数 onetime-product） */
    private static final String PATH_CREATE_PRODUCT = "/v1/actions/onetime-product/create-product";
    /** 发布商品端点（单向，test→prod） */
    private static final String PATH_PUBLISH_PRODUCT = "/v1/actions/onetime-product/publish-product";

    private final WaffoPancakeApiClient apiClient;

    public WaffoPancakeOnetimeProductsResource(WaffoPancakeApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 创建一次性商品（对齐 Go CreateWaffoPancakePrimaryProduct / CreateWaffoPancakeProductForPlan 的 Create 调用）。
     *
     * @param storeId    门店 ID（STO_xxx）
     * @param name       商品名称
     * @param amount     显示格式价格字符串（如 "1.00"）
     * @param successUrl 购买成功跳转 URL（空白则不传，对齐 Go optionalString）
     * @param merchantId 商户 ID（MER_xxx）
     * @param privateKey RSA 私钥
     * @return 新建商品 ID（PROD_xxx）
     * @throws WaffoPancakeApiException API 调用失败或响应缺失 product.id
     */
    public String createProduct(String storeId, String name, String amount, String successUrl,
                                String merchantId, PrivateKey privateKey) {
        JSONObject req = new JSONObject();
        req.put("storeId", storeId);
        req.put("name", name);
        // prices 对齐 Go SDK：只设 amount + taxCategory（taxIncluded 不传，Go 业务代码未设）
        JSONObject usdPrice = new JSONObject();
        usdPrice.put("amount", amount);
        usdPrice.put("taxCategory", "saas");
        JSONObject prices = new JSONObject();
        prices.put("USD", usdPrice);
        req.put("prices", prices);
        // 对齐 Go optionalString：空白时不传（null 让 Pancake 用门店默认）
        if (successUrl != null && !successUrl.isBlank()) {
            req.put("successUrl", successUrl);
        }
        byte[] reqBytes = req.toJSONString().getBytes(StandardCharsets.UTF_8);

        String resp = apiClient.execute("POST", PATH_CREATE_PRODUCT, reqBytes, merchantId, privateKey);
        return parseProductId(resp);
    }

    /**
     * 发布商品到生产环境（对齐 Go OnetimeProducts.Publish）。
     * <p>
     * 单向操作：将 test 版本复制到 prod。Go 返回值未使用，Java 同样不返回内容。
     *
     * @param productId  商品 ID（PROD_xxx）
     * @param merchantId 商户 ID（MER_xxx）
     * @param privateKey RSA 私钥
     * @throws WaffoPancakeApiException API 调用失败
     */
    public void publishProduct(String productId, String merchantId, PrivateKey privateKey) {
        JSONObject req = new JSONObject();
        req.put("id", productId);
        byte[] reqBytes = req.toJSONString().getBytes(StandardCharsets.UTF_8);

        apiClient.execute("POST", PATH_PUBLISH_PRODUCT, reqBytes, merchantId, privateKey);
    }

    private static String parseProductId(String respJson) {
        JSONObject root = JSON.parseObject(respJson);
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            throw new WaffoPancakeApiException("create-product response missing 'data': " + respJson);
        }
        JSONObject product = data.getJSONObject("product");
        if (product == null) {
            throw new WaffoPancakeApiException("create-product response missing 'data.product': " + respJson);
        }
        String productId = product.getString("id");
        if (productId == null || productId.isBlank()) {
            throw new WaffoPancakeApiException("create-product response missing product.id: " + respJson);
        }
        return productId;
    }
}
