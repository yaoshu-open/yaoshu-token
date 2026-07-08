package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Token;
import yaoshu.token.pojo.ipo.TokenIPO;
import yaoshu.token.service.TokenService;

import java.security.SecureRandom;
import java.util.*;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * Token 管理控制器  * <p>
 * 认证：UserAuth（getAll/search/get/add/update/delete/deleteBatch/getKeysBatch）
 */
@Slf4j
@RestController
@SaCheckLogin
@RequestMapping("/api/token")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;
    private static final String KEY_PREFIX = "sk-";
    private static final int KEY_LENGTH = 48;
    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ======================== 查询 ========================

    /**
     * 获取用户的所有 Token（分页+掩码）      */
    @GetMapping("/")
    public Result<?> getAll(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        List<Token> tokens = tokenService.getAllByUserId(userId);
        // 掩码 key（原地替换，保留 PageInfo 的 total）
        PageInfo<Token> pageInfo = PageInfo.of(tokens);
        pageInfo.getList().replaceAll(this::maskKey);
        return R.success(pageInfo);
    }

    /**
     * 搜索用户的 Token      */
    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false, name = "token") String tokenKey,
                                       HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        List<Token> tokens = tokenService.searchByUserId(userId, keyword, tokenKey);
        // 掩码 key（原地替换，保留 PageInfo 的 total）
        PageInfo<Token> pageInfo = PageInfo.of(tokens);
        pageInfo.getList().replaceAll(this::maskKey);
        return R.success(pageInfo);
    }

    /**
     * 获取单个 Token（掩码 key）      */
    @GetMapping("/{id}")
    public Result<?> get(@PathVariable int id, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        Token token = tokenService.getByIdAndUser(id, userId);
        if (token == null) {
            throw new ResultException(R.errorPrompt("Token 不存在"));
        }
        return R.success(maskKey(token));
    }

    /**
     * 查看 Token 完整密钥      * Go: RootAuth + CriticalRateLimit + DisableCache + SecureVerificationRequired
     */
    @PostMapping("/{id}/key")
    public Result<?> getKey(@PathVariable int id, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        Token token = tokenService.getByIdAndUser(id, userId);
        if (token == null) {
            throw new ResultException(R.errorPrompt("Token 不存在"));
        }

        return R.success(Map.of("key", token.getKey()));
    }

    // ======================== 写操作 ========================

    /**
     * 创建 Token      */
    @PostMapping("/")
    public Result<?> add(@Valid @RequestBody TokenIPO.Create ipo, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        String name = trimToNull(ipo.getName());
        if (name == null || name.length() > 50) {
            throw new ResultException(R.errorPrompt(name == null ? "Token 名称不能为空" : "Token 名称不能超过50个字符"));
        }

        // 生成 key
        String key = generateKey();

        // 检查用户令牌数量上限
        long count = tokenService.countByUserId(userId);
        int maxTokens = 50; // 默认上限
        if (count >= maxTokens) {
            throw new ResultException(R.errorPrompt("已达到最大令牌数量限制 (" + maxTokens + ")"));
        }

        long now = System.currentTimeMillis() / 1000;

        Token token = new Token();
        token.setUserId(userId);
        token.setName(name);
        token.setKey(key);
        token.setCreatedTime(now);
        token.setAccessedTime(now);
        token.setExpiredTime(ipo.getExpiredTime());
        token.setRemainQuota(ipo.getRemainQuota());
        token.setStatus(1); // TokenStatusEnabled
        token.setUnlimitedQuota(Boolean.TRUE.equals(ipo.getUnlimitedQuota()));
        token.setModelLimits(ipo.getModelLimits());
        token.setModelLimitsEnabled(Boolean.TRUE.equals(ipo.getModelLimitsEnabled()));
        token.setAllowIps(ipo.getAllowIps());
        token.setGroup(trimToNull(ipo.getGroup()));
        token.setCrossGroupRetry(Boolean.TRUE.equals(ipo.getCrossGroupRetry()));

        // 非无限额度时校验
        if (!Boolean.TRUE.equals(token.getUnlimitedQuota())) {
            if (token.getRemainQuota() != null && token.getRemainQuota() < 0) {
                throw new ResultException(R.errorPrompt("Token 额度不能为负数"));
            }
        }

        tokenService.create(token);
        return R.success(token);
    }

    /**
     * 更新 Token      */
    @PutMapping("/")
    public Result<?> update(@Valid @RequestBody TokenIPO.Update ipo,
                                       @RequestParam(required = false, name = "status_only") String statusOnly,
                                       HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        Integer id = ipo.getId();
        if (id == null || id == 0) {
            throw new ResultException(R.errorPrompt("无效的参数"));
        }

        // 获取原 Token 确认所有权
        Token cleanToken = tokenService.getByIdAndUser(id, userId);
        if (cleanToken == null) {
            throw new ResultException(R.errorPrompt("Token 不存在"));
        }

        // 仅更新状态
        if (statusOnly != null && !statusOnly.isEmpty()) {
            Integer status = ipo.getStatus();
            if (status == null) throw new ResultException(R.errorPrompt("无效的状态值"));
            cleanToken.setStatus(status);
            tokenService.update(cleanToken);
            return R.success(maskKey(cleanToken));
        }

        // 全量更新
        String name = trimToNull(ipo.getName());
        if (name != null) {
            if (name.length() > 50) throw new ResultException(R.errorPrompt("Token 名称不能超过50个字符"));
            cleanToken.setName(name);
        }

        if (ipo.getExpiredTime() != null) cleanToken.setExpiredTime(ipo.getExpiredTime());

        if (ipo.getRemainQuota() != null) {
            if (ipo.getRemainQuota() < 0 && !Boolean.TRUE.equals(cleanToken.getUnlimitedQuota())) {
                throw new ResultException(R.errorPrompt("Token 额度不能为负数"));
            }
            cleanToken.setRemainQuota(ipo.getRemainQuota());
        }

        if (ipo.getUnlimitedQuota() != null) cleanToken.setUnlimitedQuota(ipo.getUnlimitedQuota());
        if (ipo.getModelLimitsEnabled() != null) cleanToken.setModelLimitsEnabled(ipo.getModelLimitsEnabled());
        if (ipo.getModelLimits() != null) cleanToken.setModelLimits(ipo.getModelLimits());
        if (ipo.getAllowIps() != null) cleanToken.setAllowIps(ipo.getAllowIps());
        if (ipo.getGroup() != null) cleanToken.setGroup(trimToNull(ipo.getGroup()));
        if (ipo.getCrossGroupRetry() != null) cleanToken.setCrossGroupRetry(ipo.getCrossGroupRetry());

        tokenService.update(cleanToken);
        return R.success(maskKey(cleanToken));
    }

    /**
     * 删除 Token      */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        boolean deleted = tokenService.delete(id, userId);
        if (!deleted) {
            throw new ResultException(R.errorPrompt("Token 不存在或无权删除"));
        }
        return R.success();
    }

    /**
     * 批量删除 Token      */
    @PostMapping("/batch")
    public Result<?> deleteBatch(@Valid @RequestBody TokenIPO.BatchDelete ipo, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        int count = 0;
        for (Integer id : ipo.getIds()) {
            if (tokenService.delete(id, userId)) {
                count++;
            }
        }

        return R.success(count);
    }

    /**
     * 批量获取 Token 完整密钥      */
    @PostMapping("/batch/keys")
    public Result<?> getKeysBatch(@Valid @RequestBody TokenIPO.BatchGetKeys ipo, HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("id");
        if (userId == null) throw new ResultException(R.errorPrompt("未认证"));

        List<Integer> ids = ipo.getIds();
        if (ids.size() > 100) {
            throw new ResultException(R.errorPrompt("一次最多获取100个密钥"));
        }

        List<Token> tokens = tokenService.getKeysByIds(ids, userId);
        Map<Integer, String> keysMap = new LinkedHashMap<>();
        for (Token t : tokens) {
            keysMap.put(t.getId(), t.getKey());
        }

        return R.success(Map.of("keys", keysMap));
    }

    /**
     * 掩码 Token 的 key（Go GetMaskedKey：显示前3+后4位）
     */
    private Token maskKey(Token token) {
        if (token == null) return null;
        String key = token.getKey();
        if (key != null && key.length() > 7) {
            String masked = key.substring(0, 3) + "****" + key.substring(key.length() - 4);
            token.setKey(masked);
        }
        return token;
    }

    /**
     * 生成 Token Key（Go common.GenerateKey）
     */
    private String generateKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        sb.append(KEY_PREFIX);
        for (int i = KEY_PREFIX.length(); i < KEY_LENGTH; i++) {
            sb.append(KEY_CHARS.charAt(SECURE_RANDOM.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }




    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
