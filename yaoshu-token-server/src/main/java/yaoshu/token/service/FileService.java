package yaoshu.token.service;

import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 文件存储服务  * <p>
 * 处理文件上传/下载（本地存储 / S3 兼容存储）。
 * 当前实现本地存储模式；S3 模式待配置接入后激活。
 */
@Slf4j
public class FileService {

    /** 本地存储根目录 */
    private static final String LOCAL_STORAGE_DIR = "uploads";

    /**
     * 上传文件到本地存储      *
     * @param inputStream 文件输入流
     * @param filename    文件名
     * @return 文件相对路径
     */
    public String uploadFile(InputStream inputStream, String filename) throws Exception {
        Path dir = Paths.get(LOCAL_STORAGE_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // 生成唯一文件名（避免覆盖）
        String uniqueName = System.currentTimeMillis() + "_" + filename;
        Path filePath = dir.resolve(uniqueName);

        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        log.info("file uploaded: {}", filePath);

        return "/" + LOCAL_STORAGE_DIR + "/" + uniqueName;
    }

    /**
     * 读取文件      *
     * @param filePath 文件相对路径
     * @return 文件输入流
     */
    public InputStream getFile(String filePath) throws Exception {
        Path path = Paths.get(LOCAL_STORAGE_DIR).resolve(filePath.replace("/" + LOCAL_STORAGE_DIR + "/", ""));
        if (!Files.exists(path)) {
            throw new RuntimeException("file not found: " + filePath);
        }
        return Files.newInputStream(path);
    }
}
