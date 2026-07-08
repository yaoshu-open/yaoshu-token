package yaoshu.token.service;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.mapper.AbilityMapper;
import yaoshu.token.mapper.ChannelMapper;
import yaoshu.token.pojo.dto.ChannelInfoDTO;
import yaoshu.token.pojo.entity.Ability;
import yaoshu.token.pojo.entity.Channel;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 渠道内存缓存服务  * <p>
 * 核心职责：
 * <ul>
 *   <li>启动时从数据库全量加载渠道与能力数据，构建 group→model→channelIds 内存索引</li>
 *   <li>提供加权随机渠道选择算法（优先级分组 + 权重平滑）</li>
 *   <li>定时从数据库同步缓存（SyncChannelCache）</li>
 *   <li>运行时缓存更新（渠道状态变更）</li>
 * </ul>
 * <p>
 * 线程安全：所有缓存读写通过 {@link ReentrantReadWriteLock} 保护。
 *
 * @author yaoshu
 */
@Slf4j
public final class ChannelCacheService {

    private ChannelCacheService() {
    }

    /** group → model → 渠道ID列表（仅已启用渠道） */
    private static volatile Map<String, Map<String, List<Integer>>> group2Model2Channels = Map.of();
    /** 渠道ID → 渠道对象（全部渠道，含禁用） */
    private static volatile Map<Integer, Channel> channelsIdMap = Map.of();
    /** 读写锁 */
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    /** 同步定时器句柄 */
    private static ScheduledExecutorService syncScheduler;

    // ======================== 初始化 ========================

    /**
     * 初始化渠道缓存      * <p>
     * 从数据库全量加载 channels + abilities，构建 group→model→channelIds 索引。
     * 按 priority 降序排列每个分组下的渠道列表。
     *
     * @param channelMapper Channel Mapper
     * @param abilityMapper Ability Mapper
     */
    public static void initChannelCache(ChannelMapper channelMapper, AbilityMapper abilityMapper) {
        if (!CommonConstants.memoryCacheEnabled) {
            return;
        }
        // 1. 全量加载渠道
        List<Channel> channels = channelMapper.selectList(null);
        Map<Integer, Channel> newChannelsId2Channel = new HashMap<>();
        for (Channel channel : channels) {
            newChannelsId2Channel.put(channel.getId(), channel);
        }

        // 2. 全量加载能力，收集所有分组
        List<Ability> abilities = abilityMapper.selectList(null);
        Set<String> groups = new LinkedHashSet<>();
        for (Ability ability : abilities) {
            groups.add(ability.getGroup());
        }

        // 3. 构建 group→model→channelIds 映射（仅已启用渠道）
        Map<String, Map<String, List<Integer>>> newGroup2Model2Channels = new LinkedHashMap<>();
        for (String group : groups) {
            newGroup2Model2Channels.put(group, new LinkedHashMap<>());
        }

        for (Channel channel : channels) {
            if (channel.getStatus() == null || channel.getStatus() != CommonConstants.CHANNEL_STATUS_ENABLED) {
                continue;
            }
            String[] groupArr = channel.getGroup() != null ? channel.getGroup().split(",") : new String[]{"default"};
            String[] modelArr = channel.getModels() != null ? channel.getModels().split(",") : new String[0];
            for (String group : groupArr) {
                group = group.trim();
                Map<String, List<Integer>> modelMap = newGroup2Model2Channels.computeIfAbsent(group, k -> new LinkedHashMap<>());
                for (String model : modelArr) {
                    model = model.trim();
                    if (model.isEmpty()) continue;
                    List<Integer> channelIds = modelMap.computeIfAbsent(model, k -> new ArrayList<>());
                    channelIds.add(channel.getId());
                }
            }
        }

        // 4. 按 priority 降序排列
        for (Map.Entry<String, Map<String, List<Integer>>> groupEntry : newGroup2Model2Channels.entrySet()) {
            for (Map.Entry<String, List<Integer>> modelEntry : groupEntry.getValue().entrySet()) {
                List<Integer> channelIds = modelEntry.getValue();
                channelIds.sort((a, b) -> {
                    Channel ca = newChannelsId2Channel.get(a);
                    Channel cb = newChannelsId2Channel.get(b);
                    long pa = ca != null && ca.getPriority() != null ? ca.getPriority() : 0L;
                    long pb = cb != null && cb.getPriority() != null ? cb.getPriority() : 0L;
                    return Long.compare(pb, pa); // 降序：高优先级在前
                });
            }
        }

        // 5. 多 Key 模式下保留轮询索引（跨缓存刷新 persist）
        lock.writeLock().lock();
        try {
            for (Map.Entry<Integer, Channel> entry : newChannelsId2Channel.entrySet()) {
                Channel channel = entry.getValue();
                ChannelInfoDTO info = parseChannelInfo(channel);
                if (info != null && info.isMultiKey()) {
                    // 解析 Keys 到 transient 字段
                    channel.setKeys(parseKeys(channel.getKey()));
                    if ("polling".equals(info.getMultiKeyMode())) {
                        // 保留旧缓存中的轮询索引
                        Channel oldChannel = channelsIdMap.get(entry.getKey());
                        if (oldChannel != null) {
                            ChannelInfoDTO oldInfo = parseChannelInfo(oldChannel);
                            if (oldInfo != null && oldInfo.isMultiKey()
                                    && "polling".equals(oldInfo.getMultiKeyMode())) {
                                info.setMultiKeyPollingIndex(oldInfo.getMultiKeyPollingIndex());
                            }
                        }
                    }
                }
            }
            group2Model2Channels = newGroup2Model2Channels;
            channelsIdMap = newChannelsId2Channel;
        } finally {
            lock.writeLock().unlock();
        }
        SysLogService.sysLog("channels synced from database");
    }

    // ======================== 定时同步 ========================

    /**
     * 启动定时同步      *
     * @param channelMapper Channel Mapper
     * @param abilityMapper Ability Mapper
     * @param frequencySeconds 同步间隔（秒）
     */
    public static void startSyncChannelCache(ChannelMapper channelMapper, AbilityMapper abilityMapper, int frequencySeconds) {
        if (syncScheduler != null) {
            syncScheduler.shutdownNow();
        }
        syncScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "channel-cache-sync");
            t.setDaemon(true);
            return t;
        });
        syncScheduler.scheduleWithFixedDelay(
                () -> {
                    SysLogService.sysLog("syncing channels from database");
                    initChannelCache(channelMapper, abilityMapper);
                },
                frequencySeconds, frequencySeconds, TimeUnit.SECONDS);
    }

    /**
     * 停止定时同步
     */
    public static void stopSyncChannelCache() {
        if (syncScheduler != null) {
            syncScheduler.shutdownNow();
            syncScheduler = null;
        }
    }

    // ======================== 加权随机选择 ========================

    /**
     * 加权随机获取满足条件的渠道      * <p>
     * 选择算法：
     * <ol>
     *   <li>先精确匹配 model，未命中时用 normalizedModel 再匹配</li>
     *   <li>收集所有命中渠道的优先级，按降序排列</li>
     *   <li>retry 用于降级优先级（控制重试时尝试更低的优先级）</li>
     *   <li>同优先级内按权重加权随机选择</li>
     *   <li>权重为 0 时启用平滑因子（smoothing factor=100）确保均匀分布</li>
     * </ol>
     *
     * @param group 渠道分组
     * @param model 模型名称
     * @param retry 重试次数（0=最高优先级，N=降N级）
     * @return 选中的渠道，未找到返回 null
     */
    public static Channel getRandomSatisfiedChannel(String group, String model, int retry) {
        lock.readLock().lock();
        try {
            Map<String, List<Integer>> modelMap = group2Model2Channels.get(group);
            if (modelMap == null) {
                return null;
            }

            // 先精确匹配 model
            List<Integer> channelIds = modelMap.get(model);

            // 未找到时用 normalized model 匹配
            if (channelIds == null || channelIds.isEmpty()) {
                String normalizedModel = formatMatchingModelName(model);
                channelIds = modelMap.get(normalizedModel);
            }

            if (channelIds == null || channelIds.isEmpty()) {
                return null;
            }

            if (channelIds.size() == 1) {
                Channel channel = channelsIdMap.get(channelIds.get(0));
                if (channel == null) {
                    log.error("数据库一致性错误，渠道# {} 不存在，请联系管理员修复", channelIds.get(0));
                }
                return channel;
            }

            // 收集所有唯一优先级并降序排列
            Set<Long> uniquePriorities = new TreeSet<>(Comparator.reverseOrder());
            for (Integer channelId : channelIds) {
                Channel channel = channelsIdMap.get(channelId);
                if (channel == null) {
                    log.error("数据库一致性错误，渠道# {} 不存在，请联系管理员修复", channelId);
                    return null;
                }
                uniquePriorities.add(channel.getPriority() != null ? channel.getPriority() : 0L);
            }

            List<Long> sortedPriorities = new ArrayList<>(uniquePriorities);

            // retry 降级：超出优先级数量时取最后一级
            if (retry >= sortedPriorities.size()) {
                retry = sortedPriorities.size() - 1;
            }
            long targetPriority = sortedPriorities.get(retry);

            // 收集目标优先级的渠道
            List<Channel> targetChannels = new ArrayList<>();
            int sumWeight = 0;
            for (Integer channelId : channelIds) {
                Channel channel = channelsIdMap.get(channelId);
                if (channel == null) {
                    log.error("数据库一致性错误，渠道# {} 不存在，请联系管理员修复", channelId);
                    return null;
                }
                long priority = channel.getPriority() != null ? channel.getPriority() : 0L;
                if (priority == targetPriority) {
                    int weight = channel.getWeight() != null ? channel.getWeight() : 0;
                    sumWeight += weight;
                    targetChannels.add(channel);
                }
            }

            if (targetChannels.isEmpty()) {
                log.error("no channel found, group: {}, model: {}, priority: {}", group, model, targetPriority);
                return null;
            }

            // 加权随机选择 + 平滑因子
            int smoothingFactor = 1;
            int smoothingAdjustment = 0;

            if (sumWeight == 0) {
                // 所有权重为 0：等权随机（每个渠道有效权重=100）
                sumWeight = targetChannels.size() * 100;
                smoothingAdjustment = 100;
            } else if (sumWeight / targetChannels.size() < 10) {
                // 平均权重 < 10：放大 100 倍
                smoothingFactor = 100;
            }

            int totalWeight = sumWeight * smoothingFactor;
            int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

            for (Channel channel : targetChannels) {
                int weight = channel.getWeight() != null ? channel.getWeight() : 0;
                randomWeight -= weight * smoothingFactor + smoothingAdjustment;
                if (randomWeight < 0) {
                    return channel;
                }
            }

            log.error("channel not found after weighted selection, group: {}, model: {}", group, model);
            return null;

        } finally {
            lock.readLock().unlock();
        }
    }

    // ======================== 缓存查询 ========================

    /**
     * 从缓存获取渠道      */
    public static Channel cacheGetChannel(int id) {
        lock.readLock().lock();
        try {
            return channelsIdMap.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 从缓存获取渠道信息      */
    public static ChannelInfoDTO cacheGetChannelInfo(int id) {
        lock.readLock().lock();
        try {
            Channel channel = channelsIdMap.get(id);
            if (channel == null) {
                return null;
            }
            return parseChannelInfo(channel);
        } finally {
            lock.readLock().unlock();
        }
    }

    // ======================== 缓存更新 ========================

    /**
     * 更新缓存中的渠道状态      * <p>
     * 禁用状态时还会从 group2model2channels 中移除该渠道。
     */
    public static void cacheUpdateChannelStatus(int id, int status) {
        lock.writeLock().lock();
        try {
            Channel channel = channelsIdMap.get(id);
            if (channel != null) {
                channel.setStatus(status);
            }
            if (status != CommonConstants.CHANNEL_STATUS_ENABLED) {
                // 禁用/手动禁用：从 group2model2channels 移除
                for (Map.Entry<String, Map<String, List<Integer>>> groupEntry : group2Model2Channels.entrySet()) {
                    for (Map.Entry<String, List<Integer>> modelEntry : groupEntry.getValue().entrySet()) {
                        modelEntry.getValue().removeIf(channelId -> channelId == id);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 解析渠道 JSON 字段为 ChannelInfoDTO
     */
    private static ChannelInfoDTO parseChannelInfo(Channel channel) {
        String json = channel.getChannelInfo();
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return Convert.toJavaBean(json, ChannelInfoDTO.class);
        } catch (Exception e) {
            log.warn("Failed to parse channel_info for channel#{}: {}", channel.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * 解析渠道 Key 为多 Key 列表      * <p>
     * 支持格式：
     * <ul>
     *   <li>逗号/换行分隔的纯文本 Key</li>
     *   <li>JSON 数组格式（如 Vertex AI 场景）</li>
     * </ul>
     * <p>
     * 注意：Java 端 keys 为临时缓存字段，存储在其他属性中。目前用半列存储 returned key list 作为半列值。
     * 实际使用时由 ChannelService.getKeys() 方法动态解析。
     */
    static List<String> parseKeys(String keyStr) {
        if (keyStr == null || keyStr.isEmpty()) {
            return Collections.emptyList();
        }
        String trimmed = keyStr.trim();
        // JSON 数组格式
        if (trimmed.startsWith("[")) {
            try {
                String[] arr = Convert.toJavaBean(trimmed, String[].class);
                return arr != null ? Arrays.asList(arr) : Collections.emptyList();
            } catch (Exception e) {
                // 不是合法 JSON 数组，fallback 到换行分隔
            }
        }
        // 换行分隔
        return Arrays.asList(trimmed.split("\\n"));
    }

    /**
     * 模型名归一化      * <p>
     * 将变体模型名归一化为基础名，用于缓存查找匹配：
     * <ul>
     *   <li>gemini-2.5-* thinking → 替换为 thinking-* wildcard</li>
     *   <li>gpt-4-gizmo-* → gpt-4-gizmo-*</li>
     *   <li>gpt-4o-gizmo-* → gpt-4o-gizmo-*</li>
     * </ul>
     */
    static String formatMatchingModelName(String name) {
        if (name == null) {
            return null;
        }
        if (name.startsWith("gemini-2.5-flash-lite")) {
            name = handleThinkingBudgetModel(name, "gemini-2.5-flash-lite", "gemini-2.5-flash-lite-thinking-*");
        } else if (name.startsWith("gemini-2.5-flash")) {
            name = handleThinkingBudgetModel(name, "gemini-2.5-flash", "gemini-2.5-flash-thinking-*");
        } else if (name.startsWith("gemini-2.5-pro")) {
            name = handleThinkingBudgetModel(name, "gemini-2.5-pro", "gemini-2.5-pro-thinking-*");
        }

        if (name.startsWith("gpt-4-gizmo")) {
            name = "gpt-4-gizmo-*";
        }
        if (name.startsWith("gpt-4o-gizmo")) {
            name = "gpt-4o-gizmo-*";
        }
        return name;
    }

    /**
     * Gemini thinking budget 模型名处理      * <p>
     * 将 gemini-2.5-flash-thinking-128k → gemini-2.5-flash-thinking-*
     */
    private static String handleThinkingBudgetModel(String name, String basePrefix, String thinkingWildcard) {
        String thinkingPrefix = basePrefix + "-thinking";
        if (name.startsWith(thinkingPrefix) && !name.equals(thinkingPrefix)) {
            return thinkingWildcard;
        }
        return name;
    }
}
