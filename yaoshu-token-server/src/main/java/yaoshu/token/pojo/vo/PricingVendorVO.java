package yaoshu.token.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 供应商视图对象  */
@Data
@AllArgsConstructor
public class PricingVendorVO {

    private Integer id;
    private String name;
    private String description;
    private String icon;
}
