package yaoshu.token.relay.channel.openai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.relay.common.RelayInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAIAdaptor system role 转换白盒测试。
 * <p>
 * 防回归：确认 system role 不会被改写为 developer。
 * 第三方聚合器普遍不支持 developer role，转换后会导致 400 unknown variant `developer`。
 */
@DisplayName("OpenAIAdaptor — system role 不被改写为 developer")
class OpenAIAdaptorRoleTest {

    @Test
    @DisplayName("GPT-5 模型 system role 保持不变")
    void gpt5SystemRoleNotConverted() throws Exception {
        OpenAIAdaptor adaptor = createAdaptor();
        GeneralOpenAIRequest request = createRequestWithSystemRole("gpt-5.4");

        adaptor.convertOpenAIRequest(createRelayInfo("gpt-5.4"), request);

        assertEquals("system", request.getMessages().get(0).getRole(),
                "GPT-5 模型的 system role 不应被改写为 developer");
    }

    @Test
    @DisplayName("o3 模型 system role 保持不变")
    void o3SystemRoleNotConverted() throws Exception {
        OpenAIAdaptor adaptor = createAdaptor();
        GeneralOpenAIRequest request = createRequestWithSystemRole("o3-mini");

        adaptor.convertOpenAIRequest(createRelayInfo("o3-mini"), request);

        assertEquals("system", request.getMessages().get(0).getRole(),
                "o 系列模型的 system role 不应被改写为 developer");
    }

    @Test
    @DisplayName("非 o 系列/GPT-5 模型不受影响")
    void nonGPT5ModelUnaffected() throws Exception {
        OpenAIAdaptor adaptor = createAdaptor();
        GeneralOpenAIRequest request = createRequestWithSystemRole("gpt-4o");

        adaptor.convertOpenAIRequest(createRelayInfo("gpt-4o"), request);

        assertEquals("system", request.getMessages().get(0).getRole());
        assertNull(request.getMaxCompletionTokens(),
                "非 o 系列/GPT-5 模型不应触发 max_completion_tokens 转换");
    }

    @Test
    @DisplayName("GPT-5 模型 max_tokens 仍正确提升为 max_completion_tokens")
    void gpt5MaxTokensPromoted() throws Exception {
        OpenAIAdaptor adaptor = createAdaptor();
        GeneralOpenAIRequest request = createRequestWithSystemRole("gpt-5.4");
        request.setMaxTokens(1000);

        adaptor.convertOpenAIRequest(createRelayInfo("gpt-5.4"), request);

        assertEquals(1000, request.getMaxCompletionTokens(),
                "GPT-5 的 max_tokens 应提升为 max_completion_tokens");
        assertNull(request.getMaxTokens(),
                "提升后 max_tokens 应置空");
    }

    // ======================== 辅助方法 ========================

    private OpenAIAdaptor createAdaptor() {
        OpenAIAdaptor adaptor = new OpenAIAdaptor();
        RelayInfo initInfo = new RelayInfo();
        initInfo.setChannelType(ChannelConstants.CHANNEL_TYPE_OPENAI);
        adaptor.init(initInfo);
        return adaptor;
    }

    private GeneralOpenAIRequest createRequestWithSystemRole(String model) {
        GeneralOpenAIRequest request = new GeneralOpenAIRequest();
        request.setModel(model);

        List<GeneralOpenAIRequest.Message> messages = new ArrayList<>();
        GeneralOpenAIRequest.Message systemMsg = new GeneralOpenAIRequest.Message();
        systemMsg.setRole("system");
        systemMsg.setContent("You are a helpful assistant.");
        messages.add(systemMsg);

        GeneralOpenAIRequest.Message userMsg = new GeneralOpenAIRequest.Message();
        userMsg.setRole("user");
        userMsg.setContent("Hello");
        messages.add(userMsg);

        request.setMessages(messages);
        return request;
    }

    private RelayInfo createRelayInfo(String upstreamModel) {
        return new RelayInfo()
                .setChannelType(ChannelConstants.CHANNEL_TYPE_OPENAI)
                .setUpstreamModelName(upstreamModel);
    }
}
