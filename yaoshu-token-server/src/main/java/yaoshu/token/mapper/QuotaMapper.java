package yaoshu.token.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import yaoshu.token.pojo.entity.QuotaData;

/**
 * QuotaData Mapper  */
@Mapper
public interface QuotaMapper extends BaseMapper<QuotaData> {

    /**
     * 按模型聚合 Token 总量      */
    @Select("<script>"
            + "SELECT model_name, SUM(token_used) AS total_tokens "
            + "FROM quota_data "
            + "WHERE model_name != '' "
            + "<if test='startTime != null and startTime > 0'> AND created_at >= #{startTime} </if>"
            + "<if test='endTime != null and endTime > 0'> AND created_at &lt;= #{endTime} </if>"
            + "GROUP BY model_name "
            + "HAVING SUM(token_used) > 0 "
            + "ORDER BY total_tokens DESC"
            + "</script>")
    List<RankingQuotaTotal> getRankingQuotaTotals(@Param("startTime") Long startTime,
                                                   @Param("endTime") Long endTime);

    /**
     * 按时间桶聚合 Token      */
    @Select("<script>"
            + "SELECT model_name, "
            + "FLOOR(created_at / #{bucketSize}) * #{bucketSize} AS bucket, "
            + "SUM(token_used) AS tokens "
            + "FROM quota_data "
            + "WHERE model_name != '' "
            + "<if test='startTime != null and startTime > 0'> AND created_at >= #{startTime} </if>"
            + "<if test='endTime != null and endTime > 0'> AND created_at &lt;= #{endTime} </if>"
            + "GROUP BY model_name, bucket "
            + "HAVING SUM(token_used) > 0 "
            + "ORDER BY bucket ASC"
            + "</script>")
    List<RankingQuotaBucket> getRankingQuotaBuckets(@Param("startTime") Long startTime,
                                                     @Param("endTime") Long endTime,
                                                     @Param("bucketSize") long bucketSize);

    /**
     * 按 model_name + created_at 聚合，用于 /api/data/（管理员）
     */
    @Select("<script>"
            + "SELECT model_name, SUM(count) AS count, SUM(quota) AS quota, SUM(token_used) AS token_used, created_at "
            + "FROM quota_data "
            + "WHERE 1=1 "
            + "<if test='startTime != null and startTime > 0'> AND created_at >= #{startTime} </if>"
            + "<if test='endTime != null and endTime > 0'> AND created_at &lt;= #{endTime} </if>"
            + "GROUP BY model_name, created_at "
            + "ORDER BY created_at DESC"
            + "</script>")
    List<QuotaData> getAllQuotaDatesAgg(@Param("startTime") Long startTime,
                                         @Param("endTime") Long endTime);

    /**
     * 按 username + created_at 聚合，用于 /api/data/users（管理员）
     */
    @Select("<script>"
            + "SELECT user_id, username, SUM(count) AS count, SUM(quota) AS quota, SUM(token_used) AS token_used, created_at "
            + "FROM quota_data "
            + "WHERE 1=1 "
            + "<if test='startTime != null and startTime > 0'> AND created_at >= #{startTime} </if>"
            + "<if test='endTime != null and endTime > 0'> AND created_at &lt;= #{endTime} </if>"
            + "GROUP BY user_id, username, created_at "
            + "ORDER BY created_at DESC"
            + "</script>")
    List<QuotaData> getQuotaDataGroupByUser(@Param("startTime") Long startTime,
                                             @Param("endTime") Long endTime);

    /**
     * 从 logs 表增量聚合消费日志（type=2），供 QuotaDataAggregationTask 写入 quota_data。
     * <p>
     * 按 (user_id, username, model_name, 小时桶) 分组，对齐 Go usedata.go 的小时对齐语义
     * （{@code createdAt - createdAt%3600}）。token_used = prompt_tokens + completion_tokens。
     *
     * @param lastId     上次聚合到的 logs.id（ exclusive，聚合 id &gt; lastId 的记录）
     * @param batchEndId 本批聚合上限 logs.id（ inclusive）
     */
    @Select("SELECT user_id, username, model_name, " +
            "FLOOR(created_at / 3600) * 3600 AS created_at, " +
            "SUM(prompt_tokens + completion_tokens) AS token_used, " +
            "COUNT(*) AS count, " +
            "SUM(quota) AS quota " +
            "FROM logs " +
            "WHERE id > #{lastId} AND id <= #{batchEndId} " +
            "AND type = 2 AND model_name != '' " +
            "GROUP BY user_id, username, model_name, FLOOR(created_at / 3600) * 3600")
    List<QuotaData> aggregateLogsForQuotaData(@Param("lastId") long lastId,
                                               @Param("batchEndId") long batchEndId);

    /**
     * 查询 logs 表当前最大 id，用于 QuotaDataAggregationTask 确定聚合上界
     */
    @Select("SELECT COALESCE(MAX(id), 0) FROM logs")
    Long selectMaxLogId();

    /** 与 Go RankingQuotaTotal 对应的聚合结果 */
    class RankingQuotaTotal {
        private String modelName;
        private Long totalTokens;

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public Long getTotalTokens() { return totalTokens; }
        public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    }

    /** 与 Go RankingQuotaBucket 对应的桶聚合结果 */
    class RankingQuotaBucket {
        private String modelName;
        private Long bucket;
        private Long tokens;

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public Long getBucket() { return bucket; }
        public void setBucket(Long bucket) { this.bucket = bucket; }
        public Long getTokens() { return tokens; }
        public void setTokens(Long tokens) { this.tokens = tokens; }
    }
}
