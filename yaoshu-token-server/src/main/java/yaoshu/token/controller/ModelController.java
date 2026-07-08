package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.ModelMeta;
import yaoshu.token.pojo.ipo.ModelIPO;
import yaoshu.token.service.ModelService;
import yaoshu.token.service.ModelSyncService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 模型管理控制器，合并 Go controller/model.go + model_meta.go + model_sync.go + missing_models.go
 * <p>
 * 认证：AdminAuth（全部管理路由）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelController {

    private final ModelService modelService;
    private final ModelSyncService modelSyncService;

    // ==================== 模型元数据 CRUD ==================== 
    /**
     * 获取所有模型      * <ul>
     *   <li>`/api/models` UserAuth → DashboardListModels：返回全局静态 `channelId2Models`
     *       （map[channelType][]string，所有用户一致）</li>
     *   <li>`/api/models/` AdminAuth → GetAllModelsMeta：分页 `{items, total, vendor_counts}`</li>
     * </ul>
     * Java 端 PathMatchConfig 启用 useTrailingSlashMatch=true 把两个 URI 合并到同一映射，
     * 此处按 request.getRequestURI() 末尾是否带 `/` 区分语义。鉴权由 Filter 链按路径前缀控制。
     */
    @GetMapping(value = {"", "/"})
    public Result<?> getAll(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean isAdminPath = uri != null && uri.endsWith("/api/models/");

        if (isAdminPath) {
            // Admin 路径：role >= 2 强制校验。Filter 链 adminAuthFilter (`/api/models/*`)
            // 对裸 `/api/models/` 的匹配在不同 Servlet 容器上行为不一致，此处兜底防绕过。
            Object roleAttr = request.getAttribute("role");
            int role = (roleAttr instanceof Integer i) ? i : 0;
            if (role < 2) throw new ResultException(R.errorPrompt("无权限访问"));

            // Admin 路径：分页返回所有模型 + vendor_counts
            List<ModelMeta> models = modelService.getAll();
            PageInfo<ModelMeta> pageInfo = PageInfo.of(models);
            Map<Integer, Long> vendorCounts = modelService.getVendorModelCounts();

            // 组合 PageInfo 分页数据与 vendor_counts（字段名与 PageInfo 保持一致用 list）
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("list", pageInfo.getList());
            data.put("total", pageInfo.getTotal());
            data.put("vendor_counts", vendorCounts);
            return R.success(data);
        } else {
            // User 路径：返回全局静态 channelId2Models
            // 所有用户看到一致的"每种 channelType 当前注册的模型清单"
            return R.success(modelService.getChannelId2Models());
        }
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword) {
        List<ModelMeta> models = modelService.search(keyword != null ? keyword : "");
        return R.success(PageInfo.of(models));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable int id) {
        ModelMeta model = modelService.getById(id);
        if (model == null) throw new ResultException(R.errorPrompt("模型不存在"));
        return R.success(model);
    }

    @PostMapping("")
    public Result<?> create(@Valid @RequestBody ModelIPO.Create ipo) {
        ModelMeta model = new ModelMeta();
        model.setModelName(trimToNull(ipo.getModelName()));
        if (model.getModelName() == null) throw new ResultException(R.errorPrompt("模型名称不能为空"));

        model.setDescription(ipo.getDescription());
        model.setIcon(ipo.getIcon());
        model.setTags(ipo.getTags());
        model.setVendorId(ipo.getVendorId());
        model.setEndpoints(ipo.getEndpoints());
        model.setStatus(ipo.getStatus() != null ? ipo.getStatus() : 1);
        model.setSyncOfficial(ipo.getSyncOfficial() != null ? ipo.getSyncOfficial() : 0);
        model.setNameRule(ipo.getNameRule());
        model.setMaxContext(ipo.getMaxContext());

        modelService.create(model);
        return R.success(model);
    }

    @PutMapping("")
    public Result<?> update(@Valid @RequestBody ModelIPO.Update ipo) {
        Integer id = ipo.getId();
        if (id == null || id == 0) throw new ResultException(R.errorPrompt("无效的参数"));

        ModelMeta model = modelService.getById(id);
        if (model == null) throw new ResultException(R.errorPrompt("模型不存在"));

        String modelName = trimToNull(ipo.getModelName());
        if (modelName != null) model.setModelName(modelName);

        if (ipo.getDescription() != null) model.setDescription(ipo.getDescription());
        if (ipo.getIcon() != null) model.setIcon(ipo.getIcon());
        if (ipo.getTags() != null) model.setTags(ipo.getTags());
        if (ipo.getVendorId() != null) model.setVendorId(ipo.getVendorId());
        if (ipo.getEndpoints() != null) model.setEndpoints(ipo.getEndpoints());
        if (ipo.getStatus() != null) model.setStatus(ipo.getStatus());
        if (ipo.getSyncOfficial() != null) model.setSyncOfficial(ipo.getSyncOfficial());
        if (ipo.getNameRule() != null) model.setNameRule(ipo.getNameRule());
        if (ipo.getMaxContext() != null) model.setMaxContext(ipo.getMaxContext());

        modelService.update(model);
        return R.success(model);
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id) {
        boolean deleted = modelService.delete(id);
        if (!deleted) throw new ResultException(R.errorPrompt("模型不存在或删除失败"));
        return R.success();
    }

    // ==================== 模型同步 ==================== 
    @GetMapping("/sync_upstream/preview")
    public Result<?> syncUpstreamPreview(@RequestParam(required = false) String locale) {
        return R.success(modelSyncService.preview(locale));
    }

    @PostMapping("/sync_upstream")
    public Result<?> syncUpstream(@RequestBody(required = false) ModelIPO.SyncUpstream ipo) {
        String locale = ipo != null ? ipo.getLocale() : null;
        Object overwrite = ipo != null ? ipo.getOverwrite() : null;
        return R.success(modelSyncService.sync(locale, overwrite));
    }

    // ... rest unchanged ...

    // ==================== 缺失模型 ==================== 
    @GetMapping("/missing")
    public Result<?> getMissing() {
        return R.success(modelSyncService.getMissingModels());
    }

    // ==================== 辅助方法 ====================




    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

