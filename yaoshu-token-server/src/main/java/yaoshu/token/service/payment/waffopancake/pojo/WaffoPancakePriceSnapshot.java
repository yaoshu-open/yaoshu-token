package yaoshu.token.service.payment.waffopancake.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake checkout 价格覆盖（对齐 Go WaffoPancakePriceSnapshot）。
 * <p>
 * 对应 Pancake API priceSnapshot 字段，覆盖商品预设价格（动态定价场景）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaffoPancakePriceSnapshot {

    /** 金额（2 位小数字符串，如 "29.00"；对齐 Go 用 shopspring/decimal） */
    private String amount;

    /** 税务类别（如 "saas" / "digital_goods" / "ebook" 等） */
    private String taxCategory;
}
