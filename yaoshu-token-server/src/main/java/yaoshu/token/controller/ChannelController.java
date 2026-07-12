package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Channel;
import yaoshu.token.pojo.ipo.ChannelIPO;
import yaoshu.token.service.ChannelManagementService;
import yaoshu.token.service.ChannelService;
import yaoshu.token.service.ModelService;
import yaoshu.token.service.OllamaService;
import yaoshu.token.service.ProxyClientCacheService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import ai.yue.library.base.convert.Convert;
import ai.yue.library.base.view.Result;
import ai.yue.library.base.view.R;
import ai.yue.library.base.exception.ResultException;

/**
 * 渠道管理主控制器  * <p>
 * 认证：AdminAuth（大部分）+ RootAuth（部分敏感操作）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/channel")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelManagementService channelManagementService;
    private final ModelService modelService;
    private final OllamaService ollamaService;
    private final ProxyClientCacheService proxyClientCacheService;

    @GetMapping("/")
    public Result<?> getAll(HttpServletRequest request) {
        String sortBy = request.getParameter("sort_by");
        String sortOrder = request.getParameter("sort_order");
        List<Channel> channels = channelService.getAll(sortBy, sortOrder, false);
        return R.success(PageInfo.of(channels));
    }

    /**
     * 模型可用性诊断：检查指定模型在指定分组下的真实分发状态。
     * <p>
     * 返回 abilities 表匹配记录、渠道状态、排除原因，
     * 帮助运营理解"模型测试通过但 API 调用返回 503"的根因。
     */
    @GetMapping("/model-routing/diagnose")
    public Result<?> diagnoseModelRouting(
            @RequestParam String model,
            @RequestParam(defaultValue = "default") String group) {
        if (model == null || model.isBlank()) {
            throw new ResultException(R.errorPrompt("model 参数不能为空"));
        }
        Map<String, Object> diagnosis = channelService.diagnoseModelAvailability(model.trim(), group.trim());
        return R.success(diagnosis);
    }

    @GetMapping("/search")
    public Result<?> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String group,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status) {
        List<Channel> channels = channelService.search(keyword, group, type, status);
        return R.success(PageInfo.of(channels));
    }

    /**
     * 渠道支持的全量模型清单（OpenAI 兼容格式）      * <p>
     * 返回 openAIModels 全集（适配器声明的所有模型 + 内置 ratio 默认表）。
     */
    @GetMapping("/models")
    public Result<?> listModels() {
        return R.success(modelService.listAllOpenAIModels());
    }

    /**
     * 已启用的模型 ID 列表（去重）      * <p>
     * SQL：SELECT DISTINCT model FROM abilities WHERE enabled = true
     */
    @GetMapping("/models_enabled")
    public Result<?> listEnabledModels() {
        return R.success(modelService.getEnabledModelNames());
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable int id) {
        Channel channel = channelService.getById(id);
        if (channel == null) throw new ResultException(R.errorPrompt("渠道不存在"));
        // 掩码 key
        channel.setKey(maskKey(channel.getKey()));
        return R.success(channel);
    }

    @SaCheckRole("root")
    @PostMapping("/{id}/key")
    public Result<?> getKey(@PathVariable int id) {
        Channel channel = channelService.getById(id);
        if (channel == null) throw new ResultException(R.errorPrompt("渠道不存在"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", channel.getKey());
        return R.success(data);
    }

    @GetMapping("/test")
    public Result<?> testAll() {
        return runResponseAction(channelManagementService::testAllChannelsAsync);
    }

    @PostMapping("/test/batch")
    public Result<?> testBatch(@Valid @RequestBody ChannelIPO.BatchTest ipo) {
        return runResponseAction(() -> channelManagementService.testChannelsByIds(ipo.getIds()));
    }

    @GetMapping("/test/{id}")
    public Result<?> test(@PathVariable int id, HttpServletRequest request) {
        return runResponseAction(() -> channelManagementService.testChannel(id, request));
    }

    @GetMapping("/update_balance")
    public Result<?> updateAllBalances() {
        return runAction(channelManagementService::updateAllBalances);
    }

    @GetMapping("/update_balance/{id}")
    public Result<?> updateBalance(@PathVariable int id) {
        return runResponseAction(() -> channelManagementService.updateBalance(id));
    }

    @PostMapping("/")
    public Result<?> add(@Valid @RequestBody ChannelIPO.AddRequest ipo) {
        ChannelIPO.ChannelCreate src = ipo.getChannel();
        if (src == null) {
            throw new ResultException(R.errorPrompt("channel 不能为空"));
        }
        String name = trimToNull(src.getName());
        if (name == null) {
            throw new ResultException(R.errorPrompt("渠道名称不能为空"));
        }
        String rawKey = trimToNull(src.getKey());
        if (rawKey == null) {
            throw new ResultException(R.errorPrompt("渠道 key 不能为空"));
        }

        String mode = ipo.getMode();
        List<String> keys = new ArrayList<>();
        boolean isMultiKey = false;

        switch (mode) {
            case "single":
                keys.add(rawKey);
                break;
            case "multi_to_single":
                isMultiKey = true;
                // 多 key 按换行拆分，清理空行后重新用 \n 连接为单个渠道
                List<String> cleanKeys = new ArrayList<>();
                for (String k : rawKey.split("\n")) {
                    String trimmed = k.trim();
                    if (!trimmed.isEmpty()) {
                        cleanKeys.add(trimmed);
                    }
                }
                if (cleanKeys.isEmpty()) {
                    throw new ResultException(R.errorPrompt("渠道 key 不能为空"));
                }
                String mergedKey = String.join("\n", cleanKeys);
                keys.add(mergedKey);
                break;
            case "batch":
                for (String k : rawKey.split("\n")) {
                    String trimmed = k.trim();
                    if (!trimmed.isEmpty()) {
                        keys.add(trimmed);
                    }
                }
                if (keys.isEmpty()) {
                    throw new ResultException(R.errorPrompt("渠道 key 不能为空"));
                }
                break;
            default:
                throw new ResultException(R.errorPrompt("不支持的添加模式：" + mode));
        }

        // 构建 channelInfo（multi_to_single 模式写入多 key 标记）
        String channelInfo = src.getChannelInfo();
        if (isMultiKey) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("is_multi_key", true);
            info.put("multi_key_mode", ipo.getMultiKeyMode());
            info.put("multi_key_size", keys.get(0).split("\n").length);
            channelInfo = Convert.toJSONString(info);
        }

        // 根据拆分后的 keys 构建渠道列表
        List<Channel> channels = new ArrayList<>();
        for (String key : keys) {
            Channel ch = buildChannelFromCreate(src, name, key, channelInfo);
            // batch 模式可选追加 key 前缀到名称
            if (Boolean.TRUE.equals(ipo.getBatchAddSetKeyPrefix2Name()) && keys.size() > 1) {
                String prefix = key.length() > 8 ? key.substring(0, 8) : key;
                ch.setName(name + " " + prefix);
            }
            channels.add(ch);
        }

        if (channels.size() == 1) {
            channelService.create(channels.get(0));
        } else {
            channelService.batchCreate(channels);
        }
        proxyClientCacheService.reset();
        return R.success();
    }

    /**
     * 从 ChannelCreate IPO 构建 Channel 实体（不含 key/name/channelInfo，由调用方设置）
     */
    private Channel buildChannelFromCreate(ChannelIPO.ChannelCreate src, String name, String key, String channelInfo) {
        Channel ch = new Channel();
        ch.setName(name);
        ch.setKey(key);
        ch.setType(src.getType());
        ch.setStatus(src.getStatus() != null ? src.getStatus() : 1);
        ch.setBaseUrl(trimToNull(src.getBaseUrl()));
        ch.setModels(trimToNull(src.getModels()));
        ch.setGroup(trimToNull(src.getGroup()));
        ch.setPriority(src.getPriority() != null ? src.getPriority() : 0L);
        ch.setBalance(src.getBalance() != null ? src.getBalance() : 0.0);
        ch.setWeight(src.getWeight());
        ch.setAutoBan(src.getAutoBan());
        ch.setTestModel(trimToNull(src.getTestModel()));
        ch.setModelMapping(trimToNull(src.getModelMapping()));
        ch.setSetting(trimToNull(src.getSetting()));
        ch.setOtherSettings(trimToNull(src.getOtherSettings()));
        ch.setParamOverride(trimToNull(src.getParamOverride()));
        ch.setHeaderOverride(trimToNull(src.getHeaderOverride()));
        ch.setTag(trimToNull(src.getTag()));
        ch.setOther(trimToNull(src.getOther()));
        ch.setOtherInfo(trimToNull(src.getOtherInfo()));
        ch.setRemark(trimToNull(src.getRemark()));
        ch.setOpenaiOrganization(trimToNull(src.getOpenaiOrganization()));
        ch.setStatusCodeMapping(trimToNull(src.getStatusCodeMapping()));
        ch.setUaOverrideMode(trimToNull(src.getUaOverrideMode()));
        ch.setChannelInfo(channelInfo);
        return ch;
    }

    @PutMapping("/")
    public Result<?> update(@Valid @RequestBody ChannelIPO.Update ipo) {
        Integer id = ipo.getId();
        if (id == null || id == 0) throw new ResultException(R.errorPrompt("无效的参数"));

        Channel channel = channelService.getById(id);
        if (channel == null) throw new ResultException(R.errorPrompt("渠道不存在"));

        // 记录 models/group/status 是否变更，用于判断是否需要重建 abilities
        boolean abilitiesChanged = false;
        String oldModels = channel.getModels();
        String oldGroup = channel.getGroup();
        Integer oldStatus = channel.getStatus();

        String name = trimToNull(ipo.getName());
        if (name != null) channel.setName(name);
        Integer type = ipo.getType();
        if (type != null) channel.setType(type);
        String key = trimToNull(ipo.getKey());
        if (key != null) channel.setKey(key);
        Integer status = ipo.getStatus();
        if (status != null) channel.setStatus(status);
        String baseUrl = ipo.getBaseUrl();
        if (baseUrl != null) channel.setBaseUrl(baseUrl);
        String models = ipo.getModels();
        if (models != null) channel.setModels(models);
        String group = trimToNull(ipo.getGroup());
        if (group != null) channel.setGroup(group);
        Long priority = ipo.getPriority();
        if (priority != null) channel.setPriority(priority);
        Double balance = ipo.getBalance();
        if (balance != null) channel.setBalance(balance);

        // models/group/status 变更 → 重建 abilities（删除旧记录 + 按新配置重建）
        abilitiesChanged = !java.util.Objects.equals(oldModels, channel.getModels())
                || !java.util.Objects.equals(oldGroup, channel.getGroup())
                || !java.util.Objects.equals(oldStatus, channel.getStatus());

        channelManagementService.updateChannelWithAbilities(channel, abilitiesChanged);
        // UpdateChannel 写库后清空 proxyClients 缓存
        proxyClientCacheService.reset();
        return R.success(channel);
    }

    @DeleteMapping("/disabled")
    public Result<?> deleteDisabled() {
        return runDataAction(channelManagementService::deleteDisabledChannels);
    }

    @PostMapping("/tag/disabled")
    public Result<?> disableByTag(@Valid @RequestBody ChannelIPO.TagAction ipo) {
        return runAction(() -> channelManagementService.disableChannelsByTag(ipo.getTag()));
    }

    @PostMapping("/tag/enabled")
    public Result<?> enableByTag(@Valid @RequestBody ChannelIPO.TagAction ipo) {
        return runAction(() -> channelManagementService.enableChannelsByTag(ipo.getTag()));
    }

    @PutMapping("/tag")
    public Result<?> editTag(@Valid @RequestBody ChannelIPO.TagEdit ipo) {
        return runAction(() -> channelManagementService.editChannelsByTag(ipo));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id) {
        boolean deleted = channelService.delete(id);
        if (!deleted) throw new ResultException(R.errorPrompt("渠道不存在或删除失败"));
        return R.success();
    }

    @PostMapping("/batch")
    public Result<?> deleteBatch(@Valid @RequestBody ChannelIPO.BatchDelete ipo) {
        for (Integer id : ipo.getIds()) {
            channelService.delete(id);
        }
        return R.success();
    }

    @PostMapping("/fix")
    public Result<?> fixAbilities() {
        return runDataAction(channelManagementService::fixAbilities);
    }

    @GetMapping("/fetch_models/{id}")
    public Result<?> fetchModels(@PathVariable int id) {
        return runDataAction(() -> channelManagementService.fetchModelsByChannelId(id));
    }

    @SaCheckRole("root")
    @PostMapping("/fetch_models")
    public Result<?> fetchModelsBatch(@Valid @RequestBody ChannelIPO.FetchModels ipo) {
        return runDataAction(() -> channelManagementService.fetchModels(ipo.getType(), ipo.getBaseUrl(), ipo.getKey()));
    }

    @PostMapping("/batch/tag")
    public Result<?> batchSetTag(@Valid @RequestBody ChannelIPO.BatchSetTag ipo) {
        return runDataAction(() -> channelManagementService.batchSetTag(ipo.getIds(), ipo.getTag()));
    }

    @GetMapping("/tag/models")
    public Result<?> getTagModels(@RequestParam String tag) {
        return runDataAction(() -> channelManagementService.getTagModels(tag));
    }

    @PostMapping("/copy/{id}")
    public Result<?> copyChannel(@PathVariable int id, HttpServletRequest request) {
        String suffix = request.getParameter("suffix");
        if (suffix == null) {
            suffix = "_复制";
        }
        boolean resetBalance = parseBooleanOrDefault(request.getParameter("reset_balance"), true);
        String finalSuffix = suffix;
        return runDataAction(() -> channelManagementService.copyChannel(id, finalSuffix, resetBalance));
    }

    @PostMapping("/multi_key/manage")
    public Result<?> manageMultiKeys(@Valid @RequestBody ChannelIPO.MultiKeyManage ipo) {
        return runPayloadAction(() -> channelManagementService.manageMultiKeys(ipo));
    }

    // Codex OAuth 相关（归入渠道管理）
    @PostMapping("/codex/oauth/start")
    public Result<?> startCodexOAuth(HttpServletRequest request) {
        return runDataAction(() -> channelManagementService.startCodexOAuth(0, request.getSession(true)));
    }

    @PostMapping("/codex/oauth/complete")
    public Result<?> completeCodexOAuth(@RequestBody(required = false) ChannelIPO.CodexOAuthComplete ipo,
                                                  HttpServletRequest request) {
        return runPayloadAction(() -> channelManagementService.completeCodexOAuth(
                0,
                ipo == null ? null : ipo.getInput(),
                getExistingSession(request)
        ));
    }

    @PostMapping("/{id}/codex/oauth/start")
    public Result<?> startCodexOAuthForChannel(@PathVariable int id, HttpServletRequest request) {
        return runDataAction(() -> channelManagementService.startCodexOAuth(id, request.getSession(true)));
    }

    @PostMapping("/{id}/codex/oauth/complete")
    public Result<?> completeCodexOAuthForChannel(@PathVariable int id,
                                                            @RequestBody(required = false) ChannelIPO.CodexOAuthComplete ipo,
                                                            HttpServletRequest request) {
        return runPayloadAction(() -> channelManagementService.completeCodexOAuth(
                id,
                ipo == null ? null : ipo.getInput(),
                getExistingSession(request)
        ));
    }

    @PostMapping("/{id}/codex/refresh")
    public Result<?> refreshCodexCredential(@PathVariable int id) {
        return runPayloadAction(() -> channelManagementService.refreshCodexCredential(id));
    }

    @GetMapping("/{id}/codex/usage")
    public Result<?> getCodexUsage(@PathVariable int id) {
        return runResponseAction(() -> channelManagementService.getCodexUsage(id));
    }

    @PostMapping("/upstream_updates/apply")
    public Result<?> applyUpstreamUpdates(@Valid @RequestBody ChannelIPO.UpstreamUpdates ipo) {
        return runResponseAction(() -> channelManagementService.applyUpstreamModelUpdates(ipo));
    }

    @PostMapping("/upstream_updates/apply_all")
    public Result<?> applyAllUpstreamUpdates() {
        return runResponseAction(channelManagementService::applyAllUpstreamModelUpdates);
    }

    @PostMapping("/upstream_updates/detect")
    public Result<?> detectUpstreamUpdates(@Valid @RequestBody ChannelIPO.UpstreamUpdates ipo) {
        return runResponseAction(() -> channelManagementService.detectUpstreamModelUpdates(ipo.getId()));
    }

    @PostMapping("/upstream_updates/detect_all")
    public Result<?> detectAllUpstreamUpdates() {
        return runResponseAction(channelManagementService::detectAllUpstreamModelUpdates);
    }

    /**
     * 拉取 Ollama 模型（非流式）。      */
    @PostMapping("/ollama/pull")
    public Result<?> ollamaPullModel(@Valid @RequestBody ChannelIPO.OllamaAction ipo) {
        return ollamaService.pullModel(ipo.getChannelId(), ipo.getModelName());
    }

    /**
     * 流式拉取 Ollama 模型（SSE）。      * 直接写出 SSE 流到 HttpServletResponse，故方法返回 void。
     */
    @PostMapping("/ollama/pull/stream")
    public void ollamaPullModelStream(@Valid @RequestBody ChannelIPO.OllamaAction ipo, HttpServletResponse response) {
        ollamaService.pullModelStream(ipo.getChannelId(), ipo.getModelName(), response);
    }

    /**
     * 删除 Ollama 模型。      */
    @DeleteMapping("/ollama/delete")
    public Result<?> ollamaDeleteModel(@Valid @RequestBody ChannelIPO.OllamaAction ipo) {
        return ollamaService.deleteModel(ipo.getChannelId(), ipo.getModelName());
    }

    /**
     * 获取 Ollama 服务版本。      */
    @GetMapping("/ollama/version/{id}")
    public Result<?> ollamaVersion(@PathVariable int id) {
        return ollamaService.fetchVersion(id);
    }

    private Result<?> runAction(Runnable action) {
        try {
            action.run();
            return R.success();
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    private Result<?> runDataAction(Supplier<Object> action) {
        try {
            return R.success(action.get());
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    private Result<?> runPayloadAction(Supplier<Map<String, Object>> action) {
        try {
            Map<String, Object> payload = action.get();
            if (payload == null || payload.isEmpty()) {
                return R.success();
            }
            if (payload.containsKey("data")) {
                return R.success(payload.get("data"));
            }
            return R.success(payload);
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    private Result<?> runResponseAction(Supplier<Map<String, Object>> action) {
        try {
            return R.success(action.get());
        } catch (ResultException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResultException(R.errorPrompt(e.getMessage()));
        }
    }

    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Go GetMaskedKey: 显示 key 的前3+后4位 */
    private String maskKey(String key) {
        if (key != null && key.length() > 7) {
            return key.substring(0, 3) + "****" + key.substring(key.length() - 4);
        }
        return key;
    }

    private boolean parseBooleanOrDefault(String raw, boolean defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return defaultValue;
    }

    private HttpSession getExistingSession(HttpServletRequest request) {
        return request.getSession(false);
    }
}
