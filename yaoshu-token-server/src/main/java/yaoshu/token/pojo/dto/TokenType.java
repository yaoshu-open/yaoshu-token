package yaoshu.token.pojo.dto;

import lombok.Getter;

/**
 * Token 类型枚举  */
@Getter
public enum TokenType {

    TEXT_NUMBER("text_number"),
    TOKENIZER("tokenizer"),
    IMAGE("image");

    private final String value;

    TokenType(String value) {
        this.value = value;
    }
}
