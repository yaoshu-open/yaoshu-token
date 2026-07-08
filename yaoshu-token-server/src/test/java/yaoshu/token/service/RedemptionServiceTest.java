package yaoshu.token.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yaoshu.token.mapper.RedemptionMapper;
import yaoshu.token.pojo.entity.Redemption;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedemptionService 白盒测试 — 聚焦兑换码 key 生成逻辑。
 * <p>
 * Bug 回归：UUID 必须去横线生成 32 字符，匹配 DB 列 key CHAR(32)，
 * 否则 MySQL 严格模式（STRICT_TRANS_TABLES）报 "Data too long for column 'key'"。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedemptionService — 兑换码 key 生成长度校验（CHAR(32) 回归）")
class RedemptionServiceTest {

    @Mock
    private RedemptionMapper redemptionMapper;

    @InjectMocks
    private RedemptionService redemptionService;

    @Test
    @DisplayName("add 生成的 key 为 32 字符纯十六进制（无横线），匹配 DB CHAR(32)")
    void addGeneratesKeyMatchingChar32() {
        Redemption input = new Redemption();
        input.setName("test");
        input.setCount(3);
        input.setQuota(500000);
        input.setExpiredTime(0L);

        List<String> keys = redemptionService.add(1, input);

        // 返回 key 数量 = count
        assertEquals(3, keys.size());

        // 捕获 insert 的 Redemption 参数，验证 key 字段
        ArgumentCaptor<Redemption> captor = ArgumentCaptor.forClass(Redemption.class);
        verify(redemptionMapper, times(3)).insert(captor.capture());

        for (Redemption captured : captor.getAllValues()) {
            String key = captured.getKey();
            assertEquals(32, key.length(),
                    "key 必须为 32 字符以匹配 DB CHAR(32)，实际: " + key.length() + " value=" + key);
            assertFalse(key.contains("-"), "key 不得包含横线: " + key);
            assertTrue(key.matches("[0-9a-f]{32}"), "key 必须为纯十六进制: " + key);
        }

        // 返回的 keys 与 insert 的 key 一致
        for (String k : keys) {
            assertEquals(32, k.length());
            assertFalse(k.contains("-"));
        }
    }
}
