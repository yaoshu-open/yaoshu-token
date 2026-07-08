package yaoshu.token.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import yaoshu.token.mapper.TwoFaBackupCodeMapper;
import yaoshu.token.pojo.entity.TwoFaBackupCode;

import java.util.List;

/**
 * 双因素认证备用码校验 Helper。  * <p>
 * 统一承载 TwoFaController 与 VerificationController 中同源的备用码校验逻辑。
 */
@Component
@RequiredArgsConstructor
public class TwoFaBackupCodeHelper {

    private final TwoFaBackupCodeMapper twoFaBackupCodeMapper;

    /**
     * 校验备用码：比对哈希后标记为已使用。
     *
     * @param userId 用户 ID
     * @param code   待校验的备用码（明文，XXXX-XXXX 格式或无连字符）
     * @return 校验通过且已标记已使用返回 true；否则 false
     */
    public boolean verifyBackupCode(Integer userId, String code) {
        List<TwoFaBackupCode> codes = twoFaBackupCodeMapper.getUnusedCodesByUserId(userId);
        if (codes == null || codes.isEmpty()) return false;

        String normalized = TotpService.normalizeBackupCode(code);
        String hash = TotpService.hashBackupCode(normalized);
        for (TwoFaBackupCode backupCode : codes) {
            if (hash.equals(backupCode.getCodeHash())) {
                twoFaBackupCodeMapper.markAsUsed(backupCode.getId());
                return true;
            }
        }
        return false;
    }
}
