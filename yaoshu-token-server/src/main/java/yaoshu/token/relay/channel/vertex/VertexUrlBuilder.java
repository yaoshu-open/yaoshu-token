package yaoshu.token.relay.channel.vertex;

/**
 * Vertex AI URL 构建器  * <p>
 * 根据 baseURL/version/projectID/region/publisher/model 构建 Vertex AI 的各类 API URL。
 */
public final class VertexUrlBuilder {

    public static final String DEFAULT_API_VERSION = "v1";
    public static final String OPEN_SOURCE_API_VERSION = "v1beta1";
    public static final String PUBLISHER_GOOGLE = "google";
    public static final String PUBLISHER_ANTHROPIC = "anthropic";

    private VertexUrlBuilder() {
    }

    /** 规范化 baseURL：去除首尾空格和尾部斜杠 */
    private static String normalizeVertexBaseURL(String baseURL) {
        if (baseURL == null) return "";
        return baseURL.trim().replaceAll("/+$", "");
    }

    /** 规范化 region：空值 → global */
    private static String normalizeVertexRegion(String region) {
        if (region == null) return "global";
        region = region.trim();
        return region.isEmpty() ? "global" : region;
    }

    /** 追加 API 版本到 baseURL */
    private static String appendVertexAPIVersion(String baseURL, String version) {
        if (version == null) version = "";
        version = version.trim();
        version = version.replaceAll("^/+|/+$", "");
        if (version.isEmpty()) return baseURL;
        if (baseURL.endsWith("/" + version)) return baseURL;
        return baseURL + "/" + version;
    }

    /**
     * 构建 API 基础 URL      */
    public static String buildAPIBaseURL(String baseURL, String version, String projectID, String region) {
        String normalized = normalizeVertexBaseURL(baseURL);
        region = normalizeVertexRegion(region);

        if (!normalized.isEmpty()) {
            normalized = appendVertexAPIVersion(normalized, version);
            if (projectID != null && !projectID.trim().isEmpty()) {
                normalized = normalized + "/projects/" + projectID + "/locations/" + region;
            }
            return normalized;
        }

        if (projectID == null || projectID.trim().isEmpty()) {
            if ("global".equals(region)) {
                return "https://aiplatform.googleapis.com/" + version;
            }
            return "https://" + region + "-aiplatform.googleapis.com/" + version;
        }

        if ("global".equals(region)) {
            return "https://aiplatform.googleapis.com/" + version + "/projects/" + projectID + "/locations/global";
        }
        return "https://" + region + "-aiplatform.googleapis.com/" + version + "/projects/" + projectID + "/locations/" + region;
    }

    /**
     * 构建 Publisher Model URL      */
    public static String buildPublisherModelURL(String baseURL, String version, String projectID, String region,
                                                 String publisher, String modelName, String action) {
        return buildAPIBaseURL(baseURL, version, projectID, region)
                + "/publishers/" + publisher + "/models/" + modelName + ":" + action;
    }

    /**
     * 构建 Google Publisher Model URL
     */
    public static String buildGoogleModelURL(String baseURL, String version, String projectID, String region,
                                              String modelName, String action) {
        return buildPublisherModelURL(baseURL, version, projectID, region, PUBLISHER_GOOGLE, modelName, action);
    }

    /**
     * 构建 Anthropic Publisher Model URL
     */
    public static String buildAnthropicModelURL(String baseURL, String version, String projectID, String region,
                                                 String modelName, String action) {
        return buildPublisherModelURL(baseURL, version, projectID, region, PUBLISHER_ANTHROPIC, modelName, action);
    }

    /**
     * 构建开源模型 Chat Completions URL
     */
    public static String buildOpenSourceChatCompletionsURL(String baseURL, String projectID, String region) {
        return buildAPIBaseURL(baseURL, OPEN_SOURCE_API_VERSION, projectID, region)
                + "/endpoints/openapi/chat/completions";
    }
}
