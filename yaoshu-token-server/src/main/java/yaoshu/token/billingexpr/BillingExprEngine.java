package yaoshu.token.billingexpr;

import cn.hutool.v7.core.text.StrUtil;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import ai.yue.library.base.convert.Convert;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分层计费表达式引擎  * <p>
 * 使用 Aviator 表达式引擎替代 Go 的 expr-lang/expr。核心能力：
 * <ul>
 * <li>编译缓存：表达式字符串 → SHA-256 → 编译结果缓存（最大 256 条）</li>
 * <li>变量检测：通过 Aviator 的 getVariableFullNames 替代 Go AST introspection</li>
 * <li>自定义函数：tier/header/param/has/hour/minute/weekday/month/day + math 函数</li>
 * <li>双引号适配：Go expr-lang 使用双引号字符串，Aviator 使用单引号，编译前做转换</li>
 * </ul>
 */
@Slf4j
public final class BillingExprEngine {

    private static final int MAX_CACHE_SIZE = 256;

    /** 默认表达式版本（无版本前缀时使用） */
    public static final int DEFAULT_EXPR_VERSION = 1;

    /** Aviator 实例（独立实例，避免污染全局） */
    private static final AviatorEvaluatorInstance AVIATOR = createAviatorInstance();

    /** 编译缓存：exprHash → 缓存条目 */
    private static final ConcurrentHashMap<String, CachedEntry> CACHE = new ConcurrentHashMap<>(64);

    private BillingExprEngine() {
    }

    // ======================== 编译缓存 ======================== 
    /**
     * 缓存条目      */
    private static class CachedEntry {
        final Expression expression;
        final Set<String> usedVars;
        final int version;

        CachedEntry(Expression expression, Set<String> usedVars, int version) {
            this.expression = expression;
            this.usedVars = usedVars;
            this.version = version;
        }
    }

    /**
     * 解析表达式版本标签。      * 格式："v1:tier(...)" → version=1, body="tier(...)"。无前缀默认 v1。
     */
    public static int[] parseExprVersion(String exprStr) {
        if (exprStr != null && exprStr.startsWith("v1:")) {
            return new int[]{1};
        }
        return new int[]{DEFAULT_EXPR_VERSION};
    }

    /**
     * 编译表达式（带缓存）。      */
    public static Expression compileFromCache(String exprStr) {
        return compileFromCacheByHash(exprStr, exprHashString(exprStr));
    }

    /**
     * 按 hash 编译表达式（带缓存）。      */
    public static Expression compileFromCacheByHash(String exprStr, String hash) {
        CachedEntry entry = CACHE.get(hash);
        if (entry != null) {
            return entry.expression;
        }

        // Aviator 使用单引号，Go expr-lang 使用双引号——编译前转换
        String adapted = adaptExprForAviator(exprStr);
        Expression expression = AVIATOR.compile(adapted, true);

        // 提取使用的变量名
        Set<String> usedVars = extractUsedVars(expression);

        // 缓存大小超限时清空（与 Go 逻辑一致）
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            CACHE.clear();
        }
        int version = parseExprVersion(exprStr)[0];
        CACHE.put(hash, new CachedEntry(expression, usedVars, version));

        return expression;
    }

    /**
     * 返回表达式的版本号。      */
    public static int exprVersion(String exprStr) {
        if (StrUtil.isBlank(exprStr)) {
            return DEFAULT_EXPR_VERSION;
        }
        String hash = exprHashString(exprStr);
        CachedEntry entry = CACHE.get(hash);
        if (entry != null) {
            return entry.version;
        }
        return parseExprVersion(exprStr)[0];
    }

    /**
     * 返回表达式引用的变量名集合。      * <p>
     * 用于决定 P/C 是否需要减去子类别 token。
     */
    public static Set<String> usedVars(String exprStr) {
        if (StrUtil.isBlank(exprStr)) {
            return null;
        }
        String hash = exprHashString(exprStr);
        CachedEntry entry = CACHE.get(hash);
        if (entry != null) {
            return entry.usedVars;
        }
        // 编译（并缓存）以填充 usedVars
        compileFromCacheByHash(exprStr, hash);
        entry = CACHE.get(hash);
        return entry != null ? entry.usedVars : null;
    }

    /**
     * 清除编译缓存。      */
    public static void invalidateCache() {
        CACHE.clear();
    }

    // ======================== 表达式执行 ======================== 
    /**
     * 编译并执行表达式，返回配额（分组前）和 trace 信息。
     */
    public static ExprRunResult runExprWithRequest(String exprStr, TokenParams params, RequestInput request) {
        Expression expression = compileFromCache(exprStr);
        return runProgram(expression, params, request);
    }

    /**
     * 按 hash 编译并执行表达式。
     */
    public static ExprRunResult runExprByHashWithRequest(String exprStr, String hash, TokenParams params, RequestInput request) {
        Expression expression = compileFromCacheByHash(exprStr, hash);
        return runProgram(expression, params, request);
    }

    /**
     * 执行已编译的表达式。      * <p>
     * 构建 Aviator 环境变量 Map，注册 tier/param/header/has/时间函数为 env 内的闭包变量。
     */
    private static ExprRunResult runProgram(Expression expression, TokenParams params, RequestInput request) {
        TraceResult trace = new TraceResult(null, 0);
        Map<String, String> headers = normalizeHeaders(request != null ? request.getHeaders() : null);
        byte[] body = request != null ? request.getBody() : null;

        Map<String, Object> env = new HashMap<>(32);
        // token 变量
        env.put("p", params.p);
        env.put("c", params.c);
        env.put("len", params.len);
        env.put("cr", params.cr);
        env.put("cc", params.cc);
        env.put("cc1h", params.cc1h);
        env.put("img", params.img);
        env.put("img_o", params.imgO);
        env.put("ai", params.ai);
        env.put("ao", params.ao);

        // tier 函数：记录匹配阶梯并返回 value
        env.put("tier", new TierFunction(trace));
        // header 函数：读取请求头
        env.put("header", new HeaderFunction(headers));
        // param 函数：从请求体 JSON path 读取值
        env.put("param", new ParamFunction(body));
        // has 函数：子串检查
        env.put("has", new HasFunction());

        // 时间函数（时区感知）
        env.put("hour", new TimeFunction(TimeFuncType.HOUR));
        env.put("minute", new TimeFunction(TimeFuncType.MINUTE));
        env.put("weekday", new TimeFunction(TimeFuncType.WEEKDAY));
        env.put("month", new TimeFunction(TimeFuncType.MONTH));
        env.put("day", new TimeFunction(TimeFuncType.DAY));

        Object result = expression.execute(env);
        if (result instanceof Number n) {
            return new ExprRunResult(n.doubleValue(), trace);
        }
        throw new IllegalStateException("expr result is " + (result == null ? "null" : result.getClass().getName()) + ", want double");
    }

    // ======================== 结算编排（settle.go + round.go） ========================

    /**
     * 四舍五入。      */
    public static int quotaRound(double f) {
        return (int) Math.round(f);
    }

    /**
     * 表达式输出 → 配额转换。      * <p>
     * v1: 系数是 $/1M tokens 价格，转换公式为 output / 1_000_000 * quotaPerUnit。
     */
    public static double quotaConversion(double exprOutput, BillingSnapshot snap) {
        return exprOutput / 1_000_000.0 * snap.getQuotaPerUnit();
    }

    /**
     * 执行分层结算。      * <p>
     * 用冻结的 BillingSnapshot 重新执行表达式，返回实际配额。
     */
    public static TieredResult computeTieredQuotaWithRequest(BillingSnapshot snap, TokenParams params, RequestInput request) {
        ExprRunResult runResult = runExprByHashWithRequest(
                snap.getExprString(), snap.getExprHash(), params, request);
        double cost = runResult.quota();
        TraceResult trace = runResult.trace();

        double quotaBeforeGroup = quotaConversion(cost, snap);
        int afterGroup = quotaRound(quotaBeforeGroup * snap.getGroupRatio());
        boolean crossed = trace.getMatchedTier() != null && !trace.getMatchedTier().equals(snap.getEstimatedTier());

        return new TieredResult(quotaBeforeGroup, afterGroup, trace.getMatchedTier(), crossed);
    }

    // ======================== 辅助方法 ========================

    /**
     * 计算表达式字符串的 SHA-256 哈希。      */
    public static String exprHashString(String expr) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(expr.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
        for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 提取已编译表达式使用的变量名集合。      * <p>
     * Aviator 通过 getVariableFullNames 获取变量名列表。
     * 过滤掉函数名（tier/header/param/has/hour/minute/weekday/month/day/max/min/abs/ceil/floor），
     * 只保留 token 变量。
     */
    private static Set<String> extractUsedVars(Expression expression) {
        Set<String> vars = new HashSet<>();
        for (String name : expression.getVariableFullNames()) {
            // Aviator 可能把自定义函数也报告为变量名，需要过滤
            if (!isFunctionName(name)) {
                vars.add(name);
            }
        }
        return vars;
    }

    /** Aviator 环境中注册的函数名集合 */
    private static final Set<String> FUNCTION_NAMES = Set.of(
            "tier", "header", "param", "has", "hour", "minute", "weekday", "month", "day",
            "max", "min", "abs", "ceil", "floor"
    );

    private static boolean isFunctionName(String name) {
        return FUNCTION_NAMES.contains(name);
    }

    /**
     * 将 Go expr-lang 表达式适配为 Aviator 兼容格式。
     * <p>
     * 主要差异：Go 使用双引号字符串，Aviator 使用单引号。
     * billingexpr 的表达式格式受限（字符串仅出现在 tier name 和 header key 中），
     * 简单的双→单引号替换是安全的。
     */
    static String adaptExprForAviator(String exprStr) {
        if (exprStr == null || exprStr.isEmpty()) {
            return exprStr;
        }
        // 去除版本前缀
        String body = exprStr;
        if (body.startsWith("v1:")) {
            body = body.substring(3);
        }
        // 双引号 → 单引号
        return body.replace("\"", "'");
    }

    /**
     * 标准化 header map（key 转小写、去空白）。      */
    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, String> normalized = new HashMap<>(headers.size());
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() == null) continue;
            String k = e.getKey().trim().toLowerCase();
            String v = e.getValue() != null ? e.getValue().trim() : "";
            if (!k.isEmpty() && !v.isEmpty()) {
                normalized.put(k, v);
            }
        }
        return normalized;
    }

    // ======================== Aviator 实例初始化 ========================

    /**
     * 创建独立的 Aviator 实例并注册 math 函数。
     * <p>
     * Aviator 的 math 函数默认带 math. 命名空间前缀（如 math.abs）。
     * billingexpr 表达式使用裸函数名（abs/max/min/ceil/floor），
     * 因此需要注册为无前缀的自定义函数。
     */
    private static AviatorEvaluatorInstance createAviatorInstance() {
        AviatorEvaluatorInstance instance = AviatorEvaluator.newInstance();

        // 注册 math 函数别名（无前缀）
        instance.addFunction(new MathUnaryFunction("abs", Math::abs));
        instance.addFunction(new MathBinaryFunction("max", Math::max));
        instance.addFunction(new MathBinaryFunction("min", Math::min));
        instance.addFunction(new MathUnaryFunction("ceil", Math::ceil));
        instance.addFunction(new MathUnaryFunction("floor", Math::floor));

        return instance;
    }

    /** 数学一元函数（abs/ceil/floor） */
    private static class MathUnaryFunction extends AbstractVariadicFunction {
        private final String name;
        private final java.util.function.DoubleUnaryOperator op;

        MathUnaryFunction(String name, java.util.function.DoubleUnaryOperator op) {
            this.name = name;
            this.op = op;
        }

        @Override public String getName() { return name; }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            return AviatorRuntimeJavaType.valueOf(op.applyAsDouble(toDouble(args[0], env)));
        }
    }

    /** 数学二元函数（max/min） */
    private static class MathBinaryFunction extends AbstractVariadicFunction {
        private final String name;
        private final java.util.function.DoubleBinaryOperator op;

        MathBinaryFunction(String name, java.util.function.DoubleBinaryOperator op) {
            this.name = name;
            this.op = op;
        }

        @Override public String getName() { return name; }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            double a = toDouble(args[0], env);
            double b = toDouble(args[1], env);
            return AviatorRuntimeJavaType.valueOf(op.applyAsDouble(a, b));
        }
    }

    /** 从 AviatorObject 提取 double 值 */
    private static double toDouble(AviatorObject obj, Map<String, Object> env) {
        Object val = obj.getValue(env);
        if (val instanceof Number n) return n.doubleValue();
        throw new IllegalArgumentException("expected number, got " + (val == null ? "null" : val.getClass()));
    }

    // ======================== 自定义函数实现 ========================

    /** 表达式执行结果 */
    public record ExprRunResult(double quota, TraceResult trace) {}

    /** tier 函数：记录匹配阶梯并返回 value */
    private static class TierFunction extends AbstractVariadicFunction {
        private final TraceResult trace;

        TierFunction(TraceResult trace) { this.trace = trace; }

        @Override public String getName() { return "tier"; }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            String name = (String) args[0].getValue(env);
            double value = toDouble(args[1], env);
            trace.setMatchedTier(name);
            trace.setCost(value);
            return AviatorRuntimeJavaType.valueOf(value);
        }
    }

    /** header 函数：读取请求头 */
    private static class HeaderFunction extends AbstractVariadicFunction {
        private final Map<String, String> headers;

        HeaderFunction(Map<String, String> headers) { this.headers = headers; }

        @Override public String getName() { return "header"; }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            String key = (String) args[0].getValue(env);
            String value = "";
            if (key != null) {
                value = headers.getOrDefault(key.trim().toLowerCase(), "");
            }
            return AviatorRuntimeJavaType.valueOf(value);
        }
    }

    /** param 函数：从请求体 JSON path 读取值 */
    private static class ParamFunction extends AbstractVariadicFunction {
        private final byte[] body;

        ParamFunction(byte[] body) { this.body = body; }

        @Override public String getName() { return "param"; }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            String path = (String) args[0].getValue(env);
            if (path == null || path.trim().isEmpty() || body == null || body.length == 0) {
                return AviatorRuntimeJavaType.valueOf(null);
            }
            // 用 Convert 解析 JSON body，按 GJSON path 导航读取值
            Object value = resolveJsonPath(body, path.trim());
            return AviatorRuntimeJavaType.valueOf(value);
        }
    }

    /**
     * 轻量 JSON path 解析，替代 Go 的 gjson.GetBytes。
     * <p>
     * 支持 GJSON 点号路径语法（如 "messages.0.role"）。
     * Convert 解析 body 为 JSONObject 后逐级导航。
     */
    @SuppressWarnings("unchecked")
    private static Object resolveJsonPath(byte[] body, String path) {
        try {
            Object parsed = Convert.toJavaBean(new String(body, StandardCharsets.UTF_8), Object.class);
            if (parsed == null) return null;
            // GJSON path 用点号分隔，支持数组索引
            String[] segments = path.split("\\.");
            Object current = parsed;
            for (String seg : segments) {
                if (current == null) return null;
                // 尝试数组索引
                if (seg.matches("\\d+") && current instanceof java.util.List<?> list) {
                    int idx = Integer.parseInt(seg);
                    current = idx < list.size() ? list.get(idx) : null;
                } else if (current instanceof Map<?, ?> map) {
                    current = map.get(seg);
                } else {
                    return null;
                }
            }
            return current;
        } catch (Exception e) {
            log.debug("JSON path resolve failed for path '{}': {}", path, e.getMessage());
            return null;
        }
    }

    /** has 函数：子串检查 */
    private static class HasFunction extends AbstractVariadicFunction {
        @Override public String getName() { return "has"; }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            Object source = args[0].getValue(env);
            String substr = (String) args[1].getValue(env);
            if (source == null || substr == null || substr.isEmpty()) {
                return AviatorRuntimeJavaType.valueOf(false);
            }
            return AviatorRuntimeJavaType.valueOf(String.valueOf(source).contains(substr));
        }
    }

    /** 时间函数类型 */
    private enum TimeFuncType { HOUR, MINUTE, WEEKDAY, MONTH, DAY }

    /** 时间函数：按时区返回当前时间分量 */
    private static class TimeFunction extends AbstractVariadicFunction {
        private final TimeFuncType type;

        TimeFunction(TimeFuncType type) { this.type = type; }

        @Override
        public String getName() {
            return switch (type) {
                case HOUR -> "hour";
                case MINUTE -> "minute";
                case WEEKDAY -> "weekday";
                case MONTH -> "month";
                case DAY -> "day";
            };
        }

        @Override
        public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
            String tz = (String) args[0].getValue(env);
            ZonedDateTime now = timeInZone(tz);
            int value = switch (type) {
                case HOUR -> now.getHour();
                case MINUTE -> now.getMinute();
                case WEEKDAY -> now.getDayOfWeek().getValue() % 7; // Go: Sunday=0
                case MONTH -> now.getMonthValue();
                case DAY -> now.getDayOfMonth();
            };
            return AviatorRuntimeJavaType.valueOf(value);
        }

        private ZonedDateTime timeInZone(String tz) {
            if (tz == null || tz.trim().isEmpty()) {
                return ZonedDateTime.now(ZoneId.of("UTC"));
            }
            try {
                return ZonedDateTime.now(ZoneId.of(tz.trim()));
            } catch (Exception e) {
                return ZonedDateTime.now(ZoneId.of("UTC"));
            }
        }
    }
}
