package yaoshu.token.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件元数据  */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMeta {

    private String fileType;
    private String source;
    private String detail;
}
