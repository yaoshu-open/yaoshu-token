package yaoshu.token;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.alicp.jetcache.anno.config.EnableMethodCache;

/**
 * Token 网关
 *
 * @author yaoshu
 */
@EnableAsync
@EnableScheduling
@EnableMethodCache(basePackages = "yaoshu.token")
@SpringBootApplication
public class TokenApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenApplication.class, args);
    }

}
