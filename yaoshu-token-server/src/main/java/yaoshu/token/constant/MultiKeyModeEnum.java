package yaoshu.token.constant;

import lombok.Getter;

/**
 * 多 Key 模式枚举  */
@Getter
public enum MultiKeyModeEnum {

    /** 随机 */
    RANDOM("random"),
    /** 轮询 */
    POLLING("polling");

    private final String value;

    MultiKeyModeEnum(String value) {
        this.value = value;
    }
}
