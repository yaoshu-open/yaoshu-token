package yaoshu.token.controller;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yaoshu.token.mapper.PrefillGroupMapper;
import yaoshu.token.pojo.entity.PrefillGroup;

import java.util.List;

/**
 * 预填分组控制器  * <p>
 * 认证：AdminAuth（全部）
 */
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/prefill_group")
@RequiredArgsConstructor
public class PrefillGroupController {

    private final PrefillGroupMapper prefillGroupMapper;

    /**
     * 获取预填组列表，可按类型过滤      */
    @GetMapping("/")
    public Result<?> getAll(@RequestParam(required = false) String type) {
        LambdaQueryWrapper<PrefillGroup> query = new LambdaQueryWrapper<>();
        if (type != null && !type.isEmpty()) {
            query.eq(PrefillGroup::getType, type);
        }
        query.orderByDesc(PrefillGroup::getUpdatedTime);
        List<PrefillGroup> groups = prefillGroupMapper.selectList(query);
        return R.success(groups);
    }

    /**
     * 创建预填组      */
    @PostMapping("/")
    public Result<?> create(@RequestBody PrefillGroup group) {
        // 校验必填字段
        if (group.getName() == null || group.getName().isEmpty()
                || group.getType() == null || group.getType().isEmpty()) {
            throw new ResultException(R.errorPrompt("组名称和类型不能为空"));
        }

        // 检查名称是否重复
        boolean dup = prefillGroupMapper.selectCount(
                new LambdaQueryWrapper<PrefillGroup>()
                        .eq(PrefillGroup::getName, group.getName())
                        .ne(PrefillGroup::getId, group.getId())
        ) > 0;
        if (dup) {
            throw new ResultException(R.errorPrompt("组名称已存在"));
        }

        long now = System.currentTimeMillis() / 1000;
        group.setCreatedTime(now);
        group.setUpdatedTime(now);
        prefillGroupMapper.insert(group);

        return R.success(group);
    }

    /**
     * 更新预填组      */
    @PutMapping("/")
    public Result<?> update(@RequestBody PrefillGroup group) {
        // 校验 ID
        if (group.getId() == null || group.getId() == 0) {
            throw new ResultException(R.errorPrompt("缺少组 ID"));
        }

        // 检查名称是否重复（排除自身）
        boolean dup = prefillGroupMapper.selectCount(
                new LambdaQueryWrapper<PrefillGroup>()
                        .eq(PrefillGroup::getName, group.getName())
                        .ne(PrefillGroup::getId, group.getId())
        ) > 0;
        if (dup) {
            throw new ResultException(R.errorPrompt("组名称已存在"));
        }

        group.setUpdatedTime(System.currentTimeMillis() / 1000);
        prefillGroupMapper.updateById(group);

        return R.success(group);
    }

    /**
     * 删除预填组      */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable int id) {
        prefillGroupMapper.deleteById(id);
        return R.success();
    }
}
