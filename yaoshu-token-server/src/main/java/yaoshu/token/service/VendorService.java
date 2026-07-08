package yaoshu.token.service;

import ai.yue.library.base.util.I18nUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.mapper.VendorMapper;
import yaoshu.token.pojo.entity.Vendor;

import java.util.List;

/**
 * 供应商元数据管理服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorService {

    private final VendorMapper vendorMapper;

    public List<Vendor> getAll() {
        PageHelper.startPage(ServletUtils.getRequest());
        return vendorMapper.selectList(new LambdaQueryWrapper<Vendor>()
                .orderByAsc(Vendor::getId));
    }

    public List<Vendor> search(String keyword) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Vendor> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Vendor::getName, keyword.trim());
        }
        qw.orderByAsc(Vendor::getId);
        return vendorMapper.selectList(qw);
    }

    public Vendor getById(int id) {
        return vendorMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Vendor create(Vendor vendor) {
        if (vendor.getName() == null || vendor.getName().isBlank()) {
            throw new IllegalArgumentException(I18nUtils.get("vendor.name_empty"));
        }
        // 名称唯一性校验
        if (isNameDuplicated(0, vendor.getName())) {
            throw new IllegalArgumentException(I18nUtils.get("vendor.name_exists"));
        }
        vendor.setCreatedTime(System.currentTimeMillis() / 1000);
        vendor.setUpdatedTime(vendor.getCreatedTime());
        vendorMapper.insert(vendor);
        return vendor;
    }

    @Transactional(rollbackFor = Exception.class)
    public Vendor update(Vendor vendor) {
        if (vendor.getId() == null || vendor.getId() == 0) {
            throw new IllegalArgumentException(I18nUtils.get("vendor.id_missing"));
        }
        // 名称冲突检查
        if (vendor.getName() != null && !vendor.getName().isBlank()
                && isNameDuplicated(vendor.getId(), vendor.getName())) {
            throw new IllegalArgumentException(I18nUtils.get("vendor.name_exists"));
        }
        vendor.setUpdatedTime(System.currentTimeMillis() / 1000);
        vendorMapper.updateById(vendor);
        return vendor;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(int id) {
        vendorMapper.deleteById(id);
    }

    private boolean isNameDuplicated(int id, String name) {
        return vendorMapper.selectCount(new LambdaQueryWrapper<Vendor>()
                .ne(id > 0, Vendor::getId, id)
                .eq(Vendor::getName, name.trim())) > 0;
    }
}
