package yaoshu.token.relay.channel.gemini;

import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.config.ReasoningSuffixConfig;
import yaoshu.token.config.model.GeminiModelConfig;
import yaoshu.token.constant.ChannelConstants;
import yaoshu.token.pojo.dto.GeminiDTO;
import yaoshu.token.pojo.dto.GeneralOpenAIRequest;
import yaoshu.token.pojo.dto.OpenAIRequestDTO;
import yaoshu.token.relay.channel.openai.OpenAIAdaptor;
import yaoshu.token.relay.common.RelayInfo;
import yaoshu.token.service.DownloadService;
import yaoshu.token.service.ImageService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gemini 渠道适配器  * <p>
 * 支持 Vertex AI / Gemini API 两种接入方式，覆盖 Chat/Image/Embedding/Veo 等模式。
 */
@Slf4j
public class GeminiAdaptor extends OpenAIAdaptor {    private static final Set<String> SUPPORTED_MIME_TYPES = Set.of(
            "application/pdf",
            "audio/mpeg",
            "audio/mp3",
            "audio/wav",
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/webp",
            "image/heic",
            "image/heif",
            "text/plain",
            "video/mov",
            "video/mpeg",
            "video/mp4",
            "video/mpg",
            "video/avi",
            "video/wmv",
            "video/mpegps",
            "video/flv"
    );

    private static final String THOUGHT_SIGNATURE_BYPASS_VALUE = "context_engineering_is_the_way_to_go";

    private static final List<String> SAFETY_SETTING_LIST = List.of(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
    );

    private static final Set<String> GEMINI_OPENAPI_SCHEMA_ALLOWED_FIELDS = Set.of(
            "anyOf", "default", "description", "enum", "example", "format", "items",
            "maxItems", "maxLength", "maxProperties", "maximum", "minItems", "minLength",
            "minProperties", "minimum", "nullable", "pattern", "properties", "propertyOrdering",
            "required", "title", "type"
    );

    private static final int GEMINI_FUNCTION_SCHEMA_MAX_DEPTH = 8;
    private static final int RESPONSE_SCHEMA_MAX_DEPTH = 5;

    @Override
    public String getRequestURL(RelayInfo info) throws Exception {
        String upstreamModel = info.getUpstreamModelName();
        GeminiModelConfig geminiSettings = GeminiModelConfig.getInstance();

        if (upstreamModel != null && upstreamModel.startsWith("imagen")) {
            String version = geminiSettings.getGeminiVersionSetting(upstreamModel);
            return info.getChannelBaseUrl() + "/" + version + "/models/" + upstreamModel + ":predict";
        }

        if (upstreamModel != null && upstreamModel.startsWith("veo")) {
            return info.getChannelBaseUrl() + "/v1/models/" + upstreamModel + ":predictLongRunning";
        }

        String version = geminiSettings.getGeminiVersionSetting(upstreamModel);
        return info.getChannelBaseUrl() + "/" + version + "/models/" + upstreamModel + ":streamGenerateContent?alt=sse";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> setupRequestHeader(RelayInfo info) throws Exception {
        Map<String, String> headers = super.setupRequestHeader(info);
        headers.put("x-goog-api-key", info.getApiKey());
        return headers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object convertOpenAIRequest(RelayInfo info, Object request) throws Exception {
        if (request == null) throw new IllegalArgumentException("request is nil");

        // 先复用 OpenAI 兼容预处理，再补回 Gemini 专属字段，保持公共语义一致。
        GeneralOpenAIRequest baseRequest = request instanceof GeneralOpenAIRequest generalRequest
                ? generalRequest
                : Convert.toJavaBean(request, GeneralOpenAIRequest.class);
        GeneralOpenAIRequest normalizedBaseRequest = (GeneralOpenAIRequest) super.convertOpenAIRequest(info, baseRequest);

        OpenAIRequestDTO openAIRequest = Convert.toJavaBean(normalizedBaseRequest, OpenAIRequestDTO.class);
        mergeGeminiSpecificFields(baseRequest, openAIRequest);

        return convertOpenAIToGemini(openAIRequest, info);
    }

    @Override
    public List<String> getModelList() { return GeminiConstant.MODEL_LIST; }
    @Override
    public String getChannelName() { return GeminiConstant.CHANNEL_NAME; }

    private void mergeGeminiSpecificFields(GeneralOpenAIRequest source, OpenAIRequestDTO target) {
        if (source.getTopK() != null) {
            target.setTopK(source.getTopK());
        }
        if (source.getSeed() != null) {
            target.setSeed(source.getSeed());
        }
        if (source.getExtraBody() != null) {
            target.setExtraBody(source.getExtraBody());
        }
        if (source.getResponseFormat() != null && target.getResponseFormat() == null) {
            target.setResponseFormat(Convert.toJavaBean(source.getResponseFormat(), OpenAIRequestDTO.ResponseFormat.class));
        }
        if (source.getToolChoice() != null) {
            target.setToolChoice(source.getToolChoice());
        }
        if (source.getTools() != null && !source.getTools().isEmpty()
                && (target.getTools() == null || target.getTools().isEmpty())) {
            List<OpenAIRequestDTO.ToolCallRequest> tools = new ArrayList<>();
            for (Object tool : source.getTools()) {
                tools.add(Convert.toJavaBean(tool, OpenAIRequestDTO.ToolCallRequest.class));
            }
            target.setTools(tools);
        }
    }

    private GeminiDTO.GeminiChatRequest convertOpenAIToGemini(OpenAIRequestDTO request, RelayInfo info) throws Exception {
        GeminiModelConfig geminiSettings = GeminiModelConfig.getInstance();

        GeminiDTO.GeminiChatRequest geminiRequest = new GeminiDTO.GeminiChatRequest();
        geminiRequest.setContents(new ArrayList<>());

        GeminiDTO.GeminiChatGenerationConfig generationConfig = new GeminiDTO.GeminiChatGenerationConfig();
        generationConfig.setTemperature(request.getTemperature());
        if (request.getTopP() != null && request.getTopP() > 0) {
            generationConfig.setTopP(request.getTopP());
        }
        if (request.getTopK() != null && request.getTopK() > 0) {
            generationConfig.setTopK(request.getTopK().doubleValue());
        }
        int maxTokens = resolveMaxTokens(request);
        if (maxTokens > 0) {
            generationConfig.setMaxOutputTokens(maxTokens);
        }
        if (request.getSeed() != null && request.getSeed() != 0D) {
            generationConfig.setSeed(request.getSeed().longValue());
        }
        if (geminiSettings.isGeminiModelSupportImagine(info.getUpstreamModelName())) {
            generationConfig.setResponseModalities(List.of("TEXT", "IMAGE"));
        }
        List<String> stopSequences = parseStopSequences(request.getStop());
        if (!stopSequences.isEmpty()) {
            generationConfig.setStopSequences(stopSequences.size() > 5 ? stopSequences.subList(0, 5) : stopSequences);
        }
        geminiRequest.setGenerationConfig(generationConfig);

        boolean attachThoughtSignature = isThoughtSignatureEnabled(info, geminiSettings);
        boolean adaptorWithExtraBody = applyExtraBody(request.getExtraBody(), info, geminiRequest);
        if (!adaptorWithExtraBody) {
            applyThinkingAdaptor(geminiRequest, info, request);
        }

        geminiRequest.setSafetySettings(buildSafetySettings(geminiSettings));
        geminiRequest.setToolConfig(convertToolChoiceToGeminiConfig(request.getToolChoice()));
        geminiRequest.setTools(convertTools(request.getTools()));
        applyResponseFormat(request.getResponseFormat(), geminiRequest);
        populateContents(request.getMessages(), geminiRequest, attachThoughtSignature);

        return geminiRequest;
    }

    private int resolveMaxTokens(OpenAIRequestDTO request) {
        int maxTokens = request.getMaxTokens() != null ? request.getMaxTokens() : 0;
        int maxCompletionTokens = request.getMaxCompletionTokens() != null ? request.getMaxCompletionTokens() : 0;
        return Math.max(maxTokens, maxCompletionTokens);
    }

    private boolean isThoughtSignatureEnabled(RelayInfo info, GeminiModelConfig geminiSettings) {
        return (info.getChannelType() == ChannelConstants.CHANNEL_TYPE_GEMINI
                || info.getChannelType() == ChannelConstants.CHANNEL_TYPE_VERTEX_AI)
                && geminiSettings.isFunctionCallThoughtSignatureEnabled();
    }

    @SuppressWarnings("unchecked")
    private boolean applyExtraBody(Object extraBodyObject, RelayInfo info, GeminiDTO.GeminiChatRequest geminiRequest) {
        if (!(extraBodyObject instanceof Map<?, ?> extraBodyRaw) || extraBodyRaw.isEmpty()) {
            return false;
        }
        Object googleRaw = extraBodyRaw.get("google");
        if (!(googleRaw instanceof Map<?, ?> googleBody)) {
            return false;
        }

        boolean adaptorWithExtraBody = !endsWith(info.getUpstreamModelName(), "-nothinking");

        if (googleBody.containsKey("thinkingConfig")) {
            throw new IllegalArgumentException("extra_body.google.thinkingConfig is not supported, use extra_body.google.thinking_config instead");
        }
        if (googleBody.containsKey("imageConfig")) {
            throw new IllegalArgumentException("extra_body.google.imageConfig is not supported, use extra_body.google.image_config instead");
        }

        Object thinkingConfigRaw = googleBody.get("thinking_config");
        if (thinkingConfigRaw instanceof Map<?, ?> thinkingConfig) {
            applyThinkingConfig(thinkingConfig, geminiRequest);
        }

        Object imageConfigRaw = googleBody.get("image_config");
        if (imageConfigRaw instanceof Map<?, ?> imageConfig) {
            applyImageConfig(imageConfig, geminiRequest);
        }

        return adaptorWithExtraBody;
    }

    private void applyThinkingConfig(Map<?, ?> thinkingConfig, GeminiDTO.GeminiChatRequest geminiRequest) {
        if (thinkingConfig.containsKey("thinkingBudget")) {
            throw new IllegalArgumentException("extra_body.google.thinking_config.thinkingBudget is not supported, use extra_body.google.thinking_config.thinking_budget instead");
        }

        GeminiDTO.GeminiThinkingConfig tempThinkingConfig = new GeminiDTO.GeminiThinkingConfig();
        boolean hasThinkingConfig = false;

        if (thinkingConfig.containsKey("thinking_budget")) {
            Object budgetValue = thinkingConfig.get("thinking_budget");
            if (!(budgetValue instanceof Number numberValue)) {
                throw new IllegalArgumentException("extra_body.google.thinking_config.thinking_budget must be an integer");
            }
            int budget = numberValue.intValue();
            tempThinkingConfig.setThinkingBudget(budget);
            tempThinkingConfig.setIncludeThoughts(budget > 0);
            hasThinkingConfig = true;
        }

        if (thinkingConfig.containsKey("include_thoughts")) {
            Object includeThoughts = thinkingConfig.get("include_thoughts");
            if (!(includeThoughts instanceof Boolean boolValue)) {
                throw new IllegalArgumentException("extra_body.google.thinking_config.include_thoughts must be a boolean");
            }
            tempThinkingConfig.setIncludeThoughts(boolValue);
            hasThinkingConfig = true;
        }

        if (thinkingConfig.containsKey("thinking_level")) {
            Object thinkingLevel = thinkingConfig.get("thinking_level");
            if (!(thinkingLevel instanceof String levelValue)) {
                throw new IllegalArgumentException("extra_body.google.thinking_config.thinking_level must be a string");
            }
            tempThinkingConfig.setThinkingLevel(levelValue);
            hasThinkingConfig = true;
        }

        if (!hasThinkingConfig) {
            return;
        }

        GeminiDTO.GeminiThinkingConfig targetConfig = geminiRequest.getGenerationConfig().getThinkingConfig();
        if (targetConfig == null) {
            geminiRequest.getGenerationConfig().setThinkingConfig(tempThinkingConfig);
            return;
        }
        if (tempThinkingConfig.getThinkingBudget() != null) {
            targetConfig.setThinkingBudget(tempThinkingConfig.getThinkingBudget());
        }
        if (tempThinkingConfig.getIncludeThoughts() != null) {
            targetConfig.setIncludeThoughts(tempThinkingConfig.getIncludeThoughts());
        }
        if (tempThinkingConfig.getThinkingLevel() != null && !tempThinkingConfig.getThinkingLevel().isBlank()) {
            targetConfig.setThinkingLevel(tempThinkingConfig.getThinkingLevel());
        }
    }

    private void applyImageConfig(Map<?, ?> imageConfig, GeminiDTO.GeminiChatRequest geminiRequest) {
        if (imageConfig.containsKey("aspectRatio")) {
            throw new IllegalArgumentException("extra_body.google.image_config.aspectRatio is not supported, use extra_body.google.image_config.aspect_ratio instead");
        }
        if (imageConfig.containsKey("imageSize")) {
            throw new IllegalArgumentException("extra_body.google.image_config.imageSize is not supported, use extra_body.google.image_config.image_size instead");
        }

        Map<String, Object> geminiImageConfig = new LinkedHashMap<>();
        if (imageConfig.containsKey("aspect_ratio")) {
            geminiImageConfig.put("aspectRatio", imageConfig.get("aspect_ratio"));
        }
        if (imageConfig.containsKey("image_size")) {
            geminiImageConfig.put("imageSize", imageConfig.get("image_size"));
        }
        if (!geminiImageConfig.isEmpty()) {
            geminiRequest.getGenerationConfig().setImageConfig(geminiImageConfig);
        }
    }

    private void applyThinkingAdaptor(GeminiDTO.GeminiChatRequest geminiRequest, RelayInfo info, OpenAIRequestDTO request) {
        GeminiModelConfig geminiConfig = GeminiModelConfig.getInstance();
        if (!geminiConfig.isThinkingAdapterEnabled()) {
            trimGeminiThinkingSuffix(info);
            return;
        }

        String modelName = info.getUpstreamModelName();
        if (modelName == null || modelName.isBlank()) {
            return;
        }

        GeminiDTO.GeminiThinkingConfig thinkingConfig = null;
        if (modelName.contains("-thinking-")) {
            String[] parts = modelName.split("-thinking-", 2);
            if (parts.length == 2 && !parts[1].isBlank()) {
                try {
                    int budget = Integer.parseInt(parts[1]);
                    thinkingConfig = new GeminiDTO.GeminiThinkingConfig();
                    thinkingConfig.setThinkingBudget(clampThinkingBudget(modelName, budget));
                    thinkingConfig.setIncludeThoughts(true);
                    info.setUpstreamModelName(parts[0]);
                } catch (NumberFormatException ignored) {
                    trimGeminiThinkingSuffix(info);
                }
            }
        } else if (modelName.endsWith("-thinking")) {
            thinkingConfig = new GeminiDTO.GeminiThinkingConfig();
            thinkingConfig.setIncludeThoughts(true);
            if (geminiRequest.getGenerationConfig().getMaxOutputTokens() != null
                    && geminiRequest.getGenerationConfig().getMaxOutputTokens() > 0) {
                int budget = (int) (geminiConfig.getThinkingAdapterBudgetTokensPercentage()
                        * geminiRequest.getGenerationConfig().getMaxOutputTokens());
                thinkingConfig.setThinkingBudget(clampThinkingBudget(modelName, budget));
            } else if (request.getReasoningEffort() != null && !request.getReasoningEffort().isBlank()) {
                thinkingConfig.setThinkingBudget(clampThinkingBudgetByEffort(modelName, request.getReasoningEffort()));
            }
            info.setUpstreamModelName(modelName.substring(0, modelName.length() - "-thinking".length()));
        } else if (modelName.endsWith("-nothinking")) {
            if (!isNew25ProModel(modelName)) {
                thinkingConfig = new GeminiDTO.GeminiThinkingConfig();
                thinkingConfig.setThinkingBudget(0);
            }
            info.setUpstreamModelName(modelName.substring(0, modelName.length() - "-nothinking".length()));
        } else {
            ReasoningSuffixConfig.EffortTrim effortTrim = ReasoningSuffixConfig.trimEffortSuffix(modelName);
            if (effortTrim.found && effortTrim.effort != null && !effortTrim.effort.isBlank()) {
                thinkingConfig = new GeminiDTO.GeminiThinkingConfig();
                thinkingConfig.setIncludeThoughts(true);
                thinkingConfig.setThinkingLevel(effortTrim.effort);
                info.setReasoningEffort(effortTrim.effort);
                info.setUpstreamModelName(effortTrim.baseModel);
            }
        }

        if (thinkingConfig != null) {
            geminiRequest.getGenerationConfig().setThinkingConfig(thinkingConfig);
        }
        if (info.getUpstreamModelName() == null || info.getUpstreamModelName().isBlank()) {
            trimGeminiThinkingSuffix(info);
        }
    }

    private void trimGeminiThinkingSuffix(RelayInfo info) {
        String modelName = info.getUpstreamModelName();
        if (modelName == null) {
            return;
        }
        if (modelName.contains("-thinking-")) {
            info.setUpstreamModelName(modelName.substring(0, modelName.indexOf("-thinking-")));
        } else if (modelName.endsWith("-thinking")) {
            info.setUpstreamModelName(modelName.substring(0, modelName.length() - "-thinking".length()));
        } else if (modelName.endsWith("-nothinking")) {
            info.setUpstreamModelName(modelName.substring(0, modelName.length() - "-nothinking".length()));
        }
    }

    private List<GeminiDTO.GeminiChatSafetySettings> buildSafetySettings(GeminiModelConfig geminiSettings) {
        List<GeminiDTO.GeminiChatSafetySettings> safetySettings = new ArrayList<>();
        for (String category : SAFETY_SETTING_LIST) {
            GeminiDTO.GeminiChatSafetySettings setting = new GeminiDTO.GeminiChatSafetySettings();
            setting.setCategory(category);
            setting.setThreshold(geminiSettings.getGeminiSafetySetting(category));
            safetySettings.add(setting);
        }
        return safetySettings;
    }

    private Object convertTools(List<OpenAIRequestDTO.ToolCallRequest> tools) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }

        List<Map<String, Object>> geminiTools = new ArrayList<>();
        List<OpenAIRequestDTO.FunctionRequest> functionDeclarations = new ArrayList<>();
        boolean googleSearch = false;
        boolean codeExecution = false;
        boolean urlContext = false;

        for (OpenAIRequestDTO.ToolCallRequest tool : tools) {
            if (tool == null || tool.getFunction() == null) {
                continue;
            }
            OpenAIRequestDTO.FunctionRequest function = tool.getFunction();
            String name = function.getName();
            if ("googleSearch".equals(name)) {
                googleSearch = true;
                continue;
            }
            if ("codeExecution".equals(name)) {
                codeExecution = true;
                continue;
            }
            if ("urlContext".equals(name)) {
                urlContext = true;
                continue;
            }

            Object parameters = function.getParameters();
            if (parameters instanceof Map<?, ?> paramsMap) {
                Object properties = paramsMap.get("properties");
                if (properties instanceof Map<?, ?> props && props.isEmpty()) {
                    function.setParameters(null);
                }
            }
            function.setParameters(cleanFunctionParameters(function.getParameters(), 0));
            functionDeclarations.add(function);
        }

        if (codeExecution) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("codeExecution", new LinkedHashMap<>());
            geminiTools.add(tool);
        }
        if (googleSearch) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("googleSearch", new LinkedHashMap<>());
            geminiTools.add(tool);
        }
        if (urlContext) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("urlContext", new LinkedHashMap<>());
            geminiTools.add(tool);
        }
        if (!functionDeclarations.isEmpty()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("functionDeclarations", functionDeclarations);
            geminiTools.add(tool);
        }
        return geminiTools.isEmpty() ? null : geminiTools;
    }

    private GeminiDTO.ToolConfig convertToolChoiceToGeminiConfig(Object toolChoice) {
        if (toolChoice == null) {
            return null;
        }

        GeminiDTO.ToolConfig toolConfig = new GeminiDTO.ToolConfig();
        GeminiDTO.FunctionCallingConfig functionCallingConfig = new GeminiDTO.FunctionCallingConfig();
        toolConfig.setFunctionCallingConfig(functionCallingConfig);

        if (toolChoice instanceof String toolChoiceStr) {
            switch (toolChoiceStr) {
                case "none" -> functionCallingConfig.setMode("NONE");
                case "required" -> functionCallingConfig.setMode("ANY");
                case "auto" -> functionCallingConfig.setMode("AUTO");
                default -> functionCallingConfig.setMode("AUTO");
            }
            return toolConfig;
        }

        if (toolChoice instanceof Map<?, ?> toolChoiceMap) {
            Object type = toolChoiceMap.get("type");
            if (!"function".equals(type)) {
                return null;
            }
            functionCallingConfig.setMode("ANY");
            Object functionRaw = toolChoiceMap.get("function");
            if (functionRaw instanceof Map<?, ?> functionMap) {
                Object name = functionMap.get("name");
                if (name instanceof String functionName && !functionName.isBlank()) {
                    functionCallingConfig.setAllowedFunctionNames(List.of(functionName));
                }
            }
            return toolConfig;
        }

        return null;
    }

    private void applyResponseFormat(OpenAIRequestDTO.ResponseFormat responseFormat, GeminiDTO.GeminiChatRequest geminiRequest) {
        if (responseFormat == null || responseFormat.getType() == null) {
            return;
        }
        if (!"json_schema".equals(responseFormat.getType()) && !"json_object".equals(responseFormat.getType())) {
            return;
        }

        geminiRequest.getGenerationConfig().setResponseMimeType("application/json");
        if (responseFormat.getJsonSchema() instanceof Map<?, ?> jsonSchemaMap) {
            Object schema = jsonSchemaMap.get("schema");
            if (schema != null) {
                geminiRequest.getGenerationConfig().setResponseSchema(removeAdditionalPropertiesWithDepth(schema, 0));
            }
        }
    }

    private void populateContents(List<OpenAIRequestDTO.Message> messages,
                                  GeminiDTO.GeminiChatRequest geminiRequest,
                                  boolean attachThoughtSignature) throws Exception {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        Map<String, String> toolCallIdToName = new LinkedHashMap<>();
        List<String> systemContents = new ArrayList<>();

        for (OpenAIRequestDTO.Message message : messages) {
            if (message == null || message.getRole() == null) {
                continue;
            }

            String role = message.getRole();
            if ("system".equals(role) || "developer".equals(role)) {
                String systemText = extractStringContent(message);
                if (!systemText.isBlank()) {
                    systemContents.add(systemText);
                }
                continue;
            }

            if ("tool".equals(role) || "function".equals(role)) {
                appendFunctionResponse(geminiRequest, message, toolCallIdToName);
                continue;
            }

            GeminiDTO.GeminiChatContent content = new GeminiDTO.GeminiChatContent();
            content.setRole("assistant".equals(role) ? "model" : role);
            List<GeminiDTO.GeminiPart> parts = new ArrayList<>();
            boolean shouldAttachThoughtSignature = attachThoughtSignature && "model".equals(content.getRole());
            boolean signatureAttached = false;

            for (OpenAIRequestDTO.ToolCallRequest toolCall : parseToolCalls(message)) {
                GeminiDTO.GeminiPart toolCallPart = buildFunctionCallPart(toolCall);
                if (shouldAttachThoughtSignature && !signatureAttached && hasFunctionCallContent(toolCallPart.getFunctionCall())) {
                    toolCallPart.setThoughtSignature(quoteThoughtSignature());
                    signatureAttached = true;
                }
                parts.add(toolCallPart);
                if (toolCall != null && toolCall.getId() != null && toolCall.getFunction() != null) {
                    toolCallIdToName.put(toolCall.getId(), toolCall.getFunction().getName());
                }
            }

            for (Map<String, Object> part : parseContent(message)) {
                appendContentPart(parts, part, shouldAttachThoughtSignature);
            }

            if (shouldAttachThoughtSignature && !signatureAttached) {
                attachThoughtSignatureToFirstTextPart(parts);
            }

            if (!parts.isEmpty()) {
                content.setParts(parts);
                geminiRequest.getContents().add(content);
            }
        }

        if (!systemContents.isEmpty()) {
            GeminiDTO.GeminiPart systemPart = new GeminiDTO.GeminiPart();
            systemPart.setText(String.join("\n", systemContents));
            GeminiDTO.GeminiChatContent systemInstruction = new GeminiDTO.GeminiChatContent();
            systemInstruction.setParts(List.of(systemPart));
            geminiRequest.setSystemInstructions(systemInstruction);
        }
    }

    private void appendFunctionResponse(GeminiDTO.GeminiChatRequest geminiRequest,
                                        OpenAIRequestDTO.Message message,
                                        Map<String, String> toolCallIdToName) {
        List<GeminiDTO.GeminiChatContent> contents = geminiRequest.getContents();
        if (contents.isEmpty() || "model".equals(contents.get(contents.size() - 1).getRole())) {
            GeminiDTO.GeminiChatContent userContent = new GeminiDTO.GeminiChatContent();
            userContent.setRole("user");
            userContent.setParts(new ArrayList<>());
            contents.add(userContent);
        }

        String name = message.getName();
        if ((name == null || name.isBlank()) && message.getToolCallId() != null) {
            name = toolCallIdToName.get(message.getToolCallId());
        }

        GeminiDTO.GeminiFunctionResponse functionResponse = new GeminiDTO.GeminiFunctionResponse();
        functionResponse.setName(name);
        functionResponse.setResponse(parseFunctionResponseContent(extractStringContent(message)));

        GeminiDTO.GeminiPart responsePart = new GeminiDTO.GeminiPart();
        responsePart.setFunctionResponse(functionResponse);
        contents.get(contents.size() - 1).getParts().add(responsePart);
    }

    private GeminiDTO.GeminiPart buildFunctionCallPart(OpenAIRequestDTO.ToolCallRequest toolCall) throws Exception {
        GeminiDTO.FunctionCall functionCall = new GeminiDTO.FunctionCall();
        if (toolCall != null && toolCall.getFunction() != null) {
            functionCall.setName(toolCall.getFunction().getName());
            String arguments = toolCall.getFunction().getArguments();
            if (arguments != null && !arguments.isBlank()) {
                functionCall.setArgs(parseJsonString(arguments));
            } else {
                functionCall.setArgs(new LinkedHashMap<>());
            }
        }

        GeminiDTO.GeminiPart part = new GeminiDTO.GeminiPart();
        part.setFunctionCall(functionCall);
        return part;
    }

    private void appendContentPart(List<GeminiDTO.GeminiPart> parts,
                                   Map<String, Object> part,
                                   boolean shouldAttachThoughtSignature) throws Exception {
        String type = stringValue(part.get("type"));
        if ("text".equals(type)) {
            appendTextParts(parts, stringValue(part.get("text")), shouldAttachThoughtSignature);
            return;
        }
        if ("image_url".equals(type)) {
            Object imageUrlRaw = part.get("image_url");
            String imageUrl = extractImageUrl(imageUrlRaw);
            if (imageUrl == null || imageUrl.isBlank()) {
                return;
            }
            ImageService.FileData fileData = loadInlineFileData(imageUrl);
            appendInlineDataPart(parts, fileData.mimeType(), fileData.base64(), shouldAttachThoughtSignature);
        }
    }

    private void appendTextParts(List<GeminiDTO.GeminiPart> parts,
                                 String text,
                                 boolean shouldAttachThoughtSignature) throws Exception {
        if (text == null || text.isEmpty()) {
            return;
        }

        int cursor = 0;
        boolean hasMarkdownImage = false;
        while (cursor < text.length()) {
            int startIdx = text.indexOf("![", cursor);
            if (startIdx < 0) {
                break;
            }
            int dataStartIdx = text.indexOf("](data:", startIdx);
            if (dataStartIdx < 0) {
                break;
            }
            int closeIdx = text.indexOf(")", dataStartIdx + 2);
            if (closeIdx < 0) {
                break;
            }

            hasMarkdownImage = true;
            if (startIdx > cursor) {
                GeminiDTO.GeminiPart textPart = new GeminiDTO.GeminiPart();
                textPart.setText(text.substring(cursor, startIdx));
                parts.add(textPart);
            }

            String dataUrl = text.substring(dataStartIdx + 2, closeIdx);
            ImageService.FileData fileData = ImageService.decodeBase64FileData(dataUrl);
            appendInlineDataPart(parts, fileData.mimeType(), fileData.base64(), shouldAttachThoughtSignature);
            cursor = closeIdx + 1;
        }

        if (!hasMarkdownImage) {
            GeminiDTO.GeminiPart textPart = new GeminiDTO.GeminiPart();
            textPart.setText(text);
            parts.add(textPart);
            return;
        }

        if (cursor < text.length()) {
            GeminiDTO.GeminiPart textPart = new GeminiDTO.GeminiPart();
            textPart.setText(text.substring(cursor));
            parts.add(textPart);
        }
    }

    private void appendInlineDataPart(List<GeminiDTO.GeminiPart> parts,
                                      String mimeType,
                                      String base64Data,
                                      boolean shouldAttachThoughtSignature) {
        validateMimeType(mimeType);
        GeminiDTO.GeminiInlineData inlineData = new GeminiDTO.GeminiInlineData();
        inlineData.setMimeType(mimeType);
        inlineData.setData(base64Data);

        GeminiDTO.GeminiPart imagePart = new GeminiDTO.GeminiPart();
        imagePart.setInlineData(inlineData);
        if (shouldAttachThoughtSignature) {
            imagePart.setThoughtSignature(quoteThoughtSignature());
        }
        parts.add(imagePart);
    }

    private void attachThoughtSignatureToFirstTextPart(List<GeminiDTO.GeminiPart> parts) {
        for (GeminiDTO.GeminiPart part : parts) {
            if (part.getText() != null && !part.getText().isEmpty()) {
                part.setThoughtSignature(quoteThoughtSignature());
                return;
            }
        }
    }

    private Object quoteThoughtSignature() {
        return "\"" + THOUGHT_SIGNATURE_BYPASS_VALUE + "\"";
    }

    private ImageService.FileData loadInlineFileData(String source) throws Exception {
        if (source.startsWith("data:")) {
            return ImageService.decodeBase64FileData(source);
        }
        DownloadService.validateURL(source);
        return ImageService.getImageFromUrl(source);
    }

    private void validateMimeType(String mimeType) {
        String normalizedMimeType = mimeType != null ? mimeType.toLowerCase() : "";
        if (!SUPPORTED_MIME_TYPES.contains(normalizedMimeType)) {
            throw new IllegalArgumentException("mime type is not supported by Gemini: '" + mimeType
                    + "', supported types are: " + new LinkedHashSet<>(SUPPORTED_MIME_TYPES));
        }
    }

    private List<String> parseStopSequences(Object stop) {
        if (stop == null) {
            return List.of();
        }
        if (stop instanceof String stopString) {
            return stopString.isBlank() ? List.of() : List.of(stopString);
        }
        if (stop instanceof List<?> stopList) {
            List<String> result = new ArrayList<>();
            for (Object item : stopList) {
                if (item instanceof String stopItem && !stopItem.isBlank()) {
                    result.add(stopItem);
                }
            }
            return result;
        }
        return List.of();
    }

    private Object cleanFunctionParameters(Object params, int depth) {
        if (params == null) {
            return null;
        }
        if (depth >= GEMINI_FUNCTION_SCHEMA_MAX_DEPTH) {
            return cleanFunctionParametersShallow(params);
        }
        if (params instanceof Map<?, ?> paramsMap) {
            Map<String, Object> cleanedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
                String key = stringValue(entry.getKey());
                if (!GEMINI_OPENAPI_SCHEMA_ALLOWED_FIELDS.contains(key)) {
                    continue;
                }
                cleanedMap.put(key, entry.getValue());
            }
            normalizeGeminiSchemaTypeAndNullable(cleanedMap);

            Object properties = cleanedMap.get("properties");
            if (properties instanceof Map<?, ?> propertiesMap) {
                Map<String, Object> cleanedProps = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
                    cleanedProps.put(stringValue(entry.getKey()), cleanFunctionParameters(entry.getValue(), depth + 1));
                }
                cleanedMap.put("properties", cleanedProps);
            }

            Object items = cleanedMap.get("items");
            if (items instanceof Map<?, ?> || items instanceof List<?>) {
                if (items instanceof List<?> itemsArray && !itemsArray.isEmpty()) {
                    cleanedMap.put("items", cleanFunctionParameters(itemsArray.get(0), depth + 1));
                } else {
                    cleanedMap.put("items", cleanFunctionParameters(items, depth + 1));
                }
            }

            Object anyOf = cleanedMap.get("anyOf");
            if (anyOf instanceof List<?> nestedList) {
                List<Object> cleanedNested = new ArrayList<>();
                for (Object nestedItem : nestedList) {
                    cleanedNested.add(cleanFunctionParameters(nestedItem, depth + 1));
                }
                cleanedMap.put("anyOf", cleanedNested);
            }
            return cleanedMap;
        }

        if (params instanceof List<?> paramsList) {
            List<Object> cleanedList = new ArrayList<>();
            for (Object item : paramsList) {
                cleanedList.add(cleanFunctionParameters(item, depth + 1));
            }
            return cleanedList;
        }

        return params;
    }

    private Object cleanFunctionParametersShallow(Object params) {
        if (params instanceof Map<?, ?> paramsMap) {
            Map<String, Object> cleanedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : paramsMap.entrySet()) {
                String key = stringValue(entry.getKey());
                if (!GEMINI_OPENAPI_SCHEMA_ALLOWED_FIELDS.contains(key)) {
                    continue;
                }
                cleanedMap.put(key, entry.getValue());
            }
            normalizeGeminiSchemaTypeAndNullable(cleanedMap);
            cleanedMap.remove("properties");
            cleanedMap.remove("items");
            cleanedMap.remove("anyOf");
            return cleanedMap;
        }
        if (params instanceof List<?>) {
            return new ArrayList<>();
        }
        return params;
    }

    private void normalizeGeminiSchemaTypeAndNullable(Map<String, Object> schema) {
        Object rawType = schema.get("type");
        if (rawType == null) {
            return;
        }
        if (rawType instanceof String typeString) {
            TypeNormalization normalization = normalizeType(typeString);
            if (normalization.nullableOnly()) {
                schema.put("nullable", true);
                schema.remove("type");
                return;
            }
            schema.put("type", normalization.type());
            return;
        }
        if (rawType instanceof List<?> typeList) {
            boolean nullable = false;
            String chosenType = null;
            for (Object item : typeList) {
                if (!(item instanceof String itemString)) {
                    continue;
                }
                TypeNormalization normalization = normalizeType(itemString);
                if (normalization.nullableOnly()) {
                    nullable = true;
                    continue;
                }
                if (chosenType == null) {
                    chosenType = normalization.type();
                }
            }
            if (nullable) {
                schema.put("nullable", true);
            }
            if (chosenType != null) {
                schema.put("type", chosenType);
            } else {
                schema.remove("type");
            }
        }
    }

    private TypeNormalization normalizeType(String type) {
        return switch (type == null ? "" : type.trim().toLowerCase()) {
            case "object" -> new TypeNormalization("OBJECT", false);
            case "array" -> new TypeNormalization("ARRAY", false);
            case "string" -> new TypeNormalization("STRING", false);
            case "integer" -> new TypeNormalization("INTEGER", false);
            case "number" -> new TypeNormalization("NUMBER", false);
            case "boolean" -> new TypeNormalization("BOOLEAN", false);
            case "null" -> new TypeNormalization("", true);
            default -> new TypeNormalization(type, false);
        };
    }

    private Object removeAdditionalPropertiesWithDepth(Object schema, int depth) {
        if (depth >= RESPONSE_SCHEMA_MAX_DEPTH || !(schema instanceof Map<?, ?> schemaMap) || schemaMap.isEmpty()) {
            return schema;
        }

        Map<String, Object> mutableSchema = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : schemaMap.entrySet()) {
            mutableSchema.put(stringValue(entry.getKey()), entry.getValue());
        }

        mutableSchema.remove("title");
        mutableSchema.remove("$schema");

        Object type = mutableSchema.get("type");
        if (!"object".equals(type) && !"array".equals(type)) {
            return mutableSchema;
        }

        if ("object".equals(type)) {
            mutableSchema.remove("additionalProperties");
            Object properties = mutableSchema.get("properties");
            if (properties instanceof Map<?, ?> propertiesMap) {
                Map<String, Object> nestedProperties = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : propertiesMap.entrySet()) {
                    nestedProperties.put(stringValue(entry.getKey()),
                            removeAdditionalPropertiesWithDepth(entry.getValue(), depth + 1));
                }
                mutableSchema.put("properties", nestedProperties);
            }
            for (String field : List.of("allOf", "anyOf", "oneOf")) {
                Object nested = mutableSchema.get(field);
                if (nested instanceof List<?> nestedList) {
                    List<Object> cleanedNested = new ArrayList<>();
                    for (Object item : nestedList) {
                        cleanedNested.add(removeAdditionalPropertiesWithDepth(item, depth + 1));
                    }
                    mutableSchema.put(field, cleanedNested);
                }
            }
        } else {
            Object items = mutableSchema.get("items");
            if (items != null) {
                mutableSchema.put("items", removeAdditionalPropertiesWithDepth(items, depth + 1));
            }
        }

        return mutableSchema;
    }

    private List<Map<String, Object>> parseContent(OpenAIRequestDTO.Message message) {
        if (message == null || message.getContent() == null) {
            return List.of();
        }
        Object content = message.getContent();
        if (content instanceof List<?> contentList) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : contentList) {
                if (item instanceof Map<?, ?> itemMap) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
                        normalized.put(stringValue(entry.getKey()), entry.getValue());
                    }
                    result.add(normalized);
                }
            }
            return result;
        }
        if (content instanceof String contentString && !contentString.isEmpty()) {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", contentString);
            return List.of(textPart);
        }
        return List.of();
    }

    private List<OpenAIRequestDTO.ToolCallRequest> parseToolCalls(OpenAIRequestDTO.Message message) {
        if (message == null || message.getToolCalls() == null) {
            return List.of();
        }
        Object toolCalls = message.getToolCalls();
        if (!(toolCalls instanceof List<?> toolCallList)) {
            return List.of();
        }
        List<OpenAIRequestDTO.ToolCallRequest> result = new ArrayList<>();
        for (Object item : toolCallList) {
            result.add(item instanceof OpenAIRequestDTO.ToolCallRequest toolCall
                    ? toolCall
                    : Convert.toJavaBean(item, OpenAIRequestDTO.ToolCallRequest.class));
        }
        return result;
    }

    private String extractStringContent(OpenAIRequestDTO.Message message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        if (message.getContent() instanceof String contentString) {
            return contentString;
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> part : parseContent(message)) {
            if ("text".equals(part.get("type")) && part.get("text") != null) {
                sb.append(part.get("text"));
            }
        }
        return sb.toString();
    }

    private Object parseFunctionResponseContent(String content) {
        if (content == null || content.isBlank()) {
            return Map.of("content", "");
        }
        try {
            Object parsed = Convert.toJavaBean(content, Object.class);
            if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
                return parsed;
            }
        } catch (Exception e) {
            log.debug("解析 Gemini content JSON 失败，按原始内容返回", e);
        }
        return Map.of("content", content);
    }

    private Object parseJsonString(String json) throws Exception {
        return Convert.toJavaBean(json, Object.class);
    }

    private boolean hasFunctionCallContent(GeminiDTO.FunctionCall call) {
        if (call == null) {
            return false;
        }
        if (call.getName() != null && !call.getName().isBlank()) {
            return true;
        }
        Object args = call.getArgs();
        if (args == null) {
            return false;
        }
        if (args instanceof String argsString) {
            return !argsString.isBlank();
        }
        if (args instanceof Map<?, ?> argsMap) {
            return !argsMap.isEmpty();
        }
        if (args instanceof List<?> argsList) {
            return !argsList.isEmpty();
        }
        return true;
    }

    private String extractImageUrl(Object imageUrlRaw) {
        if (imageUrlRaw instanceof String imageUrl) {
            return imageUrl;
        }
        if (imageUrlRaw instanceof Map<?, ?> imageUrlMap) {
            Object url = imageUrlMap.get("url");
            if (url instanceof String imageUrl) {
                return imageUrl;
            }
        }
        return null;
    }

    private boolean endsWith(String source, String suffix) {
        return source != null && suffix != null && source.endsWith(suffix);
    }

    private boolean isNew25ProModel(String modelName) {
        return modelName != null
                && modelName.startsWith("gemini-2.5-pro")
                && !modelName.startsWith("gemini-2.5-pro-preview-05-06")
                && !modelName.startsWith("gemini-2.5-pro-preview-03-25");
    }

    private boolean is25FlashLiteModel(String modelName) {
        return modelName != null && modelName.startsWith("gemini-2.5-flash-lite");
    }

    private int clampThinkingBudget(String modelName, int budget) {
        if (is25FlashLiteModel(modelName)) {
            return Math.max(512, Math.min(24576, budget));
        }
        if (isNew25ProModel(modelName)) {
            return Math.max(128, Math.min(32768, budget));
        }
        return Math.max(0, Math.min(24576, budget));
    }

    private int clampThinkingBudgetByEffort(String modelName, String effort) {
        int maxBudget = is25FlashLiteModel(modelName) ? 24576 : (isNew25ProModel(modelName) ? 32768 : 24576);
        switch (effort) {
            case "high" -> maxBudget = maxBudget * 80 / 100;
            case "medium" -> maxBudget = maxBudget * 50 / 100;
            case "low" -> maxBudget = maxBudget * 20 / 100;
            case "minimal" -> maxBudget = maxBudget * 5 / 100;
            default -> {
            }
        }
        return clampThinkingBudget(modelName, maxBudget);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record TypeNormalization(String type, boolean nullableOnly) {
    }
}
