package yaoshu.token.service.passkey;

import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import yaoshu.token.config.SystemSettingConfig;
import yaoshu.token.mapper.PasskeyMapper;
import yaoshu.token.pojo.entity.PasskeyCredential;

import java.util.*;

/**
 * Passkey WebAuthn 核心服务  * <p>
 * 构建 Yubico RelyingParty 实例，封装注册/登录认证操作。
 */
@Slf4j
@Service
public class PasskeyService {

    private final PasskeyMapper passkeyMapper;
    private final PasskeyCredentialRepository credentialRepository;

    public PasskeyService(PasskeyMapper passkeyMapper) {
        this.passkeyMapper = passkeyMapper;
        this.credentialRepository = new PasskeyCredentialRepository(passkeyMapper);
    }

    /**
     * 构建 RelyingParty 实例      */
    public RelyingParty buildRelyingParty(HttpServletRequest request) {
        SystemSettingConfig.PasskeySetting settings = SystemSettingConfig.PasskeySetting.current();
        if (settings == null || !settings.isEnabled()) return null;

        // 解析 RP ID
        String rpId = resolveRpId(request, settings);
        String rpName = settings.getRpName() != null && !settings.getRpName().isEmpty()
                ? settings.getRpName() : "爻枢 Token";

        // 解析 Origins
        Set<String> origins = resolveOrigins(request, settings);

        // AuthenticatorSelection
        AuthenticatorSelectionCriteria authSelection = AuthenticatorSelectionCriteria.builder()
                .residentKey(ResidentKeyRequirement.REQUIRED)
                .userVerification(parseUserVerification(settings.getUserVerification()))
                .build();

        if (settings.getAttachmentPreference() != null && !settings.getAttachmentPreference().isEmpty()) {
            authSelection = authSelection.toBuilder()
                    .authenticatorAttachment(AuthenticatorAttachment.valueOf(
                            settings.getAttachmentPreference().toUpperCase()))
                    .build();
        }

        return RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(rpId).name(rpName).build())
                .credentialRepository(credentialRepository)
                .origins(origins)
                .allowOriginPort(true)
                .build();
    }

    /** 开始注册 */
    public PublicKeyCredentialCreationOptions startRegistration(RelyingParty rp, int userId,
                                                                  String username, String displayName) {
        PasskeyCredential existing = passkeyMapper.selectByUserId(userId);
        byte[] userHandle = String.valueOf(userId).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        UserIdentity userIdentity = UserIdentity.builder()
                .name(username)
                .displayName(displayName != null && !displayName.isEmpty() ? displayName : username)
                .id(new ByteArray(userHandle))
                .build();

        StartRegistrationOptions.StartRegistrationOptionsBuilder builder = StartRegistrationOptions.builder()
                .user(userIdentity)
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.PREFERRED)
                        .build());

        // Yubico WebAuthn 库会自动从 credentialRepository 查询已有凭证并加入排除列表，
        // 无需像 Go webauthn.WithExclusions 那样手动传入。existing 查询仅用于业务判断。

        return rp.startRegistration(builder.build());
    }

    /** 完成注册 */
    public RegistrationResult finishRegistration(RelyingParty rp, PublicKeyCredentialCreationOptions request,
                                                  String responseJson) throws Exception {
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                PublicKeyCredential.parseRegistrationResponseJson(responseJson);
        return rp.finishRegistration(FinishRegistrationOptions.builder()
                .request(request)
                .response(pkc)
                .build());
    }

    /** 开始登录（Discoverable） */
    public AssertionRequest startDiscoverableLogin(RelyingParty rp) {
        return rp.startAssertion(StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build());
    }

    /** 开始登录（非 Discoverable，指定 userId） */
    public AssertionRequest startLogin(RelyingParty rp, int userId) {
        return rp.startAssertion(StartAssertionOptions.builder()
                .username(String.valueOf(userId))
                .userVerification(UserVerificationRequirement.PREFERRED)
                .build());
    }

    /**
     * 完成 Passkey 登录（含用户发现回调）      * <p>
     * handler 通过 credentialId 查找用户并返回 AssertionResult
     */
    public AssertionResult finishPasskeyLogin(RelyingParty rp, AssertionRequest request,
                                               String responseJson,
                                               java.util.function.BiFunction<ByteArray, ByteArray, PasskeyUser> handler)
            throws Exception {
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(responseJson);

        return rp.finishAssertion(FinishAssertionOptions.builder()
                .request(request)
                .response(pkc)
                .build());
    }

    /** 完成登录验证（指定 userId） */
    public AssertionResult finishLogin(RelyingParty rp, AssertionRequest request,
                                        String responseJson) throws Exception {
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(responseJson);
        return rp.finishAssertion(FinishAssertionOptions.builder()
                .request(request)
                .response(pkc)
                .build());
    }

    // ======================== 内部方法 ========================

    private String resolveRpId(HttpServletRequest request, SystemSettingConfig.PasskeySetting settings) {
        if (settings.getRpId() != null && !settings.getRpId().isEmpty()) {
            return settings.getRpId();
        }
        // 从 Host header 推导
        String host = request.getHeader("Host");
        if (host != null) {
            int colonIdx = host.indexOf(':');
            return colonIdx > 0 ? host.substring(0, colonIdx) : host;
        }
        return "localhost";
    }

    private Set<String> resolveOrigins(HttpServletRequest request, SystemSettingConfig.PasskeySetting settings) {
        if (settings.getOrigin() != null && !settings.getOrigin().isEmpty()) {
            return new HashSet<>(Arrays.asList(settings.getOrigin().split(",")));
        }
        // 从请求推导
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isEmpty()) scheme = request.getScheme();
        String host = request.getHeader("Host");
        if (host == null) host = "localhost";
        return Set.of(scheme + "://" + host);
    }

    private UserVerificationRequirement parseUserVerification(String uv) {
        if (uv == null) return UserVerificationRequirement.PREFERRED;
        return switch (uv.toLowerCase()) {
            case "required" -> UserVerificationRequirement.REQUIRED;
            case "discouraged" -> UserVerificationRequirement.DISCOURAGED;
            default -> UserVerificationRequirement.PREFERRED;
        };
    }

    /**
     * Passkey 登录/认证的用户包装结果
     */
    public record PasskeyUser(int userId, String username, String displayName, int status) {}
}
