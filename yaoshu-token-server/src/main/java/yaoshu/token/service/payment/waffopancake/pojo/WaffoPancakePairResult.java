package yaoshu.token.service.payment.waffopancake.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake 配对结果（对齐 Go WaffoPancakePairResult）。
 * <p>
 * 当 {@link #orphanStore} 为 true 时，store 已创建但 product 创建失败，
 * store 成为"孤儿"需运营侧手动清理。Controller 据此向前端返回半成功上下文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakePairResult {

    private String storeId;
    private String storeName;
    private String productId;
    private String productName;

    /** store 创建成功但 product 创建失败时为 true（半成功孤儿标志） */
    private boolean orphanStore;
}
