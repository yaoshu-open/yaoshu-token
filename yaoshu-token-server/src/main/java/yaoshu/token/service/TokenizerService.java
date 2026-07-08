package yaoshu.token.service;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 分词服务  * <p>
 * 使用 jtokkit 实现 OpenAI 模型的 BPE 编码/解码与 Token 计数。
 * 编码器按模型缓存（ConcurrentHashMap），线程安全。
 */
@Slf4j
@Service
public class TokenizerService {

    /** 编码器注册中心 */
    private EncodingRegistry registry;

    /** 默认编码器 cl100k_base*/
    private Encoding defaultEncoding;

    /** 模型 → 编码器缓存 */
    private final ConcurrentHashMap<String, Encoding> encodingCache = new ConcurrentHashMap<>();

    /**
     * 初始化编码器      */
    @PostConstruct
    public void init() {
        log.info("initializing token encoders");
        registry = Encodings.newDefaultEncodingRegistry();
        defaultEncoding = registry.getEncoding(EncodingType.CL100K_BASE);
        log.info("token encoders initialized");
    }

    /**
     * 获取模型对应的编码器      * <p>
     * 按模型缓存，首次命中 → 直接从 registry 查找 → 失败则退回默认编码器。
     * ConcurrentHashMap.computeIfAbsent 替代 Go 的 sync.RWMutex double-checked locking。
     */
    public Encoding getEncoding(String model) {
        if (model == null || model.isEmpty()) {
            return defaultEncoding;
        }
        return encodingCache.computeIfAbsent(model, m -> {
            try {
                return registry.getEncodingForModel(m).orElse(defaultEncoding);
            } catch (Exception e) {
                log.debug("failed to get encoding for model '{}', using default cl100k_base: {}", m, e.getMessage());
                return defaultEncoding;
            }
        });
    }

    /**
     * 计算文本的 Token 数量（指定模型）      *
     * @param model 模型名（如 "gpt-4o"、"gpt-3.5-turbo"）
     * @param text  待计数的文本
     * @return Token 数量
     */
    public int countTokens(String model, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Encoding encoding = getEncoding(model);
        return encoding.countTokens(text);
    }

    /**
     * 计算文本的 Token 数量（使用默认编码器 cl100k_base）
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return defaultEncoding.countTokens(text);
    }

    /**
     * 编码文本为 Token ID 列表
     */
    public List<Integer> encode(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        IntArrayList tokenIds = defaultEncoding.encode(text);
        return toBoxedList(tokenIds);
    }

    /**
     * 解码 Token ID 列表为文本
     */
    public String decode(List<Integer> tokenIds) {
        if (tokenIds == null || tokenIds.isEmpty()) {
            return "";
        }
        IntArrayList list = new IntArrayList(tokenIds.size());
        tokenIds.forEach(list::add);
        return defaultEncoding.decode(list);
    }

    /** IntArrayList → List<Integer>（jtokkit 1.1.0 IntArrayList 不实现 List 接口） */
    private static List<Integer> toBoxedList(IntArrayList list) {
        List<Integer> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            result.add(list.get(i));
        }
        return result;
    }
}
