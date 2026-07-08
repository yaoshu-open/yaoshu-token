package yaoshu.token.pojo.vo;

import java.util.List;

import lombok.Data;

/**
 * 定价视图对象  * <p>
 * 前端定价接口返回的模型计算数据。
 */
@Data
public class PricingVO {

    private String modelName;
    private String description;
    private String icon;
    private String tags;
    private Integer vendorId;
    private int quotaType;
    private double modelRatio;
    private double modelPrice;
    private String ownerBy;
    private double completionRatio;
    private Double cacheRatio;
    private Double createCacheRatio;
    private Double imageRatio;
    private Double audioRatio;
    private Double audioCompletionRatio;
    private List<String> enableGroup;
    private List<String> supportedEndpointTypes;
    private String billingMode;
    private String billingExpr;
    private String pricingVersion;
}
