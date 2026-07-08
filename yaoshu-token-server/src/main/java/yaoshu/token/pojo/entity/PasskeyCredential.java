package yaoshu.token.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.data.AuthenticatorTransport;
import lombok.Data;

/**
 * Passkey 凭证实测  *
 * @author yaoshu
 */
@Data
@TableName("passkey_credentials")
public class PasskeyCredential {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private String credentialId;

    private String publicKey;

    private String attestationType;

    private String aaguid;

    private Long signCount;

    private Boolean cloneWarning;

    private Boolean userPresent;

    private Boolean userVerified;

    private Boolean backupEligible;

    private Boolean backupState;

    private String transports;
    private String attachment;

    private Long lastUsedAt;

    private Long createdAt;

    private Long updatedAt;

    private Long deletedAt;

    // ======================== 工厂方法 ========================

    /**
     * 从 Yubico RegistrationResult 构建 PasskeyCredential      */
    public static PasskeyCredential fromRegistrationResult(int userId, RegistrationResult result) {
        PasskeyCredential p = new PasskeyCredential();
        p.userId = userId;
        p.credentialId = result.getKeyId().getId().getBase64();
        p.publicKey = result.getPublicKeyCose().getBase64();
        p.attestationType = result.getAttestationType().name();
        p.signCount = result.getSignatureCount();

        if (result.getAaguid() != null && !result.getAaguid().isEmpty()) {
            p.aaguid = result.getAaguid().getBase64();
        }
        if (result.getKeyId().getTransports().isPresent()) {
            p.transports = String.join(",",
                    result.getKeyId().getTransports().get().stream()
                        .map(AuthenticatorTransport::toString).toList());
        }
        // 布尔字段默认 false
        p.cloneWarning = false;
        p.userPresent = true;
        p.userVerified = true;
        p.backupEligible = false;
        p.backupState = false;

        long now = System.currentTimeMillis() / 1000;
        p.createdAt = now;
        p.updatedAt = now;
        p.lastUsedAt = now;
        return p;
    }

    /**
     * 从 Yubico AssertionResult 更新凭证信息
     */
    public void updateFromAssertion(com.yubico.webauthn.AssertionResult result) {
        if (result.getSignatureCount() > this.signCount) {
            this.signCount = result.getSignatureCount();
        }
        this.lastUsedAt = System.currentTimeMillis() / 1000;
        this.updatedAt = System.currentTimeMillis() / 1000;
    }
}
