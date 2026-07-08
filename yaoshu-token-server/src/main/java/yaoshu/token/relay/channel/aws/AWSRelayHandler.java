package yaoshu.token.relay.channel.aws;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.ClaudeDTO;
import yaoshu.token.relay.channel.IAdaptor;
import yaoshu.token.relay.channel.claude.ClaudeAdaptor;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;

import java.net.http.HttpResponse;

/**
 * AWS Bedrock 中转处理器  * <p>
 * AWS Bedrock 支持 Claude 和 Nova 模型。
 * Claude 模型通过 AWS SDK InvokeModel API 调用，响应委托给 ClaudeAdaptor 处理。
 */
@Slf4j
public class AWSRelayHandler {    private static final String ANTHROPIC_VERSION = "bedrock-2023-05-31";

    /**
     * AWS Claude 请求格式化      * <p>
     * 设置 anthropic_version 并提取 anthropic-beta header。
     */
    public static AWSDTOPlaceholder.AwsClaudeRequest formatClaudeRequest(
            ClaudeDTO.ClaudeRequest claudeRequest, java.util.Map<String, String> requestHeaders) throws Exception {

        AWSDTOPlaceholder.AwsClaudeRequest awsRequest = new AWSDTOPlaceholder.AwsClaudeRequest();
        awsRequest.setAnthropicVersion(ANTHROPIC_VERSION);
        awsRequest.setSystem(claudeRequest.getSystem());
        awsRequest.setMessages(claudeRequest.getMessages());
        awsRequest.setMaxTokens(claudeRequest.getMaxTokens());
        awsRequest.setTemperature(claudeRequest.getTemperature());
        awsRequest.setTopP(claudeRequest.getTopP());
        awsRequest.setTopK(claudeRequest.getTopK());
        awsRequest.setStopSequences(claudeRequest.getStopSequences());
        awsRequest.setTools(claudeRequest.getTools());
        awsRequest.setToolChoice(claudeRequest.getToolChoice());
        awsRequest.setThinking(claudeRequest.getThinking());
        awsRequest.setOutputConfig(claudeRequest.getOutputConfig());

        // anthropic-beta header
        if (requestHeaders != null) {
            String betaHeader = requestHeaders.get("anthropic-beta");
            if (betaHeader != null && !betaHeader.isEmpty()) {
                awsRequest.setAnthropicBeta(java.util.Arrays.asList(betaHeader.split(",")));
            }
        }

        return awsRequest;
    }

    /**
     * AWS 响应处理      * <p>
     * Claude 模型委托给 ClaudeAdaptor，Nova/OpenAI 兼容模型委托给 OpenAIAdaptor。
     */
    public static IAdaptor.DoResponseResult doResponse(RelayInfo info, HttpResponse<?> resp, boolean isNova) throws Exception {
        if (isNova) {
            // Nova 模型使用 OpenAI 兼容响应处理
            return new OpenAIAdaptor().doResponse(info, resp);
        }
        // Claude 模型委托给 ClaudeAdaptor
        return new ClaudeAdaptor().doResponse(info, resp);
    }
}
