package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.entity.Vendor;
import yaoshu.token.pojo.ipo.VendorIPO;
import yaoshu.token.service.VendorService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 供应商元数据管理控制器  * <p>
 * 认证：AdminAuth（全部）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @GetMapping("/")
    public Result<?> getAll(HttpServletRequest request) {
        List<Vendor> vendors = vendorService.getAll();
        return R.success(PageInfo.of(vendors));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                                      HttpServletRequest request) {
        List<Vendor> vendors = vendorService.search(keyword);
        return R.success(PageInfo.of(vendors));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable int id) {
        Vendor vendor = vendorService.getById(id);
        if (vendor == null) throw new ResultException(R.errorPrompt("供应商不存在"));
        return R.success(vendor);
    }

    @PostMapping("/")
    public Result<?> create(@Valid @RequestBody VendorIPO.Create ipo) {
        Vendor vendor = new Vendor();
        vendor.setName(trimToNull(ipo.getName()));
        vendor.setDescription(trimToNull(ipo.getDescription()));
        vendor.setIcon(trimToNull(ipo.getIcon()));
        vendor.setStatus(ipo.getStatus() != null ? ipo.getStatus() : 1);
        return R.success(vendorService.create(vendor));
    }

    @PutMapping("/")
    public Result<?> update(@Valid @RequestBody VendorIPO.Update ipo) {
        Integer id = ipo.getId();
        if (id == null || id == 0) throw new ResultException(R.errorPrompt("无效的参数"));
        Vendor vendor = vendorService.getById(id);
        if (vendor == null) throw new ResultException(R.errorPrompt("供应商不存在"));
        String name = trimToNull(ipo.getName());
        if (name != null) vendor.setName(name);
        if (ipo.getDescription() != null) vendor.setDescription(ipo.getDescription());
        if (ipo.getIcon() != null) vendor.setIcon(ipo.getIcon());
        if (ipo.getStatus() != null) vendor.setStatus(ipo.getStatus());
        return R.success(vendorService.update(vendor));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id) {
        vendorService.delete(id);
        return R.success();
    }

    // ======================== 辅助方法 ========================




    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


