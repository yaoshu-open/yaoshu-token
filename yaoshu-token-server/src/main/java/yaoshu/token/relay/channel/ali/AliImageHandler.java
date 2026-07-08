package yaoshu.token.relay.channel.ali;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.OpenAIImageDTO;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.*;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.relay.common.RelayInfo.Usage;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 阿里图片生成中转处理器  * <p>
 * 支持同步图片模型（多模态生成）和异步图片模型（任务轮询）两种模式。
 */
@Slf4j
public class AliImageHandler {    /**
     * OpenAI 图片请求 → 阿里图片请求      */
    @SuppressWarnings("unchecked")
    public static AliImageRequest oaiImage2AliImageRequest(RelayInfo info, OpenAIImageDTO request, boolean isSync) throws Exception {
        AliImageRequest imageRequest = new AliImageRequest();
        imageRequest.setModel(request.getModel());
        imageRequest.setResponseFormat(request.getResponseFormat());

        AliImageParameters parameters = new AliImageParameters();

        // 从 extra 字段提取阿里特有参数
        Map<String, Object> extra = request.getExtra();
        if (extra != null) {
            Object parametersVal = extra.get("parameters");
            if (parametersVal != null) {
                parameters = Convert.toJavaBean(parametersVal, AliImageParameters.class);
            } else {
                // 兼容没有 parameters 字段的情况，从 openai 标准字段中提取
                String size = request.getSize();
                if (size != null) {
                    parameters.setSize(size.replace("x", "*"));
                }
                int n = (request.getN() != null) ? request.getN() : 1;
                parameters.setN(n);
                parameters.setWatermark(request.getWatermark());
            }

            Object inputVal = extra.get("input");
            if (inputVal != null) {
                imageRequest.setInput(Convert.toJavaBean(inputVal, Object.class));
            }
        } else {
            // 无 extra 时从标准字段提取
            String size = request.getSize();
            if (size != null) {
                parameters.setSize(size.replace("x", "*"));
            }
            int n = (request.getN() != null) ? request.getN() : 1;
            parameters.setN(n);
            parameters.setWatermark(request.getWatermark());
        }

        imageRequest.setParameters(parameters);

        // z-image prompt_extend 按 2 倍计费
        if (request.getModel() != null && request.getModel().contains("z-image")) {
            if (parameters.promptExtendValue() && info.getPriceData() != null) {
                info.getPriceData().addOtherRatio("prompt_extend", 2);
            }
        }

        // n 参数计费
        if (parameters.getN() != null && parameters.getN() != 0 && info.getPriceData() != null) {
            info.getPriceData().addOtherRatio("n", parameters.getN());
        }

        // 同步/异步模型请求格式不同
        if (imageRequest.getInput() == null) {
            if (isSync) {
                // 同步模型使用 messages 格式
                AliImageInput input = new AliImageInput();
                AliMessage message = new AliMessage();
                message.setRole("user");
                AliMediaContent content = new AliMediaContent();
                content.setText(request.getPrompt());
                message.setContent(List.of(content));
                input.setMessages(List.of(message));
                imageRequest.setInput(input);
            } else {
                // 异步模型使用 prompt 格式
                AliImageInput input = new AliImageInput();
                input.setPrompt(request.getPrompt());
                imageRequest.setInput(input);
            }
        }

        return imageRequest;
    }

    /**
     * 查询异步任务状态      */
    private static AliResponse updateTask(RelayInfo info, String taskId) throws Exception {
        String url = info.getChannelBaseUrl() + "/api/v1/tasks/" + taskId;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + info.getApiKey())
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return Convert.toJavaBean(resp.body(), AliResponse.class);
    }

    /**
     * 异步任务轮询等待      */
    private static AliResponse asyncTaskWait(RelayInfo info, String taskId) throws Exception {
        int waitSeconds = 10;
        int step = 0;
        int maxStep = 20;

        Thread.sleep(5000);

        while (true) {
            log.debug("asyncTaskWait step {}/{}, wait {} seconds", step, maxStep, waitSeconds);
            step++;
            AliResponse rsp;
            try {
                rsp = updateTask(info, taskId);
            } catch (Exception e) {
                log.warn("asyncTaskWait updateTask err: {}", e.getMessage());
                Thread.sleep(waitSeconds * 1000L);
                continue;
            }

            if (rsp.getOutput() == null || rsp.getOutput().getTaskStatus() == null
                    || rsp.getOutput().getTaskStatus().isEmpty()) {
                return rsp;
            }

            String status = rsp.getOutput().getTaskStatus();
            switch (status) {
                case "FAILED":
                case "CANCELED":
                case "SUCCEEDED":
                case "UNKNOWN":
                    return rsp;
            }

            if (step >= maxStep) {
                break;
            }
            Thread.sleep(waitSeconds * 1000L);
        }

        throw new RuntimeException("ali asyncTaskWait timeout");
    }

    /**
     * 阿里图片响应 → OpenAI 图片响应      */
    private static Map<String, Object> responseAli2OpenAIImage(AliResponse response, byte[] originBody, RelayInfo info, String responseFormat) {
        Map<String, Object> imageResponse = new LinkedHashMap<>();
        imageResponse.put("created", System.currentTimeMillis() / 1000);

        List<Map<String, Object>> dataList = new ArrayList<>();
        AliOutput output = response.getOutput();
        if (output != null) {
            // results 格式（异步模型）
            if (output.getResults() != null && !output.getResults().isEmpty()) {
                for (TaskResult result : output.getResults()) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    if (result.getUrl() != null && !result.getUrl().isEmpty()) {
                        data.put("url", result.getUrl());
                    }
                    if (result.getB64Image() != null && !result.getB64Image().isEmpty()) {
                        data.put("b64_json", result.getB64Image());
                    }
                    dataList.add(data);
                }
            }
            // choices 格式（同步模型）
            if (output.getChoices() != null && !output.getChoices().isEmpty()) {
                for (AliOutputChoice choice : output.getChoices()) {
                    if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
                        for (AliMediaContent content : choice.getMessage().getContent()) {
                            Map<String, Object> data = new LinkedHashMap<>();
                            if (content.getImage() != null && !content.getImage().isEmpty()) {
                                if (content.getImage().startsWith("http")) {
                                    data.put("url", content.getImage());
                                } else {
                                    data.put("b64_json", content.getImage());
                                }
                            } else if (content.getText() != null && !content.getText().isEmpty()) {
                                data.put("revised_prompt", content.getText());
                            }
                            if (!data.isEmpty()) {
                                dataList.add(data);
                            }
                        }
                    }
                }
            }
        }
        imageResponse.put("data", dataList);
        imageResponse.put("metadata", new String(originBody));
        return imageResponse;
    }

    /**
     * 阿里图片响应处理主入口      */
    public static Usage aliImageHandler(RelayInfo info, HttpResponse<InputStream> resp, boolean isSyncImageModel) throws Exception {
        HttpServletResponse response = info.getResponse();

        byte[] responseBody;
        try (InputStream bodyStream = resp.body()) {
            responseBody = bodyStream.readAllBytes();
        }

        AliResponse aliTaskResponse = Convert.toJavaBean(new String(responseBody, java.nio.charset.StandardCharsets.UTF_8), AliResponse.class);

        if (aliTaskResponse.getMessage() != null && !aliTaskResponse.getMessage().isEmpty()) {
            throw new RuntimeException("ali async task failed: " + aliTaskResponse.getMessage());
        }

        AliResponse aliResponse;
        byte[] originRespBody;

        if (isSyncImageModel) {
            aliResponse = aliTaskResponse;
            originRespBody = responseBody;
        } else {
            // 异步图片模型需要轮询任务结果
            String taskId = (aliTaskResponse.getOutput() != null) ? aliTaskResponse.getOutput().getTaskId() : null;
            if (taskId == null) {
                throw new RuntimeException("ali async image: missing task_id");
            }
            aliResponse = asyncTaskWait(info, taskId);
            originRespBody = Convert.toJSONString(aliResponse).getBytes(java.nio.charset.StandardCharsets.UTF_8);

            if (!"SUCCEEDED".equals(aliResponse.getOutput().getTaskStatus())) {
                throw new RuntimeException("ali async image task status: "
                        + aliResponse.getOutput().getTaskStatus()
                        + ", message: " + aliResponse.getOutput().getMessage());
            }
        }

        if (isSyncImageModel) {
            log.debug("ali sync image result received");
        } else {
            log.debug("ali async image result received");
        }

        // response_format 来自客户端请求头/参数（Go: c.GetString("response_format")）
        String responseFormat = null;
        if (info.getClientHeaders() != null) {
            responseFormat = info.getClientHeaders().get("response_format");
        }
        Map<String, Object> imageResponses = responseAli2OpenAIImage(aliResponse, originRespBody, info, responseFormat);

        // 计费：image_count 或 data 数量
        if (info.getPriceData() != null) {
            if (aliResponse.getUsage() != null && aliResponse.getUsage().getImageCount() != 0) {
                info.getPriceData().addOtherRatio("n", aliResponse.getUsage().getImageCount());
            } else if (imageResponses.get("data") instanceof List<?> dataList && !dataList.isEmpty()) {
                info.getPriceData().addOtherRatio("n", dataList.size());
            }
        }

        byte[] jsonResponse = Convert.toJSONString(imageResponses).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        response.setContentType("application/json");
        response.setStatus(resp.statusCode());
        response.getOutputStream().write(jsonResponse);
        response.getOutputStream().flush();

        return new Usage();
    }
}
