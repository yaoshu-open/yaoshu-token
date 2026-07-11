package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.pojo.entity.Channel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ai.yue.library.base.convert.Convert;
/**
 * 渠道服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelMapper channelMapper;
    private final yaoshu.token.mapper.AbilityMapper abilityMapper;

    // ======================== 查询方法 ========================

    public Channel getById(Integer id) {
        if (id == null || id == 0) {
            return null;
        }
        return channelMapper.selectById(id);
    }

    /**
     * 获取所有渠道（分页）      */
    public List<Channel> getAll(String sortBy, String sortOrder, boolean idSort) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Channel> qw = new LambdaQueryWrapper<>();
        if (sortBy != null && !sortBy.isEmpty()) {
            boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
            switch (sortBy) {
                case "id": qw.orderBy(true, isAsc, Channel::getId); break;
                case "name": qw.orderBy(true, isAsc, Channel::getName); break;
                case "priority": qw.orderBy(true, isAsc, Channel::getPriority); break;
                case "balance": qw.orderBy(true, isAsc, Channel::getBalance); break;
                case "response_time": qw.orderBy(true, isAsc, Channel::getResponseTime); break;
                case "test_time": qw.orderBy(true, isAsc, Channel::getTestTime); break;
                default: qw.orderByDesc(Channel::getPriority); break;
            }
        } else if (idSort) {
            qw.orderByDesc(Channel::getId);
        } else {
            qw.orderByDesc(Channel::getPriority);
        }
        return channelMapper.selectList(qw);
    }

    /**
     * 搜索渠道      */
    public List<Channel> search(String keyword, String group, Integer type, Integer status) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Channel> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like(Channel::getName, keyword).or().like(Channel::getKey, keyword));
            try {
                int id = Integer.parseInt(keyword);
                qw.or().eq(Channel::getId, id);
            } catch (NumberFormatException e) {
                log.debug("搜索关键词非数字 ID，跳过 ID 精确匹配: {}", keyword, e);
            }
        }
        if (group != null && !group.isEmpty()) {
            qw.eq(Channel::getGroup, group);
        }
        if (type != null) {
            qw.eq(Channel::getType, type);
        }
        if (status != null) {
            qw.eq(Channel::getStatus, status);
        }
        qw.orderByDesc(Channel::getPriority);
        return channelMapper.selectList(qw);
    }

    /**
     * 获取已启用渠道      */
    public List<Channel> getEnabled() {
        return channelMapper.selectList(
                new LambdaQueryWrapper<Channel>().eq(Channel::getStatus, 1)
        );
    }

    /**
     * 按分组获取已启用渠道
     */
    public List<Channel> getEnabledByGroup(String group) {
        LambdaQueryWrapper<Channel> qw = new LambdaQueryWrapper<Channel>()
                .eq(Channel::getStatus, 1);
        if (group != null && !group.isEmpty()) {
            qw.eq(Channel::getGroup, group);
        }
        return channelMapper.selectList(qw);
    }

    // ======================== Key 管理 ========================

    /**
     * 解析渠道的 Key 列表      */
    public List<String> parseKeys(Channel channel) {
        if (channel.getKey() == null || channel.getKey().isEmpty()) {
            return List.of();
        }
        String trimmed = channel.getKey().trim();
        // JSON 数组格式（如 Vertex AI 场景）
        if (trimmed.startsWith("[")) {
            try {
                String[] arr = Convert.toJavaBean(trimmed, String[].class);
                return Arrays.asList(arr);
            } catch (Exception e) {
                log.debug("解析 channel key JSON 数组失败，回退到换行分隔", e);
            }
        }
        // 换行分隔
        return Arrays.stream(trimmed.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ======================== 写操作 ========================

    @Transactional(rollbackFor = Exception.class)
    public Channel create(Channel channel) {
        if (channel.getCreatedTime() == null) {
            channel.setCreatedTime(System.currentTimeMillis() / 1000);
        }
        channelMapper.insert(channel);
        return channel;
    }

    /**
     * 批量创建渠道（事务保证全成功或全失败）      */
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(List<Channel> channels) {
        if (channels == null || channels.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis() / 1000;
        for (Channel channel : channels) {
            if (channel.getCreatedTime() == null) {
                channel.setCreatedTime(now);
            }
            channelMapper.insert(channel);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean update(Channel channel) {
        return channelMapper.updateById(channel) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Integer id) {
        return channelMapper.deleteById(id) > 0;
    }

    /**
     * 更新渠道余额      */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBalance(Integer channelId, Double balance) {
        LambdaUpdateWrapper<Channel> uw = new LambdaUpdateWrapper<>();
        uw.eq(Channel::getId, channelId)
                .set(Channel::getBalance, balance)
                .set(Channel::getBalanceUpdatedTime, System.currentTimeMillis() / 1000);
        return channelMapper.update(null, uw) > 0;
    }

    /**
     * 更新渠道状态      */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Integer channelId, Integer status) {
        Channel update = new Channel();
        update.setId(channelId);
        update.setStatus(status);
        return channelMapper.updateById(update) > 0;
    }

    /**
     * 批量更新渠道状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStatus(List<Integer> ids, Integer status) {
        Channel update = new Channel();
        update.setStatus(status);
        LambdaUpdateWrapper<Channel> uw = new LambdaUpdateWrapper<>();
        uw.in(Channel::getId, ids);
        channelMapper.update(update, uw);
    }

    /**
     * 随机选择一个满足条件的已启用渠道。      * <p>
     * 联查 abilities 表确认模型在该分组下启用，按 priority 分级 + weight 加权随机选择：
     * <ol>
     *   <li>取该 group+model 下所有启用 ability（priority 降序）</li>
     *   <li>按 retry 选定目标 priority 级别（retry 超出时用最低优先级）</li>
     *   <li>在目标优先级的渠道中按 weight+10 加权随机选取一个 channelId</li>
     *   <li>用 channelId 查出已启用的 Channel 返回</li>
     * </ol>
     *
     * @param group         分组名
     * @param modelName     模型名
     * @param priorityRetry 重试优先级序号（0=最高优先级，>0=次级优先级）
     * @return 可用渠道，无可用渠道时返回 null
     */
    public Channel getRandomSatisfiedChannel(String group, String modelName, int priorityRetry) {
        return getRandomSatisfiedChannel(group, modelName, priorityRetry, null);
    }

    /**
     * 随机选择一个满足条件的已启用渠道（支持排除已尝试的渠道）。
     * <p>
     * 重试场景传入 excludeIds 避免反复选到同一个已失败渠道。
     *
     * @param excludeIds 需排除的渠道 ID 集合（null 或 empty 表示不排除）
     */
    public Channel getRandomSatisfiedChannel(String group, String modelName, int priorityRetry,
                                              Set<Integer> excludeIds) {
        if (group == null || group.isEmpty() || modelName == null || modelName.isEmpty()) {
            return null;
        }
        // 1. 取该 group+model 下所有启用的 ability（已按 priority DESC, weight DESC 排序）
        List<yaoshu.token.pojo.entity.Ability> abilities = abilityMapper.selectEnabledAbilities(group, modelName);
        if (abilities.isEmpty()) {
            return null;
        }

        // 2. 提取去重后的优先级列表（降序），按 retry 选定目标优先级
        List<Long> priorities = abilities.stream()
                .map(a -> a.getPriority() == null ? 0L : a.getPriority())
                .distinct()
                .collect(Collectors.toList());
        int retry = priorityRetry < 0 ? 0 : priorityRetry;
        long targetPriority = retry >= priorities.size()
                ? priorities.get(priorities.size() - 1)
                : priorities.get(retry);

        // 3. 目标优先级下的候选 ability，排除指定渠道后按 weight+10 加权随机
        Set<Integer> finalExcludeIds = excludeIds != null ? excludeIds : Set.of();
        List<yaoshu.token.pojo.entity.Ability> candidates = abilities.stream()
                .filter(a -> (a.getPriority() == null ? 0L : a.getPriority()) == targetPriority)
                .filter(a -> !finalExcludeIds.contains(a.getChannelId()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }
        int weightSum = 0;
        for (yaoshu.token.pojo.entity.Ability a : candidates) {
            weightSum += (a.getWeight() == null ? 0 : a.getWeight()) + 10;
        }
        int weight = (int) (Math.random() * weightSum);
        int channelId = candidates.get(0).getChannelId();
        for (yaoshu.token.pojo.entity.Ability a : candidates) {
            weight -= (a.getWeight() == null ? 0 : a.getWeight()) + 10;
            if (weight <= 0) {
                channelId = a.getChannelId();
                break;
            }
        }

        // 4. 用 channelId 查出已启用的渠道
        Channel channel = channelMapper.selectById(channelId);
        if (channel == null || (channel.getStatus() != null && channel.getStatus() != 1)) {
            return null;
        }
        return channel;
    }

    /**
     * 判断指定渠道在某分组下是否启用了该模型。      */
    public boolean isChannelEnabledForGroupModel(String group, String modelName, int channelId) {
        if (group == null || group.isEmpty() || modelName == null || modelName.isEmpty() || channelId <= 0) {
            return false;
        }
        return abilityMapper.countEnabledForGroupModelChannel(group, modelName, channelId) > 0;
    }

    /**
     * 获取指定分组+模型下目标优先级的所有候选渠道（排除指定 ID）。
     * <p>
     * 供 ChannelSelector SPI 使用——先获取候选集，再由 SPI 做健康过滤和智能选择。
     *
     * @param group         分组名
     * @param modelName     模型名
     * @param priorityRetry 重试优先级序号（0=最高优先级）
     * @param excludeIds    需排除的渠道 ID 集合（null 或 empty 表示不排除）
     * @return 候选渠道列表（已按 priority DESC 排序），空列表表示无可用渠道
     */
    public List<Channel> getCandidateChannels(String group, String modelName, int priorityRetry,
                                               Set<Integer> excludeIds) {
        if (group == null || group.isEmpty() || modelName == null || modelName.isEmpty()) {
            return List.of();
        }
        List<yaoshu.token.pojo.entity.Ability> abilities = abilityMapper.selectEnabledAbilities(group, modelName);
        if (abilities.isEmpty()) {
            return List.of();
        }

        // 提取去重后的优先级列表（降序），按 retry 选定目标优先级
        List<Long> priorities = abilities.stream()
                .map(a -> a.getPriority() == null ? 0L : a.getPriority())
                .distinct()
                .collect(Collectors.toList());
        int retry = priorityRetry < 0 ? 0 : priorityRetry;
        long targetPriority = retry >= priorities.size()
                ? priorities.get(priorities.size() - 1)
                : priorities.get(retry);

        // 目标优先级下的候选 ability，排除指定渠道
        Set<Integer> finalExcludeIds = excludeIds != null ? excludeIds : Set.of();
        List<yaoshu.token.pojo.entity.Ability> candidates = abilities.stream()
                .filter(a -> (a.getPriority() == null ? 0L : a.getPriority()) == targetPriority)
                .filter(a -> !finalExcludeIds.contains(a.getChannelId()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 加载启用的 Channel 对象
        List<Channel> result = new ArrayList<>();
        for (yaoshu.token.pojo.entity.Ability a : candidates) {
            Channel channel = channelMapper.selectById(a.getChannelId());
            if (channel != null && (channel.getStatus() == null || channel.getStatus() == 1)) {
                result.add(channel);
            }
        }
        return result;
    }
}
