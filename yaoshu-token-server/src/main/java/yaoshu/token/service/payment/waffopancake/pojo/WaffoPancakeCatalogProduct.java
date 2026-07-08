package yaoshu.token.service.payment.waffopancake.pojo;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake Catalog 商品条目（对齐 Go WaffoPancakeCatalogProduct）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeCatalogProduct {

    private String id;
    private String name;
    private String status;

    public static WaffoPancakeCatalogProduct fromJson(JSONObject json) {
        if (json == null) {
            return null;
        }
        return WaffoPancakeCatalogProduct.builder()
                .id(json.getString("id"))
                .name(json.getString("name"))
                .status(json.getString("status"))
                .build();
    }
}
