package yaoshu.token.config;

import lombok.Data;

/**
 * 聊天设置 POJO  */
@Data
public class ChatSettingConfig {

    /** 是否启用聊天模式 */
    private boolean enabled = true;
    /** 默认系统 Prompt */
    private String defaultSystemPrompt;
    /** 最大上下文长度 */
    private int maxContextLength = 4000;
    /** 是否启用流式输出 */
    private boolean streamingEnabled = true;
}
