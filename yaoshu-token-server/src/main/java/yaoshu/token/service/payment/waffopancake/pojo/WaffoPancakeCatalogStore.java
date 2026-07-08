package yaoshu.token.service.payment.waffopancake.pojo;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Waffo Pancake Catalog 店铺条目（对齐 Go WaffoPancakeCatalogStore）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeCatalogStore {

    private String id;
    private String name;
    private String status;

    /** 是否已启用 prod 环境（test 店铺默认 false） */
    private Boolean prodEnabled;

    /** 店铺下的一次性商品（已过滤 status != "active" 的商品） */
    private List<WaffoPancakeCatalogProduct> onetimeProducts;

    public static WaffoPancakeCatalogStore fromJson(JSONObject json) {
        if (json == null) {
            return null;
        }
        List<WaffoPancakeCatalogProduct> products = new ArrayList<>();
        JSONArray arr = json.getJSONArray("onetimeProducts");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JSONObject p = arr.getJSONObject(i);
                String status = p.getString("status");
                // 对齐 Go：过滤 status != "active" 的商品
                if (status != null && "active".equalsIgnoreCase(status.trim())) {
                    products.add(WaffoPancakeCatalogProduct.fromJson(p));
                }
            }
        }
        return WaffoPancakeCatalogStore.builder()
                .id(json.getString("id"))
                .name(json.getString("name"))
                .status(json.getString("status"))
                .prodEnabled(json.getBoolean("prodEnabled"))
                .onetimeProducts(products)
                .build();
    }
}
