package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.pojo.dto.ErrorCode;
import yaoshu.token.pojo.dto.RelayException;

/**
 * 违规扣费服务  * <p>
 * 处理 Grok 等渠道的 CSAM（儿童安全内容）违规检测标记。
 * 当检测到违规内容时，将错误码标准化为 violation_fee.* 系列，
 * 并在结算时对违规请求进行扣费。
 */
@Slf4j
public final class ViolationFeeService {

    private ViolationFeeService() {
    }

    /** 违规扣费错误码前缀 */
    public static final String VIOLATION_FEE_CODE_PREFIX = "violation_fee.";

    /** CSAM 违规标记 */
    private static final String CSAM_VIOLATION_MARKER = "Failed check: SAFETY_CHECK_TYPE";

    /** 内容违规使用指南标记 */
    private static final String CONTENT_VIOLATES_USAGE_MARKER = "Content violates usage guidelines";

    /**
     * 判断是否为违规扣费错误码      */
    public static boolean isViolationFeeCode(String errorCode) {
        return errorCode != null && errorCode.startsWith(VIOLATION_FEE_CODE_PREFIX);
    }

    /**
     * 检查错误是否包含 CSAM 违规标记      */
    public static boolean hasCSAMViolationMarker(RelayException err) {
        if (err == null) return false;
        String msg = err.getMessage();
        if (msg == null) return false;
        return msg.contains(CSAM_VIOLATION_MARKER) || msg.contains(CONTENT_VIOLATES_USAGE_MARKER);
    }

    /**
     * 将错误包装为 Grok CSAM 违规扣费错误      */
    public static RelayException wrapAsViolationFeeGrokCSAM(RelayException err) {
        if (err == null) return null;
        err.setErrorType(ErrorCode.VIOLATION_FEE_GROK_CSAM);
        err.setErrorCode(ErrorCode.VIOLATION_FEE_GROK_CSAM);
        err.setSkipRetry(true);
        return err;
    }

    /**
     * 标准化违规扣费错误      * <p>
     * 在重试决策之前调用：
     * - 若包含 CSAM 标记 → 包装为 Grok CSAM 违规扣费错误
     * - 若错误码已是 violation_fee.* → 启用 skipRetry
     */
    public static RelayException normalizeViolationFeeError(RelayException err) {
        if (err == null) return null;

        if (hasCSAMViolationMarker(err)) {
            return wrapAsViolationFeeGrokCSAM(err);
        }

        if (isViolationFeeCode(err.getErrorCode())) {
            err.setSkipRetry(true);
            return err;
        }

        return err;
    }

    /**
     * 判断是否应对此错误收取违规扣费      */
    public static boolean shouldChargeViolationFee(RelayException err) {
        if (err == null) return false;
        if (ErrorCode.VIOLATION_FEE_GROK_CSAM.equals(err.getErrorCode())) {
            return true;
        }
        // 兜底检查（某些调用方可能未调用 normalize）
        return hasCSAMViolationMarker(err);
    }

    /**
     * 计算违规扣费配额      *
     * @param amount      扣费金额
     * @param groupRatio  分组比率
     * @return 配额消耗量
     */
    public static int calcViolationFeeQuota(double amount, double groupRatio) {
        if (amount <= 0 || groupRatio <= 0) return 0;
        double quota = amount * CommonConstants.quotaPerUnit * groupRatio;
        return (int) Math.round(quota);
    }
}
