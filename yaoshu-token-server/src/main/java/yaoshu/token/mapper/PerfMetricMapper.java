package yaoshu.token.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import yaoshu.token.pojo.entity.PerfMetric;

/**
 * PerfMetric Mapper  */
@Mapper
public interface PerfMetricMapper extends BaseMapper<PerfMetric> {

    /**
     * 按模型/分组/时间范围查询      */
    @Select("<script>"
            + "SELECT * FROM perf_metrics "
            + "WHERE model_name = #{modelName} "
            + "AND bucket_ts &gt;= #{startTs} AND bucket_ts &lt;= #{endTs} "
            + "<if test='group != null and group != \"\"'> AND `group` = #{group} </if>"
            + "ORDER BY bucket_ts ASC"
            + "</script>")
    List<PerfMetric> getPerfMetrics(@Param("modelName") String modelName,
                                    @Param("group") String group,
                                    @Param("startTs") long startTs,
                                    @Param("endTs") long endTs);

    /**
     * 聚合汇总所有模型      */
    @Select("<script>"
            + "SELECT model_name, "
            + "SUM(request_count) AS request_count, "
            + "SUM(success_count) AS success_count, "
            + "SUM(total_latency_ms) AS total_latency_ms, "
            + "SUM(output_tokens) AS output_tokens, "
            + "SUM(generation_ms) AS generation_ms "
            + "FROM perf_metrics "
            + "WHERE bucket_ts &gt;= #{startTs} AND bucket_ts &lt;= #{endTs} "
            + "<if test='groups != null and groups.size() > 0'> "
            + "  AND `group` IN "
            + "  <foreach collection='groups' item='g' open='(' separator=',' close=')'> #{g} </foreach>"
            + "</if>"
            + "<if test='groups != null and groups.size() == 0'> AND 1=0 </if>"
            + "GROUP BY model_name "
            + "HAVING SUM(request_count) > 0 "
            + "ORDER BY request_count DESC"
            + "</script>")
    List<PerfMetricSummary> getPerfMetricsSummaryAll(@Param("startTs") long startTs,
                                                      @Param("endTs") long endTs,
                                                      @Param("groups") List<String> groups);

    /**
     * 清理过期数据      */
    @Delete("DELETE FROM perf_metrics WHERE bucket_ts < #{cutoffTs}")
    int deletePerfMetricsBefore(@Param("cutoffTs") long cutoffTs);

    /**
     * Upsert 性能采样数据到 perf_metrics      * <p>
     * 唯一键为 (model_name, `group`, bucket_ts)，冲突时累加计数。
     */
    @Insert("<script>"
            + "INSERT INTO perf_metrics (model_name, `group`, bucket_ts, request_count, success_count, "
            + "  total_latency_ms, ttft_sum_ms, ttft_count, output_tokens, generation_ms) "
            + "VALUES (#{p.modelName}, #{p.group}, #{p.bucketTs}, 1, "
            + "  #{p.successCount}, #{p.totalLatencyMs}, #{p.ttftSumMs}, #{p.ttftCount}, "
            + "  #{p.outputTokens}, #{p.generationMs}) "
            + "ON DUPLICATE KEY UPDATE "
            + "  request_count = request_count + 1, "
            + "  success_count = success_count + VALUES(success_count), "
            + "  total_latency_ms = total_latency_ms + VALUES(total_latency_ms), "
            + "  ttft_sum_ms = ttft_sum_ms + VALUES(ttft_sum_ms), "
            + "  ttft_count = ttft_count + VALUES(ttft_count), "
            + "  output_tokens = output_tokens + VALUES(output_tokens), "
            + "  generation_ms = generation_ms + VALUES(generation_ms)"
            + "</script>")
    int upsertRelaySample(@Param("p") PerfMetric metric);

    /**
     * 批量 Upsert 热桶 flush 数据      * <p>
     * 与 upsertRelaySample 不同，此处 request_count 使用累加值（而非硬编码 1）。
     */
    @Insert("<script>"
            + "INSERT INTO perf_metrics (model_name, `group`, bucket_ts, request_count, success_count, "
            + "  total_latency_ms, ttft_sum_ms, ttft_count, output_tokens, generation_ms) "
            + "VALUES (#{p.modelName}, #{p.group}, #{p.bucketTs}, #{p.requestCount}, "
            + "  #{p.successCount}, #{p.totalLatencyMs}, #{p.ttftSumMs}, #{p.ttftCount}, "
            + "  #{p.outputTokens}, #{p.generationMs}) "
            + "ON DUPLICATE KEY UPDATE "
            + "  request_count = request_count + VALUES(request_count), "
            + "  success_count = success_count + VALUES(success_count), "
            + "  total_latency_ms = total_latency_ms + VALUES(total_latency_ms), "
            + "  ttft_sum_ms = ttft_sum_ms + VALUES(ttft_sum_ms), "
            + "  ttft_count = ttft_count + VALUES(ttft_count), "
            + "  output_tokens = output_tokens + VALUES(output_tokens), "
            + "  generation_ms = generation_ms + VALUES(generation_ms)"
            + "</script>")
    int upsertPerfMetricBatch(@Param("p") PerfMetric metric);

    /** 聚合汇总结果 */
    class PerfMetricSummary {
        private String modelName;
        private long requestCount;
        private long successCount;
        private long totalLatencyMs;
        private long outputTokens;
        private long generationMs;

        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
        public long getRequestCount() { return requestCount; }
        public void setRequestCount(long requestCount) { this.requestCount = requestCount; }
        public long getSuccessCount() { return successCount; }
        public void setSuccessCount(long successCount) { this.successCount = successCount; }
        public long getTotalLatencyMs() { return totalLatencyMs; }
        public void setTotalLatencyMs(long totalLatencyMs) { this.totalLatencyMs = totalLatencyMs; }
        public long getOutputTokens() { return outputTokens; }
        public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
        public long getGenerationMs() { return generationMs; }
        public void setGenerationMs(long generationMs) { this.generationMs = generationMs; }
    }
}
