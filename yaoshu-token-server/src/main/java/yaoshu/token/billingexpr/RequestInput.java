package yaoshu.token.billingexpr;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 计费表达式请求输入。  * <p>
 * 提供 header() / param() 函数的运行时数据源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestInput {
    private Map<String, String> headers = new HashMap<>();
    private byte[] body;
}
