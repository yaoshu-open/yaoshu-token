package yaoshu.token.service.payment.waffopancake.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Waffo Pancake GraphQL Catalog 响应（对齐 Go WaffoPancakeCatalog）。
 * <p>
 * 由 {@code stores(limit: 100)} 查询返回，作为管理端凭证探针与商品选择数据源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeCatalog {

    /** 店铺列表（limit: 100 必填，默认仅返回单店） */
    private List<WaffoPancakeCatalogStore> stores;
}
