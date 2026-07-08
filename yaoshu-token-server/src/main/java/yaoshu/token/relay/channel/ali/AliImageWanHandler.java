package yaoshu.token.relay.channel.ali;

import yaoshu.token.pojo.dto.OpenAIImageDTO;
import yaoshu.token.relay.channel.ali.AliDTOPlaceholder.*;
import yaoshu.token.relay.common.RelayInfo;

/**
 * 阿里通义万相图片编辑中转处理器  * <p>
 * 处理 Wan 系列模型的旧版/新版判断，以及表单图片编辑请求转换。
 */
public class AliImageWanHandler {

    /**
     * 判断是否为旧版 Wan 模型（wan 但不含 wan2.6/wan2.7）      */
    public static boolean isOldWanModel(String modelName) {
        if (modelName == null || !modelName.contains("wan")) {
            return false;
        }
        return !modelName.contains("wan2.6") && !modelName.contains("wan2.7");
    }

    /**
     * 判断是否为 Wan 模型      */
    public static boolean isWanModel(String modelName) {
        return modelName != null && modelName.contains("wan");
    }

    /**
     * OpenAI 图片编辑请求 → Wan 旧版图片编辑请求      * <p>
     * Wan 旧版编辑接口使用 WanImageInput 格式（prompt + images base64 数组）。
     */
    public static AliImageRequest oaiFormEdit2WanxImageEdit(RelayInfo info, OpenAIImageDTO request, java.util.List<String> imageBase64s) {
        AliImageRequest imageRequest = new AliImageRequest();
        imageRequest.setModel(request.getModel());
        imageRequest.setResponseFormat(request.getResponseFormat());

        WanImageInput wanInput = new WanImageInput();
        wanInput.setPrompt(request.getPrompt());
        wanInput.setImages(imageBase64s);
        imageRequest.setInput(wanInput);

        AliImageParameters parameters = new AliImageParameters();
        int n = (request.getN() != null) ? request.getN() : 1;
        parameters.setN(n);
        imageRequest.setParameters(parameters);

        if (info.getPriceData() != null) {
            info.getPriceData().addOtherRatio("n", n);
        }

        return imageRequest;
    }
}
