package yaoshu.token.relay.channel.task.doubao;

import java.util.List;
import java.util.Map;

public final class TaskDoubaoConstant {
    private TaskDoubaoConstant() {}

    public static final String CHANNEL_NAME = "doubao-video";

    public static final List<String> MODEL_LIST = List.of(
            "doubao-seedance-1-0-pro-250528",
            "doubao-seedance-1-0-lite-t2v",
            "doubao-seedance-1-0-lite-i2v",
            "doubao-seedance-1-5-pro-251215",
            "doubao-seedance-2-0-260128",
            "doubao-seedance-2-0-fast-260128"
    );

    private static final Map<String, Double> VIDEO_INPUT_RATIO_MAP = Map.of(
            "doubao-seedance-2-0-260128", 28.0 / 46.0,
            "doubao-seedance-2-0-fast-260128", 22.0 / 37.0
    );

    public static Double getVideoInputRatio(String modelName) {
        return VIDEO_INPUT_RATIO_MAP.get(modelName);
    }
}
