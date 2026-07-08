package yaoshu.token.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地文件数据  */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileData {

    private String mimeType;
    private String base64Data;
    private String url;
    private long size;
}
