package yaoshu.token.relay.channel.task.ali;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TaskAliConstant {
    private TaskAliConstant() {}

    public static final String CHANNEL_NAME = "ali";
    public static final List<String> MODEL_LIST = List.of(
            "wan2.5-i2v-preview",
            "wan2.2-i2v-flash",
            "wan2.2-i2v-plus",
            "wanx2.1-i2v-plus",
            "wanx2.1-i2v-turbo"
    );

    public static final Set<String> SIZE_480P = Set.of("832*480", "480*832", "624*624");
    public static final Set<String> SIZE_720P = Set.of("1280*720", "720*1280", "960*960", "1088*832", "832*1088");
    public static final Set<String> SIZE_1080P = Set.of("1920*1080", "1080*1920", "1440*1440", "1632*1248", "1248*1632");

    public static final Map<String, Map<String, Double>> ALI_RATIOS = Map.of(
            "wan2.6-i2v", Map.of("720P", 1.0, "1080P", 1 / 0.6),
            "wan2.5-t2v-preview", Map.of("480P", 1.0, "720P", 2.0, "1080P", 1 / 0.3),
            "wan2.2-t2v-plus", Map.of("480P", 1.0, "1080P", 0.7 / 0.14),
            "wan2.5-i2v-preview", Map.of("480P", 1.0, "720P", 2.0, "1080P", 1 / 0.3),
            "wan2.2-i2v-plus", Map.of("480P", 1.0, "1080P", 0.7 / 0.14),
            "wan2.2-kf2v-flash", Map.of("480P", 1.0, "720P", 2.0, "1080P", 4.8),
            "wan2.2-i2v-flash", Map.of("480P", 1.0, "720P", 2.0),
            "wan2.2-s2v", Map.of("480P", 1.0, "720P", 0.9 / 0.5)
    );
}
