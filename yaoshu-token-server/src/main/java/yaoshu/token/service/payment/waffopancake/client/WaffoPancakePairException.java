package yaoshu.token.service.payment.waffopancake.client;

import yaoshu.token.service.payment.waffopancake.pojo.WaffoPancakePairResult;

/**
 * Waffo Pancake 配对半成功异常（store 创建成功但 product 创建失败）。
 * <p>
 * Java 等价 Go {@code (result, error)} tuple 中 result != nil 且 result.OrphanStore = true 的场景。
 * 携带 {@link #partialResult}（含已创建的 storeId/storeName），
 * 供 Controller 提取后向前端返回孤儿 store 上下文（对齐 Go controller 的 orphan 响应）。
 */
public class WaffoPancakePairException extends RuntimeException {

    private final WaffoPancakePairResult partialResult;

    public WaffoPancakePairException(String message, WaffoPancakePairResult partialResult) {
        super(message);
        this.partialResult = partialResult;
    }

    public WaffoPancakePairResult getPartialResult() {
        return partialResult;
    }
}
