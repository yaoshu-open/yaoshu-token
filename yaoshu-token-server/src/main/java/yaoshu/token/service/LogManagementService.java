package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.mapper.LogMapper;
import yaoshu.token.pojo.entity.Log;

import java.util.List;
import java.util.stream.Stream;

/**
 * 日志管理服务  */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogManagementService {

    private static final int DELETE_BATCH_SIZE = 100;
    private final LogMapper logMapper;

    /** 分页查询所有日志 */
    public List<Log> getAllLogs(Integer logType, Long startTimestamp, Long endTimestamp,
                                String modelName, String username, String tokenName,
                                Integer channel, String group, String requestId, String upstreamRequestId) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Log> qw = buildLogQuery(logType, startTimestamp, endTimestamp,
                modelName, username, tokenName, channel, group, requestId, upstreamRequestId);
        qw.orderByDesc(Log::getId);
        return logMapper.selectList(qw);
    }

    /** 查询用户日志 */
    public List<Log> getUserLogs(int userId, Integer logType, Long startTimestamp, Long endTimestamp,
                                  String modelName, String tokenName,
                                  String group, String requestId, String upstreamRequestId) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Log> qw = buildLogQuery(logType, startTimestamp, endTimestamp,
                modelName, null, tokenName, null, group, requestId, upstreamRequestId);
        qw.eq(Log::getUserId, userId);
        qw.orderByDesc(Log::getId);
        return logMapper.selectList(qw);
    }

    /** 统计 quota/rpm/tpm */
    public StatResult sumUsedQuota(Integer logType, Long startTimestamp, Long endTimestamp,
                                    String modelName, String username, String tokenName,
                                    Integer channel, String group) {
        LambdaQueryWrapper<Log> qw = buildLogQuery(logType, startTimestamp, endTimestamp,
                modelName, username, tokenName, channel, group, null, null);
        var logs = logMapper.selectList(qw.select(Log::getQuota, Log::getPromptTokens,
                Log::getCompletionTokens, Log::getUseTime, Log::getCreatedAt));
        int quotaSum = 0;
        int promptSum = 0;
        int completionSum = 0;
        for (Log l : logs) {
            if (l.getQuota() != null) quotaSum += l.getQuota();
            if (l.getPromptTokens() != null) promptSum += l.getPromptTokens();
            if (l.getCompletionTokens() != null) completionSum += l.getCompletionTokens();
        }
        // rpm: 按分钟去重计数
        long rpm = logs.stream()
                .map(l -> l.getCreatedAt() != null ? l.getCreatedAt() / 60 : 0L)
                .distinct().count();
        return new StatResult(quotaSum, rpm, promptSum + completionSum);
    }

    /** 按 token_id 查询日志 */
    public List<Log> getByTokenId(int tokenId) {
        return logMapper.selectList(new LambdaQueryWrapper<Log>()
                .eq(Log::getTokenId, tokenId)
                .orderByDesc(Log::getId));
    }

    /** 删除指定时间戳之前的历史日志 */
    @Transactional(rollbackFor = Exception.class)
    public long deleteOldLogs(long targetTimestamp) {
        if (targetTimestamp <= 0) {
            throw new IllegalArgumentException("target timestamp is required");
        }
        return logMapper.delete(new LambdaUpdateWrapper<Log>()
                .lt(Log::getCreatedAt, targetTimestamp)
                .last("LIMIT " + DELETE_BATCH_SIZE));
    }

    /** 统计结果 */
    public record StatResult(int quota, long rpm, long tpm) {}
    public record StatData(int quota, long rpm, long tpm) {}

    private LambdaQueryWrapper<Log> buildLogQuery(Integer logType, Long startTimestamp, Long endTimestamp,
                                                   String modelName, String username, String tokenName,
                                                   Integer channel, String group, String requestId,
                                                   String upstreamRequestId) {
        LambdaQueryWrapper<Log> qw = new LambdaQueryWrapper<>();
        if (logType != null) qw.eq(Log::getType, logType);
        if (startTimestamp != null) qw.ge(Log::getCreatedAt, startTimestamp);
        if (endTimestamp != null) qw.le(Log::getCreatedAt, endTimestamp);
        if (modelName != null && !modelName.isBlank()) qw.eq(Log::getModelName, modelName.trim());
        if (username != null && !username.isBlank()) qw.eq(Log::getUsername, username.trim());
        if (tokenName != null && !tokenName.isBlank()) qw.eq(Log::getTokenName, tokenName.trim());
        if (channel != null) qw.eq(Log::getChannelId, channel);
        if (group != null && !group.isBlank()) qw.eq(Log::getGroup, group.trim());
        if (requestId != null && !requestId.isBlank()) qw.eq(Log::getRequestId, requestId.trim());
        if (upstreamRequestId != null && !upstreamRequestId.isBlank())
            qw.eq(Log::getUpstreamRequestId, upstreamRequestId.trim());
        return qw;
    }
}
