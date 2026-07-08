package yaoshu.token.pojo.dto;

import lombok.Getter;

/**
 * 文件类型枚举  */
@Getter
public enum FileType {

    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video"),
    FILE("file");

    private final String value;

    FileType(String value) {
        this.value = value;
    }
}
