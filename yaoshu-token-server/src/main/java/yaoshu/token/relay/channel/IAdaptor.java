package yaoshu.token.relay.channel;

import yaoshu.token.pojo.dto.*;
import yaoshu.token.relay.common.RelayInfo;

import java.io.Reader;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * 渠道适配器接口  * <p>
 * 每个 AI 厂商渠道实现此接口，封装厂商特定的请求转换、签名、响应解析逻辑。
 * Go 通过 interface 多态 + 结构体嵌入实现，Java 通过接口实现。
 */
public interface IAdaptor {

    /** 初始化适配器 */
    void init(RelayInfo info);

    /** 获取上游请求 URL */
    String getRequestURL(RelayInfo info) throws Exception;

    /** 设置请求头 */
    Map<String, String> setupRequestHeader(RelayInfo info) throws Exception;

    /** 转换 OpenAI 请求 */
    Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception;

    /** 转换 Rerank 请求 */
    Object convertRerankRequest(RelayInfo info, int relayMode, yaoshu.token.pojo.dto.RerankRequest rerankRequest) throws Exception;

    /** 转换 Embedding 请求 */
    Object convertEmbeddingRequest(RelayInfo info, EmbeddingDTO embeddingRequest) throws Exception;

    /** 转换 Audio 请求 */
    Reader convertAudioRequest(RelayInfo info, AudioDTO audioRequest) throws Exception;

    /** 转换 Image 请求 */
    Object convertImageRequest(RelayInfo info, OpenAIImageDTO imageRequest) throws Exception;

    /** 转换 OpenAI Responses 请求 */
    Object convertOpenAIResponsesRequest(RelayInfo info, OpenAIResponsesRequest responsesRequest) throws Exception;

    /** 发送请求 */
    Object doRequest(RelayInfo info, Object requestBody) throws Exception;

    /** 处理响应*/
    DoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception;

    /** 获取支持的模型列表 */
    List<String> getModelList();

    /** 获取渠道名称 */
    String getChannelName();

    /** 转换 Claude 请求 */
    Object convertClaudeRequest(RelayInfo info, ClaudeDTO.ClaudeRequest claudeRequest) throws Exception;

    /** 转换 Gemini 请求 */
    Object convertGeminiRequest(RelayInfo info, GeminiDTO.GeminiChatRequest geminiRequest) throws Exception;

    // ======================== 响应结果 ========================

    /**
     * DoResponse 返回结果，封装 usage + error 的多返回值语义
     */
    class DoResponseResult {
        private final Object usage;
        private final RelayException error;

        public DoResponseResult(Object usage) {
            this.usage = usage;
            this.error = null;
        }

        public DoResponseResult(Object usage, RelayException error) {
            this.usage = usage;
            this.error = error;
        }

        public static DoResponseResult success(Object usage) {
            return new DoResponseResult(usage);
        }

        public static DoResponseResult failure(RelayException error) {
            return new DoResponseResult(null, error);
        }

        public Object getUsage() { return usage; }
        public RelayException getError() { return error; }
        public boolean isError() { return error != null; }
    }

    // ======================== Task 适配器接口 ========================

    /**
     * 异步 Task 适配器接口      * <p>
     * 用于视频生成、音乐生成等异步任务的渠道适配。
     */
    interface ITaskAdaptor {

        void init(RelayInfo info);

        /** 验证请求并设置 action */
        Object validateRequestAndSetAction(RelayInfo info);

        /** 估算计费：根据用户请求返回 OtherRatios（时长、分辨率等） */
        Map<String, Double> estimateBilling(RelayInfo info);

        /** 提交后计费调整：根据上游实际返回调整 OtherRatios */
        Map<String, Double> adjustBillingOnSubmit(RelayInfo info, byte[] taskData);

        /** 任务完成时计费调整：返回实际额度（正数触发差额结算） */
        int adjustBillingOnComplete(Object task, Object taskResult);

        String buildRequestURL(RelayInfo info) throws Exception;

        Object buildRequestHeader(RelayInfo info) throws Exception;

        Object buildRequestBody(RelayInfo info) throws Exception;

        HttpResponse<?> doRequest(RelayInfo info, Object requestBody) throws Exception;

        TaskDoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp) throws Exception;

        List<String> getModelList();

        String getChannelName();

        /** 轮询：获取上游任务状态 */
        HttpResponse<?> fetchTask(String baseUrl, String key, Map<String, Object> body, String proxy) throws Exception;

        /** 轮询：解析任务结果 */
        Object parseTaskResult(byte[] respBody) throws Exception;
    }

    /**
     * Task 适配器 DoResponse 返回结果
     */
    class TaskDoResponseResult {
        private final String taskID;
        private final byte[] taskData;
        private final Object error;

        public TaskDoResponseResult(String taskID, byte[] taskData) {
            this.taskID = taskID;
            this.taskData = taskData;
            this.error = null;
        }

        public TaskDoResponseResult(String taskID, byte[] taskData, Object error) {
            this.taskID = taskID;
            this.taskData = taskData;
            this.error = error;
        }

        public static TaskDoResponseResult success(String taskID, byte[] taskData) {
            return new TaskDoResponseResult(taskID, taskData);
        }

        public static TaskDoResponseResult failure(Object error) {
            return new TaskDoResponseResult(null, null, error);
        }

        public String getTaskID() { return taskID; }
        public byte[] getTaskData() { return taskData; }
        public Object getError() { return error; }
        public boolean isError() { return error != null; }
    }

    /**
     * OpenAI Video 转换接口      */
    interface OpenAIVideoConverter {
        byte[] convertToOpenAIVideo(Object originTask) throws Exception;
    }

    // ======================== DTO ========================

}
