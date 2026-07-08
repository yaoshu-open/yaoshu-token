package yaoshu.token.service.passkey;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.mapper.PasskeyMapper;
import yaoshu.token.pojo.entity.PasskeyCredential;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Yubico WebAuthn CredentialRepository 实现  * <p>
 * 将数据库中的 PasskeyCredential 映射为 Yubico 库所需的 RegisteredCredential。
 */
@Slf4j
@RequiredArgsConstructor
public class PasskeyCredentialRepository implements CredentialRepository {

    private final PasskeyMapper passkeyMapper;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        // username 字段实际承载数字 userId（历史协议）
        try {
            int userId = Integer.parseInt(username);
            PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
            if (credential == null) return Collections.emptySet();
            return Set.of(PublicKeyCredentialDescriptor.builder()
                    .id(ByteArray.fromBase64(credential.getCredentialId()))
                    .build());
        } catch (NumberFormatException e) {
            return Collections.emptySet();
        }
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        try {
            int userId = Integer.parseInt(username);
            return Optional.of(new ByteArray(String.valueOf(userId).getBytes(StandardCharsets.UTF_8)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        if (userHandle == null) return Optional.empty();
        try {
            String userIdStr = new String(userHandle.getBytes(), StandardCharsets.UTF_8);
            int userId = Integer.parseInt(userIdStr);
            PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
            if (credential != null) return Optional.of(String.valueOf(credential.getUserId()));
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        if (credentialId == null) return Optional.empty();
        String credIdB64 = credentialId.getBase64();
        PasskeyCredential credential = passkeyMapper.selectByCredentialId(credIdB64);
        if (credential == null) return Optional.empty();

        return Optional.of(RegisteredCredential.builder()
                .credentialId(ByteArray.fromBase64(credential.getCredentialId()))
                .userHandle(new ByteArray(String.valueOf(credential.getUserId()).getBytes(StandardCharsets.UTF_8)))
                .publicKeyCose(ByteArray.fromBase64(credential.getPublicKey()))
                .signatureCount(credential.getSignCount())
                .build());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray userHandle) {
        if (userHandle == null) return Collections.emptySet();
        try {
            int userId = Integer.parseInt(new String(userHandle.getBytes(), StandardCharsets.UTF_8));
            PasskeyCredential credential = passkeyMapper.selectByUserId(userId);
            if (credential == null) return Collections.emptySet();
            return Set.of(RegisteredCredential.builder()
                    .credentialId(ByteArray.fromBase64(credential.getCredentialId()))
                    .userHandle(userHandle)
                    .publicKeyCose(ByteArray.fromBase64(credential.getPublicKey()))
                    .signatureCount(credential.getSignCount())
                    .build());
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
}
