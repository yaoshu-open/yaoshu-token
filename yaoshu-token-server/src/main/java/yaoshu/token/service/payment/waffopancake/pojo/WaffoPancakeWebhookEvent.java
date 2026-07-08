package yaoshu.token.service.payment.waffopancake.pojo;

import com.alibaba.fastjson2.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Waffo Pancake Webhook 验签后事件（对齐 Go WaffoPancakeWebhookEvent）。
 * <p>
 * 由 {@link yaoshu.token.service.payment.waffopancake.client.WaffoPancakeWebhookVerifier}
 * 从原始 body 解析构造，业务层据 eventType 分发处理。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaffoPancakeWebhookEvent {

    /** 事件实体 ID（多数事件 = eventId） */
    private String id;

    /** 事件时间（ISO 8601 UTC） */
    private String timestamp;

    /** 事件类型（order.completed / subscription.activated 等，业务层按此分发） */
    private String eventType;

    /** 业务事件标识（按 eventType 映射到不同实体：order→PaymentID, subscription→OrderID） */
    private String eventId;

    /** 店铺 ID */
    private String storeId;

    /** 店铺名称 */
    private String storeName;

    /** 来源环境（"test" / "prod"，必须与验签公钥环境一致） */
    private String mode;

    /** 事件数据 */
    private WaffoPancakeWebhookData data;

    /**
     * 从 Pancake 原始 JSON 构造事件（驼形 key，绕过 Convert SNAKE_CASE 自动映射）。
     */
    public static WaffoPancakeWebhookEvent fromJson(JSONObject root) {
        if (root == null) {
            return null;
        }
        WaffoPancakeWebhookData data = WaffoPancakeWebhookData.fromJson(root.getJSONObject("data"));
        return WaffoPancakeWebhookEvent.builder()
                .id(root.getString("id"))
                .timestamp(root.getString("timestamp"))
                .eventType(root.getString("eventType"))
                .eventId(root.getString("eventId"))
                .storeId(root.getString("storeId"))
                .storeName(root.getString("storeName"))
                .mode(root.getString("mode"))
                .data(data)
                .build();
    }
}
