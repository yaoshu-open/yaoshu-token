package yaoshu.token.service.passkey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.mapper.PasskeyMapper;
import yaoshu.token.pojo.entity.PasskeyCredential;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.service.UserService;

/**
 * Passkey 用户服务  * <p>
 * 管理 WebAuthn/FIDO2 用户凭证存储与查找。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasskeyUserService {

    private final PasskeyMapper passkeyMapper;
    private final UserService userService;

    /**
     * 根据用户名查找用户 ID      *
     * @param username 用户名
     * @return 用户 ID，未找到返回 0
     */
    public int getUserIDByUsername(String username) {
        if (username == null || username.isBlank()) {
            return 0;
        }
        User user = userService.findByUsernameOrEmail(username);
        if (user == null || user.getId() == null) {
            return 0;
        }
        return user.getId();
    }

    /**
     * 检查用户是否已注册 Passkey      *
     * @param userId 用户 ID
     * @return true=已注册 Passkey
     */
    public boolean hasPasskey(int userId) {
        if (userId <= 0) {
            return false;
        }
        PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
        return credential != null;
    }

    /**
     * 根据用户 ID 获取 Passkey 凭证      *
     * @param userId 用户 ID
     * @return 凭证记录，未找到返回 null
     */
    public PasskeyCredential getPasskeyByUserId(int userId) {
        if (userId <= 0) {
            return null;
        }
        return passkeyMapper.selectByUserId(userId);
    }

    /**
     * 根据凭证 ID 获取 Passkey 凭证      *
     * @param credentialId 凭证 ID（base64 编码）
     * @return 凭证记录，未找到返回 null
     */
    public PasskeyCredential getPasskeyByCredentialId(String credentialId) {
        if (credentialId == null || credentialId.isEmpty()) {
            return null;
        }
        return passkeyMapper.selectByCredentialId(credentialId);
    }

    /**
     * 存储用户 Passkey 凭证（Upsert 语义）      * <p>
     * 先删除该用户已有凭证（硬删除，避免唯一索引冲突），再插入新凭证。
     *
     * @param credential 凭证记录
     */
    public void storePasskey(PasskeyCredential credential) {
        if (credential == null || credential.getUserId() == null) {
            throw new IllegalArgumentException("credential or userId is null");
        }
        // 先删除已有凭证（Unscoped 硬删除语义，MyBatis-Plus 逻辑删除字段 deleted_at 不影响 @Delete 原生 SQL）
        passkeyMapper.deleteByUserId(credential.getUserId());
        // 插入新凭证
        long now = System.currentTimeMillis() / 1000;
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        if (credential.getLastUsedAt() == null) {
            credential.setLastUsedAt(now);
        }
        passkeyMapper.insert(credential);
        log.info("passkey stored for user {}", credential.getUserId());
    }

    /**
     * 存储用户 Passkey 凭证（简化签名）      *
     * @param userId       用户 ID
     * @param credentialId 凭证 ID
     * @param publicKey    公钥
     * @param signCount    签名计数
     */
    public void storePasskey(int userId, String credentialId, byte[] publicKey, int signCount) {
        PasskeyCredential credential = new PasskeyCredential();
        credential.setUserId(userId);
        credential.setCredentialId(credentialId);
        credential.setPublicKey(java.util.Base64.getEncoder().encodeToString(publicKey));
        credential.setSignCount((long) signCount);
        storePasskey(credential);
    }

    /**
     * 删除用户的 Passkey 凭证      *
     * @param userId 用户 ID
     * @return true=删除成功
     */
    public boolean deletePasskeyByUserId(int userId) {
        if (userId <= 0) {
            return false;
        }
        return passkeyMapper.deleteByUserId(userId) > 0;
    }

    /**
     * 更新凭证的签名计数和最后使用时间（登录验证后调用）
     *
     * @param credentialId 凭证 ID（base64）
     * @param signCount    新的签名计数
     */
    public void updateSignCount(String credentialId, long signCount) {
        PasskeyCredential credential = passkeyMapper.selectByCredentialId(credentialId);
        if (credential == null) return;
        if (signCount > (credential.getSignCount() != null ? credential.getSignCount() : 0)) {
            credential.setSignCount(signCount);
        }
        long now = System.currentTimeMillis() / 1000;
        credential.setLastUsedAt(now);
        credential.setUpdatedAt(now);
        passkeyMapper.updateById(credential);
    }
}
