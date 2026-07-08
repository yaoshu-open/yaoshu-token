package yaoshu.token.relay.channel.task.kling;

import java.util.List;

public final class TaskKlingChannelConstant {
    private TaskKlingChannelConstant() {
    }

    public static final String CHANNEL_NAME = "kling";
    public static final List<String> MODEL_LIST = List.of("kling-v1", "kling-v1-6", "kling-v2-master");
}
