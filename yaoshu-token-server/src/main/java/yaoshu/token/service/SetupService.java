package yaoshu.token.service;

import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.util.I18nUtils;
import ai.yue.library.base.view.R;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yaoshu.token.constant.CommonConstants;
import yaoshu.token.constant.SetupConstants;
import yaoshu.token.mapper.SetupMapper;
import yaoshu.token.mapper.UserMapper;
import yaoshu.token.pojo.entity.Setup;
import yaoshu.token.pojo.entity.User;
import yaoshu.token.pojo.ipo.SetupIPO;
import yaoshu.token.pojo.vo.SetupStatusVO;

import java.security.SecureRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 系统初始化服务  * <p>
 * 提供两条初始化路径：
 * <ol>
 * <li>auto 模式（默认）：{@link #initializeAuto()} 由启动钩子调用，自动创建 root 账号</li>
 * <li>interactive 模式：{@link #postSetup(SetupIPO)} 由前端 SetupWizard 提交</li>
 * </ol>
 * 幂等判据：setups 表有记录 = 已初始化。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    private final SetupMapper setupMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final OptionService optionService;

    /** 数据库类型（本项目固定 MySQL） */
    private static final String DB_TYPE = "mysql";

    /** 角色：超级管理员 */
    private static final int ROLE_ROOT = 3;
    /** 用户状态：启用 */
    private static final int STATUS_ENABLED = 1;
    /** root 用户初始配额 */
    private static final long ROOT_QUOTA = 100_000_000;
    /** 默认用户组 */
    private static final String DEFAULT_GROUP = "default";
    /** root 显示名 */
    private static final String ROOT_DISPLAY_NAME = "Root User";

    /** 随机密码字符集（字母 + 数字，排除易混淆字符） */
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ======================== 查询 ========================

    /**
     * 系统是否已初始化（setups 表有记录 = true）
     */
    public boolean isInitialized() {
        return setupMapper.selectCount(null) > 0;
    }

    /**
     * 是否已存在 root 用户（role=3）
     */
    public boolean rootUserExists() {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, ROLE_ROOT)
        ) > 0;
    }

    /**
     * GET /api/setup 状态      */
    public SetupStatusVO getSetupStatus() {
        SetupStatusVO vo = new SetupStatusVO();
        vo.setStatus(isInitialized());
        vo.setRootInit(rootUserExists());
        vo.setDatabaseType(DB_TYPE);
        return vo;
    }

    // ======================== 路径 A：auto 模式（启动钩子） ========================

    /**
     * auto 模式初始化，由 {@link yaoshu.token.config.RootUserInitializer} 启动时调用。
     * <p>
     * 幂等：已初始化直接返回；interactive 模式跳过；无 root 则创建。
     */
    @Transactional(rollbackFor = Exception.class)
    public void initializeAuto() {
        // 1. 幂等判据：setups 表有记录
        if (isInitialized()) {
            return;
        }

        // 2. 模式判断
        String mode = envOrDefault(SetupConstants.ENV_INIT_MODE, SetupConstants.MODE_AUTO);
        if (SetupConstants.MODE_INTERACTIVE.equalsIgnoreCase(mode)) {
            log.warn("[Setup] 系统未初始化，YAOSHU_INIT_MODE=interactive，等待前端 SetupWizard 通过 POST /api/setup 完成");
            return;
        }

        // 3. auto 模式：创建 root 或兼容老库
        boolean rootExists = rootUserExists();
        String username = envOrDefault(SetupConstants.ENV_INIT_ROOT_USERNAME, SetupConstants.DEFAULT_ROOT_USERNAME);
        boolean passwordFromEnv = System.getenv(SetupConstants.ENV_INIT_ROOT_PASSWORD) != null;
        String password = passwordFromEnv
                ? System.getenv(SetupConstants.ENV_INIT_ROOT_PASSWORD)
                : generateRandomPassword();

        if (!rootExists) {
            User root = buildRootUser(username, userService.hashPassword(password));
            userMapper.insert(root);
        } else {
            // 老库兼容：已有 root 但无 setup 记录，仅补写记录（不重置密码）
            password = null;
        }

        // 4. 写 setups 记录
        writeSetupRecord();

        // 5. 日志输出（仅未通过环境变量预置密码时明文打印一次）
        if (!rootExists) {
            if (passwordFromEnv) {
                log.warn("[Setup] auto 模式初始化完成，root 账号: {}（密码由环境变量 YAOSHU_INIT_ROOT_PASSWORD 提供）", username);
            } else {
                log.warn("[Setup] auto 模式初始化完成，root 账号: {} / 密码: {}（请立即登录修改，此密码仅显示一次）", username, password);
            }
        } else {
            log.warn("[Setup] 检测到已有 root 用户，已补写 setups 初始化记录");
        }
    }

    // ======================== 路径 B：interactive 模式（前端 SetupWizard） ========================

    /**
     * POST /api/setup      * <p>
     * 幂等：已初始化拒绝；已存在 root 则跳过建账号仅写 options + setups。
     * 入参基本校验由 Controller 层 @Valid 完成，本方法做业务级校验（密码一致性）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void postSetup(SetupIPO ipo) {
        // 1. 幂等：已初始化拒绝
        if (isInitialized()) {
            throw new ResultException(R.errorPrompt(I18nUtils.get("setup.already_initialized")));
        }

        // 2. 若无 root 用户则创建（已存在则跳过）
        boolean rootExists = rootUserExists();
        if (!rootExists) {
            // 业务校验：密码一致性（@Valid 无法跨字段校验）
            if (!ipo.getPassword().equals(ipo.getConfirmPassword())) {
                throw new ResultException(R.errorPrompt(I18nUtils.get("setup.password_mismatch")));
            }
            User root = buildRootUser(ipo.getUsername(), userService.hashPassword(ipo.getPassword()));
            userMapper.insert(root);
        }

        // 3. 写 options（使用模式开关，复用 OptionService）
        if (ipo.getSelfUseModeEnabled() != null) {
            optionService.saveOrUpdate("SelfUseModeEnabled", String.valueOf(ipo.getSelfUseModeEnabled()));
        }
        if (ipo.getDemoSiteEnabled() != null) {
            optionService.saveOrUpdate("DemoSiteEnabled", String.valueOf(ipo.getDemoSiteEnabled()));
        }

        // 4. 写 setups 记录
        writeSetupRecord();
    }

    // ======================== 内部工具 ========================

    private User buildRootUser(String username, String hashedPassword) {
        User root = new User();
        root.setUsername(username);
        root.setPassword(hashedPassword);
        root.setDisplayName(ROOT_DISPLAY_NAME);
        root.setRole(ROLE_ROOT);
        root.setStatus(STATUS_ENABLED);
        root.setQuota(ROOT_QUOTA);
        root.setGroup(DEFAULT_GROUP);
        root.setCreatedAt(System.currentTimeMillis());
        return root;
    }

    private void writeSetupRecord() {
        Setup setup = new Setup();
        setup.setVersion(CommonConstants.version);
        setup.setInitializedAt(System.currentTimeMillis());
        setupMapper.insert(setup);
    }

    /**
     * 生成随机密码（字母 + 数字，排除易混淆字符）
     */
    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(SetupConstants.RANDOM_PASSWORD_LENGTH);
        for (int i = 0; i < SetupConstants.RANDOM_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 读取环境变量，不存在则返回默认值
     */
    private String envOrDefault(String envName, String defaultValue) {
        String val = System.getenv(envName);
        return (val == null || val.isBlank()) ? defaultValue : val;
    }
}
