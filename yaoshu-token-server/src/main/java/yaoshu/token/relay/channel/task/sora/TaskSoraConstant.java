package yaoshu.token.relay.channel.task.sora;

import java.util.List;

public final class TaskSoraConstant {

    private TaskSoraConstant() {
    }

    public static final String CHANNEL_NAME = "sora";

    public static final List<String> MODEL_LIST = List.of(
            "sora-2",
            "sora-2-pro"
    );
}
