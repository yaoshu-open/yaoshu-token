package yaoshu.token.relay.common;

import ai.yue.library.base.util.SpringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import lombok.extern.slf4j.Slf4j;
import yaoshu.token.pojo.dto.RelayException;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 参数覆写引擎  * <p>
 * 在请求转发至上游前，根据渠道配置的 ParamOverride 规则对请求体 JSON 进行增删改操作。
 * 支持 20+ 种操作模式（delete/set/move/copy/prepend/append/trim_prefix/trim_suffix/
 * ensure_prefix/ensure_suffix/trim_space/to_lower/to_upper/replace/regex_replace/
 * return_error/prune_objects/set_header/delete_header/copy_header/move_header/pass_headers/
 * sync_fields），以及条件判断（AND/OR 逻辑、反选、全等/前缀/后缀/包含/数值比较）。
 */
@Slf4j
public final class OverrideUtils {

    private OverrideUtils() {
    }

    private static ObjectMapper MAPPER;

    private static ObjectMapper getMapper() {
        if (MAPPER == null) {
            MAPPER = SpringUtils.getBean(ObjectMapper.class);
        }
        return MAPPER;
    }

    // ======================== 偏移索引正则 ========================

    /** 负数数组索引，如 .messages.-1 → 最后一元素 */
    private static final Pattern NEGATIVE_INDEX_REGEX = Pattern.compile("\\.(-\\d+)");

    // ======================== 常量 ========================

    static final String PARAM_OVERRIDE_CONTEXT_REQUEST_HEADERS = "request_headers";
    static final String PARAM_OVERRIDE_CONTEXT_HEADER_OVERRIDE = "header_override";
    static final String PARAM_OVERRIDE_CONTEXT_AUDIT_RECORDER = "__param_override_audit_recorder";

    /** 敏感路径前缀（审计记录时需要记录变更） */
    private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
            "model", "original_model", "upstream_model", "service_tier", "inference_geo",
            "speed", "messages", "input", "instructions", "system", "contents",
            "systemInstruction", "system_instruction"
    );

    /** 验证不需要来源头的错误 */
    static final String ERR_SOURCE_HEADER_NOT_FOUND = "source header does not exist";

    // ======================== 条件操作 ========================

    /**
     * 条件操作 POJO      */
    public static class ConditionOperation {
        private String path;
        private String mode;         // full, prefix, suffix, contains, gt, gte, lt, lte
        private Object value;
        private boolean invert;
        private boolean passMissingKey;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
        public boolean isInvert() { return invert; }
        public void setInvert(boolean invert) { this.invert = invert; }
        public boolean isPassMissingKey() { return passMissingKey; }
        public void setPassMissingKey(boolean passMissingKey) { this.passMissingKey = passMissingKey; }
    }

    /**
     * 参数操作 POJO      */
    public static class ParamOperation {
        private String path;
        private String mode;         // delete, set, move, copy, prepend, append, ...
        private Object value;
        private boolean keepOrigin;
        private String from;
        private String to;
        private List<ConditionOperation> conditions;
        private String logic = "OR"; // AND / OR

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
        public boolean isKeepOrigin() { return keepOrigin; }
        public void setKeepOrigin(boolean keepOrigin) { this.keepOrigin = keepOrigin; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public List<ConditionOperation> getConditions() { return conditions; }
        public void setConditions(List<ConditionOperation> conditions) { this.conditions = conditions; }
        public String getLogic() { return logic; }
        public void setLogic(String logic) { this.logic = logic; }
    }

    /**
     * 参数覆写返回错误      */
    public static class ParamOverrideReturnError extends RuntimeException {
        private final String message;
        private final int statusCode;
        private final String code;
        private final String type;
        private final boolean skipRetry;

        public ParamOverrideReturnError(String message, int statusCode, String code, String type, boolean skipRetry) {
            super(message != null && !message.isEmpty() ? message : "param override return error");
            this.message = message;
            this.statusCode = statusCode;
            this.code = code;
            this.type = type;
            this.skipRetry = skipRetry;
        }

        public String getErrorCode() { return code; }
        public String getErrorType() { return type; }
        public int getStatusCode() { return statusCode; }
        public boolean isSkipRetry() { return skipRetry; }
    }

    // ======================== 审计记录器 ========================

    static class ParamOverrideAuditRecorder {
        final List<String> lines = new ArrayList<>();

        void recordOperation(String mode, String path, String from, String to, Object value) {
            String line = buildAuditLine(mode, path, from, to, value);
            if (line != null && !line.isEmpty() && !lines.contains(line)) {
                lines.add(line);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static ParamOverrideAuditRecorder getAuditRecorder(Map<String, Object> context) {
        if (context == null) return null;
        return (ParamOverrideAuditRecorder) context.get(PARAM_OVERRIDE_CONTEXT_AUDIT_RECORDER);
    }

    // ======================== APIError 构建 ========================

    /**
     * 从 ParamOverrideReturnError 构建 OpenAI 兼容错误
     */
    public static Object newApiErrorFromParamOverride(ParamOverrideReturnError err) {
        if (err == null) {
            return Map.of(
                    "error", Map.of(
                            "message", "param override return error is nil",
                            "type", "invalid_request_error",
                            "code", "channel_param_override_invalid"
                    )
            );
        }

        int statusCode = err.getStatusCode();
        if (statusCode < 100 || statusCode > 511) {
            statusCode = 400;
        }

        String errorCode = err.getErrorCode();
        if (errorCode == null || errorCode.trim().isEmpty()) {
            errorCode = "invalid_request";
        }

        String errorType = err.getErrorType();
        if (errorType == null || errorType.trim().isEmpty()) {
            errorType = "invalid_request_error";
        }

        String message = err.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "request blocked by param override";
        }

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("message", message);
        errorBody.put("type", errorType);
        errorBody.put("code", errorCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", errorBody);
        result.put("status_code", statusCode);
        return result;
    }

    // ======================== 主入口 ========================

    /**
     * 应用参数覆写      *
     * @param jsonData          原始请求体 JSON 字节
     * @param paramOverride     参数覆写配置
     * @param conditionContext  条件上下文
     * @return 覆写后的 JSON 字节
     */
    public static byte[] applyParamOverride(
            byte[] jsonData,
            Map<String, Object> paramOverride,
            Map<String, Object> conditionContext) throws Exception {

        if (paramOverride == null || paramOverride.isEmpty()) {
            return jsonData;
        }

        ParamOverrideAuditRecorder auditRecorder = getAuditRecorder(conditionContext);

        // 尝试解析为 operations 格式
        List<ParamOperation> operations = tryParseOperations(paramOverride);
        if (operations != null) {
            // 先处理旧格式 top-level key
            Map<String, Object> legacyOverride = buildLegacyParamOverride(paramOverride);
            JsonNode workingNode = getMapper().readTree(jsonData);

            if (!legacyOverride.isEmpty()) {
                workingNode = applyOperationsLegacy(workingNode, legacyOverride, auditRecorder);
            }

            // 执行新格式操作
            byte[] result = applyOperations(getMapper().writeValueAsBytes(workingNode), operations, conditionContext);
            return result;
        }

        // 纯旧格式
        return applyOperationsLegacyBytes(jsonData, paramOverride, auditRecorder);
    }

    /**
     * 带 RelayInfo 上下文的参数覆写      */
    public static byte[] applyParamOverrideWithRelayInfo(byte[] jsonData, RelayInfo info) throws Exception {
        Map<String, Object> paramOverride = getParamOverrideMap(info);
        if (paramOverride == null || paramOverride.isEmpty()) {
            return jsonData;
        }

        Map<String, Object> overrideCtx = buildParamOverrideContext(info);
        ParamOverrideAuditRecorder auditRecorder = null;

        if (shouldEnableParamOverrideAudit(paramOverride)) {
            auditRecorder = new ParamOverrideAuditRecorder();
            overrideCtx.put(PARAM_OVERRIDE_CONTEXT_AUDIT_RECORDER, auditRecorder);
        }

        byte[] result = applyParamOverride(jsonData, paramOverride, overrideCtx);

        // 同步 Runtime Header Override
        syncRuntimeHeaderOverrideFromContext(info, overrideCtx);

        if (info != null) {
            if (auditRecorder != null) {
                info.setParamOverrideAudit(auditRecorder.lines);
            } else {
                info.setParamOverrideAudit(null);
            }
        }

        return result;
    }

    // ======================== 私有方法 ========================

    private static Map<String, Object> buildLegacyParamOverride(Map<String, Object> paramOverride) {
        if (paramOverride == null || paramOverride.isEmpty()) return Collections.emptyMap();
        Map<String, Object> legacy = new LinkedHashMap<>(paramOverride.size());
        for (Map.Entry<String, Object> entry : paramOverride.entrySet()) {
            if ("operations".equalsIgnoreCase(entry.getKey().trim())) continue;
            legacy.put(entry.getKey(), entry.getValue());
        }
        return legacy;
    }

    /**
     * 尝试解析 paramOverride 为 operations 格式      */
    @SuppressWarnings("unchecked")
    public static List<ParamOperation> tryParseOperations(Map<String, Object> paramOverride) {
        Object opsValue = paramOverride.get("operations");
        if (opsValue == null) return null;

        List<Map<String, Object>> opMaps;
        if (opsValue instanceof List) {
            opMaps = new ArrayList<>();
            for (Object item : (List<?>) opsValue) {
                if (!(item instanceof Map)) return null;
                opMaps.add((Map<String, Object>) item);
            }
        } else {
            return null;
        }

        List<ParamOperation> operations = new ArrayList<>();
        for (Map<String, Object> opMap : opMaps) {
            ParamOperation op = new ParamOperation();
            op.setPath((String) opMap.getOrDefault("path", ""));

            Object mode = opMap.get("mode");
            if (!(mode instanceof String)) return null;
            op.setMode((String) mode);

            op.setValue(opMap.get("value"));
            op.setKeepOrigin(Boolean.TRUE.equals(opMap.get("keep_origin")));
            op.setFrom((String) opMap.get("from"));
            op.setTo((String) opMap.get("to"));
            op.setLogic(Objects.toString(opMap.get("logic"), "OR"));

            // 解析条件
            Object conditions = opMap.get("conditions");
            if (conditions instanceof List) {
                List<ConditionOperation> condList = new ArrayList<>();
                for (Object condItem : (List<?>) conditions) {
                    if (condItem instanceof Map) {
                        condList.add(parseConditionOperation((Map<String, Object>) condItem));
                    }
                }
                op.setConditions(condList);
            }

            operations.add(op);
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private static ConditionOperation parseConditionOperation(Map<String, Object> condMap) {
        ConditionOperation cond = new ConditionOperation();
        cond.setPath((String) condMap.getOrDefault("path", ""));
        cond.setMode((String) condMap.getOrDefault("mode", "full"));
        cond.setValue(condMap.get("value"));
        cond.setInvert(Boolean.TRUE.equals(condMap.get("invert")));
        cond.setPassMissingKey(Boolean.TRUE.equals(condMap.get("pass_missing_key")));
        return cond;
    }

    // ======================== 旧格式覆写 ========================

    /**
     * 旧格式参数覆写（[]byte 版）      * <p>
     * 顶层 key 视为字面 key（不解析嵌套路径），逐个用 value 覆盖。
     */
    @SuppressWarnings("unchecked")
    private static byte[] applyOperationsLegacyBytes(
            byte[] jsonData,
            Map<String, Object> paramOverride,
            ParamOverrideAuditRecorder auditRecorder) throws Exception {

        if (paramOverride.isEmpty()) return jsonData;

        JsonNode node = getMapper().readTree(jsonData);
        if (!(node instanceof ObjectNode)) return jsonData;

        ObjectNode objNode = (ObjectNode) node;
        for (Map.Entry<String, Object> entry : paramOverride.entrySet()) {
            JsonNode valueNode = getMapper().valueToTree(entry.getValue());
            objNode.set(entry.getKey(), valueNode);
            if (auditRecorder != null) {
                auditRecorder.recordOperation("set", entry.getKey(), "", "", entry.getValue());
            }
        }

        return getMapper().writeValueAsBytes(objNode);
    }

    /**
     * 旧格式覆写（JsonNode 版），返回修改后的 JsonNode
     */
    @SuppressWarnings("unchecked")
    static JsonNode applyOperationsLegacy(
            JsonNode node,
            Map<String, Object> paramOverride,
            ParamOverrideAuditRecorder auditRecorder) throws Exception {

        if (paramOverride.isEmpty() || !(node instanceof ObjectNode)) return node;

        ObjectNode objNode = (ObjectNode) node;
        for (Map.Entry<String, Object> entry : paramOverride.entrySet()) {
            JsonNode valueNode = getMapper().valueToTree(entry.getValue());
            objNode.set(entry.getKey(), valueNode);
            if (auditRecorder != null) {
                auditRecorder.recordOperation("set", entry.getKey(), "", "", entry.getValue());
            }
        }
        return objNode;
    }

    // ======================== 新格式操作引擎 ========================

    /** applyOneOperation 的返回值：携带处理后的 JSON 与可能被刷新的 contextJSON */
    private record OpResult(byte[] data, String contextJSON) {
    }

    /**
     * 在 []byte 上应用所有 param override 操作。      * <p>
     * 全程在 byte[] 上工作，路径解析使用 {@link #processNegativeIndex} / {@link #resolveOperationPaths}
     * 支持点号嵌套路径、数组索引、负数索引与通配符 *。
     */
    private static byte[] applyOperations(
            byte[] jsonData,
            List<ParamOperation> operations,
            Map<String, Object> conditionContext) throws Exception {

        Map<String, Object> context = conditionContext != null ? conditionContext : new LinkedHashMap<>();
        ParamOverrideAuditRecorder auditRecorder = getAuditRecorder(context);
        String contextJSON = marshalContextJSON(context);

        byte[] result = jsonData;
        for (ParamOperation op : operations) {
            // 步骤1：检查条件是否满足，不满足则跳过
            List<ConditionOperation> conditions = op.getConditions() != null ? op.getConditions() : Collections.emptyList();
            if (!checkConditions(result, contextJSON, conditions, op.getLogic())) {
                continue;
            }
            // 步骤2：处理负数索引，并对路径型操作展开通配符
            String opPath = processNegativeIndex(result, op.getPath());
            List<String> opPaths = null;
            if (isPathBasedOperation(op.getMode())) {
                opPaths = resolveOperationPaths(result, opPath);
                if (opPaths.isEmpty()) {
                    continue;
                }
            }
            // 步骤3：执行单个操作
            OpResult r = applyOneOperation(result, op, opPaths, context, contextJSON, auditRecorder);
            result = r.data();
            contextJSON = r.contextJSON();
        }
        return result;
    }

    /** 路径型操作（需要解析 JSON 路径/通配符） */
    private static boolean isPathBasedOperation(String mode) {
        return switch (mode) {
            case "delete", "set", "prepend", "append", "trim_prefix", "trim_suffix",
                 "ensure_prefix", "ensure_suffix", "trim_space", "to_lower", "to_upper",
                 "replace", "regex_replace", "prune_objects" -> true;
            default -> false;
        };
    }

    /**
     * 执行单个 param override 操作。      */
    private static OpResult applyOneOperation(
            byte[] data, ParamOperation op, List<String> opPaths,
            Map<String, Object> context, String contextJSON,
            ParamOverrideAuditRecorder auditRecorder) throws Exception {

        String mode = op.getMode();
        byte[] result = data;
        switch (mode) {
            case "delete" -> {
                for (String path : opPaths) {
                    result = JsonPathOps.deleteBytes(result, path);
                    record(auditRecorder, "delete", path, "", "", null);
                }
            }
            case "set" -> {
                for (String path : opPaths) {
                    if (op.isKeepOrigin() && JsonPathOps.exists(result, path)) continue;
                    result = JsonPathOps.setBytes(result, path, op.getValue());
                    record(auditRecorder, "set", path, "", "", op.getValue());
                }
            }
            case "move" -> {
                String from = processNegativeIndex(result, op.getFrom());
                String to = processNegativeIndex(result, op.getTo());
                result = moveValue(result, from, to);
                record(auditRecorder, "move", "", from, to, null);
            }
            case "copy" -> {
                if (isBlank(op.getFrom()) || isBlank(op.getTo())) {
                    throw new IllegalArgumentException("copy from/to is required");
                }
                String from = processNegativeIndex(result, op.getFrom());
                String to = processNegativeIndex(result, op.getTo());
                result = copyValue(result, from, to);
                record(auditRecorder, "copy", "", from, to, null);
            }
            case "prepend" -> {
                for (String path : opPaths) {
                    result = modifyValue(result, path, op.getValue(), op.isKeepOrigin(), true);
                    record(auditRecorder, "prepend", path, "", "", op.getValue());
                }
            }
            case "append" -> {
                for (String path : opPaths) {
                    result = modifyValue(result, path, op.getValue(), op.isKeepOrigin(), false);
                    record(auditRecorder, "append", path, "", "", op.getValue());
                }
            }
            case "trim_prefix" -> {
                for (String path : opPaths) {
                    result = trimStringValue(result, path, op.getValue(), true);
                    record(auditRecorder, "trim_prefix", path, "", "", op.getValue());
                }
            }
            case "trim_suffix" -> {
                for (String path : opPaths) {
                    result = trimStringValue(result, path, op.getValue(), false);
                    record(auditRecorder, "trim_suffix", path, "", "", op.getValue());
                }
            }
            case "ensure_prefix" -> {
                for (String path : opPaths) {
                    result = ensureStringAffix(result, path, op.getValue(), true);
                    record(auditRecorder, "ensure_prefix", path, "", "", op.getValue());
                }
            }
            case "ensure_suffix" -> {
                for (String path : opPaths) {
                    result = ensureStringAffix(result, path, op.getValue(), false);
                    record(auditRecorder, "ensure_suffix", path, "", "", op.getValue());
                }
            }
            case "trim_space" -> {
                for (String path : opPaths) {
                    result = transformStringValue(result, path, String::trim);
                    record(auditRecorder, "trim_space", path, "", "", null);
                }
            }
            case "to_lower" -> {
                for (String path : opPaths) {
                    result = transformStringValue(result, path, s -> s.toLowerCase(Locale.ROOT));
                    record(auditRecorder, "to_lower", path, "", "", null);
                }
            }
            case "to_upper" -> {
                for (String path : opPaths) {
                    result = transformStringValue(result, path, s -> s.toUpperCase(Locale.ROOT));
                    record(auditRecorder, "to_upper", path, "", "", null);
                }
            }
            case "replace" -> {
                for (String path : opPaths) {
                    result = replaceStringValue(result, path, op.getFrom(), op.getTo());
                    record(auditRecorder, "replace", path, op.getFrom(), op.getTo(), null);
                }
            }
            case "regex_replace" -> {
                for (String path : opPaths) {
                    result = regexReplaceStringValue(result, path, op.getFrom(), op.getTo());
                    record(auditRecorder, "regex_replace", path, op.getFrom(), op.getTo(), null);
                }
            }
            case "return_error" -> {
                record(auditRecorder, "return_error", op.getPath(), "", "", op.getValue());
                throw parseParamOverrideReturnError(op.getValue());
            }
            case "prune_objects" -> {
                for (String path : opPaths) {
                    result = pruneObjects(result, path, contextJSON, op.getValue());
                }
            }
            case "set_header" -> {
                setHeaderOverrideInContext(context, op.getPath(), op.getValue(), op.isKeepOrigin());
                record(auditRecorder, "set_header", op.getPath(), "", "", op.getValue());
                contextJSON = marshalContextJSON(context);
            }
            case "delete_header" -> {
                deleteHeaderOverrideInContext(context, op.getPath());
                record(auditRecorder, "delete_header", op.getPath(), "", "", null);
                contextJSON = marshalContextJSON(context);
            }
            case "copy_header", "move_header" -> {
                String src = !isBlank(op.getFrom()) ? op.getFrom().trim() : safeTrim(op.getPath());
                String dst = !isBlank(op.getTo()) ? op.getTo().trim() : safeTrim(op.getPath());
                if ("copy_header".equals(mode)) {
                    copyHeaderInContext(context, src, dst, op.isKeepOrigin());
                } else {
                    moveHeaderInContext(context, src, dst, op.isKeepOrigin());
                }
                record(auditRecorder, mode, "", src, dst, null);
                contextJSON = marshalContextJSON(context);
            }
            case "pass_headers" -> {
                List<String> headerNames = parseHeaderPassThroughNames(op.getValue());
                for (String headerName : headerNames) {
                    copyHeaderInContext(context, headerName, headerName, op.isKeepOrigin());
                }
                record(auditRecorder, "pass_headers", "", "", "", headerNames);
                contextJSON = marshalContextJSON(context);
            }
            case "sync_fields" -> {
                result = syncFieldsBetweenTargets(result, context, op.getFrom(), op.getTo());
                record(auditRecorder, "sync_fields", "", op.getFrom(), op.getTo(), null);
                contextJSON = marshalContextJSON(context);
            }
            default -> throw new IllegalArgumentException("unknown operation: " + mode);
        }
        return new OpResult(result, contextJSON);
    }

    private static void record(ParamOverrideAuditRecorder r, String mode, String path, String from, String to, Object value) {
        if (r != null) r.recordOperation(mode, path, from, to, value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String marshalContextJSON(Map<String, Object> context) throws Exception {
        if (context == null || context.isEmpty()) return "";
        return getMapper().writeValueAsString(context);
    }

    /**
     * 解析 return_error 配置      */
    @SuppressWarnings("unchecked")
    private static ParamOverrideReturnError parseParamOverrideReturnError(Object value) {
        int statusCode = 400;
        String code = "invalid_request";
        String type = "invalid_request_error";
        boolean skipRetry = true;
        String message = "";

        if (value == null) {
            throw new IllegalArgumentException("return_error value is required");
        } else if (value instanceof String s) {
            message = s.trim();
        } else if (value instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw;
            if (m.get("message") instanceof String s) message = s.trim();
            if (message.isEmpty() && m.get("msg") instanceof String s2) message = s2.trim();
            if (m.containsKey("code")) {
                String c = String.valueOf(m.get("code")).trim();
                if (!c.isEmpty()) code = c;
            }
            if (m.get("type") instanceof String t && !t.trim().isEmpty()) type = t.trim();
            if (m.get("skip_retry") instanceof Boolean b) skipRetry = b;
            Object scRaw = m.containsKey("status_code") ? m.get("status_code") : m.get("status");
            if (scRaw != null) {
                Integer sc = parseOverrideInt(scRaw);
                if (sc == null) throw new IllegalArgumentException("return_error status_code must be an integer");
                statusCode = sc;
            }
        } else {
            throw new IllegalArgumentException("return_error value must be string or object");
        }

        if (message.isEmpty()) throw new IllegalArgumentException("return_error message is required");
        if (statusCode < 100 || statusCode > 511) {
            throw new IllegalArgumentException("return_error status code out of range: " + statusCode);
        }
        return new ParamOverrideReturnError(message, statusCode, code, type, skipRetry);
    }

    private static Integer parseOverrideInt(Object v) {
        if (v instanceof Integer i) return i;
        if (v instanceof Long l) return l.intValue();
        if (v instanceof Double d) {
            if (d != Math.floor(d)) return null;
            return d.intValue();
        }
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    // ======================== JSON 操作 ========================

    // ======================== JSON 路径引擎（gjson/sjson 等价） ========================

    /**
     * 基于 Jackson 的 JSON 路径读写引擎。      * <p>
     * 路径语法：点号分隔（{@code a.b.c}），纯数字段视为数组下标（{@code messages.0.role}）。
     * set 时自动创建中间 object/array 节点。
     */
    static final class JsonPathOps {

        private JsonPathOps() {
        }

        static boolean exists(byte[] data, String path) throws Exception {
            return getNode(getMapper().readTree(data), path) != null;
        }

        /** 按路径读取节点，不存在返回 null */
        static JsonNode getNode(JsonNode root, String path) {
            if (path == null || path.isEmpty()) return root;
            JsonNode cur = root;
            for (String seg : path.split("\\.")) {
                if (cur == null) return null;
                if (cur.isArray()) {
                    Integer idx = toInt(seg);
                    if (idx == null || idx < 0 || idx >= cur.size()) return null;
                    cur = cur.get(idx);
                } else if (cur.isObject()) {
                    cur = cur.get(seg);
                } else {
                    return null;
                }
            }
            return cur;
        }

        static byte[] setBytes(byte[] data, String path, Object value) throws Exception {
            JsonNode root = getMapper().readTree(data);
            JsonNode valNode = getMapper().valueToTree(value);
            JsonNode newRoot = setNode(root, path.split("\\."), 0, valNode);
            return getMapper().writeValueAsBytes(newRoot);
        }

        /** 写入 JsonNode 原值（用于已是 JsonNode 的场景，避免二次转换） */
        static byte[] setRawNode(byte[] data, String path, JsonNode valNode) throws Exception {
            JsonNode root = getMapper().readTree(data);
            JsonNode newRoot = setNode(root, path.split("\\."), 0, valNode);
            return getMapper().writeValueAsBytes(newRoot);
        }

        private static JsonNode setNode(JsonNode node, String[] segs, int i, JsonNode value) {
            if (i == segs.length) return value;
            String seg = segs[i];
            Integer idx = toInt(seg);
            if (idx != null && idx >= 0) {
                ArrayNode arr = (node instanceof ArrayNode) ? (ArrayNode) node : getMapper().createArrayNode();
                while (arr.size() <= idx) arr.addNull();
                JsonNode child = arr.get(idx);
                if (child != null && child.isNull()) child = null;
                arr.set(idx, setNode(child, segs, i + 1, value));
                return arr;
            }
            ObjectNode obj = (node instanceof ObjectNode) ? (ObjectNode) node : getMapper().createObjectNode();
            obj.set(seg, setNode(obj.get(seg), segs, i + 1, value));
            return obj;
        }

        static byte[] deleteBytes(byte[] data, String path) throws Exception {
            if (path == null || path.trim().isEmpty()) return data;
            JsonNode root = getMapper().readTree(data);
            String[] segs = path.split("\\.");
            JsonNode parent = root;
            for (int i = 0; i < segs.length - 1; i++) {
                if (parent.isArray()) {
                    Integer idx = toInt(segs[i]);
                    if (idx == null || idx < 0 || idx >= parent.size()) return data;
                    parent = parent.get(idx);
                } else if (parent.isObject()) {
                    parent = parent.get(segs[i]);
                } else {
                    return data;
                }
                if (parent == null) return data;
            }
            String last = segs[segs.length - 1];
            if (parent.isObject()) {
                ((ObjectNode) parent).remove(last);
            } else if (parent.isArray()) {
                Integer idx = toInt(last);
                if (idx != null && idx >= 0 && idx < parent.size()) {
                    ((ArrayNode) parent).remove(idx);
                }
            }
            return getMapper().writeValueAsBytes(root);
        }

        private static Integer toInt(String s) {
            if (s == null || s.isEmpty()) return null;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c < '0' || c > '9') {
                    if (i == 0 && c == '-' && s.length() > 1) continue;
                    return null;
                }
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    // ======================== JSON 路径操作 ======================== 
    /**
     * 处理路径中的负数索引。      * <p>
     * 把 {@code messages.-1.content} 中的 {@code -1} 根据对应数组实际长度替换为正数索引。
     */
    private static String processNegativeIndex(byte[] data, String path) throws Exception {
        if (path == null || !path.contains("-")) return path;
        java.util.regex.Matcher m = NEGATIVE_INDEX_REGEX.matcher(path);
        if (!m.find()) return path;

        JsonNode root = getMapper().readTree(data);
        String result = path;
        m.reset();
        while (m.find()) {
            String negIndex = m.group(1);       // 如 -1
            String matchAll = m.group();          // 如 .-1
            int index = Integer.parseInt(negIndex);
            // 数组路径 = 负数索引前的路径
            String arrayPath = path.split(java.util.regex.Pattern.quote(negIndex))[0];
            if (arrayPath.endsWith(".")) {
                arrayPath = arrayPath.substring(0, arrayPath.length() - 1);
            }
            JsonNode array = JsonPathOps.getNode(root, arrayPath);
            if (array != null && array.isArray()) {
                int actualIndex = array.size() + index;
                if (actualIndex >= 0 && actualIndex < array.size()) {
                    result = result.replaceFirst(java.util.regex.Pattern.quote(matchAll), "." + actualIndex);
                }
            }
        }
        return result;
    }

    /** 解析操作路径（处理通配符 *） */
    private static List<String> resolveOperationPaths(byte[] data, String path) throws Exception {
        if (path == null || !path.contains("*")) {
            return Collections.singletonList(path);
        }
        JsonNode root = getMapper().readTree(data);
        List<String> paths = new ArrayList<>();
        collectWildcardPaths(root, path.split("\\."), 0, new ArrayList<>(), paths);
        // 去重
        return new ArrayList<>(new LinkedHashSet<>(paths));
    }

    private static void collectWildcardPaths(JsonNode node, String[] segs, int i,
                                             List<String> prefix, List<String> out) {
        if (i >= segs.length) {
            out.add(String.join(".", prefix));
            return;
        }
        String seg = segs[i].trim();
        if (seg.isEmpty()) return;
        boolean isLast = i == segs.length - 1;

        if ("*".equals(seg)) {
            if (node != null && node.isObject()) {
                List<String> keys = new ArrayList<>();
                node.fieldNames().forEachRemaining(keys::add);
                Collections.sort(keys);
                for (String key : keys) {
                    prefix.add(key);
                    collectWildcardPaths(node.get(key), segs, i + 1, prefix, out);
                    prefix.remove(prefix.size() - 1);
                }
            } else if (node != null && node.isArray()) {
                for (int idx = 0; idx < node.size(); idx++) {
                    prefix.add(String.valueOf(idx));
                    collectWildcardPaths(node.get(idx), segs, i + 1, prefix, out);
                    prefix.remove(prefix.size() - 1);
                }
            }
            return;
        }

        if (node != null && node.isObject()) {
            if (isLast) {
                prefix.add(seg);
                out.add(String.join(".", prefix));
                prefix.remove(prefix.size() - 1);
                return;
            }
            JsonNode next = node.get(seg);
            if (next == null) return;
            prefix.add(seg);
            collectWildcardPaths(next, segs, i + 1, prefix, out);
            prefix.remove(prefix.size() - 1);
        } else if (node != null && node.isArray()) {
            Integer idx = JsonPathOps.toInt(seg);
            if (idx == null || idx < 0 || idx >= node.size()) return;
            if (isLast) {
                prefix.add(seg);
                out.add(String.join(".", prefix));
                prefix.remove(prefix.size() - 1);
                return;
            }
            prefix.add(seg);
            collectWildcardPaths(node.get(idx), segs, i + 1, prefix, out);
            prefix.remove(prefix.size() - 1);
        }
    }

    /** 移动值 */
    private static byte[] moveValue(byte[] data, String fromPath, String toPath) throws Exception {
        JsonNode root = getMapper().readTree(data);
        JsonNode source = JsonPathOps.getNode(root, fromPath);
        if (source == null) {
            throw new IllegalArgumentException("source path does not exist: " + fromPath);
        }
        byte[] result = JsonPathOps.setRawNode(data, toPath, source.deepCopy());
        return JsonPathOps.deleteBytes(result, fromPath);
    }

    /** 复制值 */
    private static byte[] copyValue(byte[] data, String fromPath, String toPath) throws Exception {
        JsonNode root = getMapper().readTree(data);
        JsonNode source = JsonPathOps.getNode(root, fromPath);
        if (source == null) {
            throw new IllegalArgumentException("source path does not exist: " + fromPath);
        }
        return JsonPathOps.setRawNode(data, toPath, source.deepCopy());
    }

    /** prepend/append 修改 */
    private static byte[] modifyValue(byte[] data, String path, Object value, boolean keepOrigin, boolean isPrepend) throws Exception {
        JsonNode root = getMapper().readTree(data);
        JsonNode current = JsonPathOps.getNode(root, path);
        if (current != null && current.isArray()) {
            return modifyArray(data, path, current, value, isPrepend);
        }
        if (current != null && current.isTextual()) {
            return modifyString(data, path, current.asText(), value, isPrepend);
        }
        if (current != null && current.isObject()) {
            return mergeObjects(data, path, current, value, keepOrigin);
        }
        throw new IllegalArgumentException("operation not supported for type at path: " + path);
    }

    private static byte[] modifyArray(byte[] data, String path, JsonNode current, Object value, boolean isPrepend) throws Exception {
        ArrayNode newArray = getMapper().createArrayNode();
        JsonNode valNode = getMapper().valueToTree(value);
        Runnable addValue = () -> {
            if (valNode.isArray()) {
                valNode.forEach(newArray::add);
            } else {
                newArray.add(valNode);
            }
        };
        Runnable addOriginal = () -> current.forEach(newArray::add);
        if (isPrepend) {
            addValue.run();
            addOriginal.run();
        } else {
            addOriginal.run();
            addValue.run();
        }
        return JsonPathOps.setRawNode(data, path, newArray);
    }

    private static byte[] modifyString(byte[] data, String path, String currentStr, Object value, boolean isPrepend) throws Exception {
        String valueStr = String.valueOf(value);
        String newStr = isPrepend ? valueStr + currentStr : currentStr + valueStr;
        return JsonPathOps.setBytes(data, path, newStr);
    }

    private static byte[] mergeObjects(byte[] data, String path, JsonNode current, Object value, boolean keepOrigin) throws Exception {
        ObjectNode result = getMapper().createObjectNode();
        result.setAll((ObjectNode) current.deepCopy());
        JsonNode newNode = getMapper().valueToTree(value);
        if (newNode.isObject()) {
            newNode.fields().forEachRemaining(e -> {
                if (!keepOrigin || result.get(e.getKey()) == null || result.get(e.getKey()).isNull()) {
                    result.set(e.getKey(), e.getValue());
                }
            });
        }
        return JsonPathOps.setRawNode(data, path, result);
    }

    /** trim_prefix / trim_suffix */
    private static byte[] trimStringValue(byte[] data, String path, Object value, boolean isPrefix) throws Exception {
        String current = requireStringAt(data, path);
        if (value == null) throw new IllegalArgumentException("trim value is required");
        String valueStr = String.valueOf(value);
        String newStr = isPrefix
                ? (current.startsWith(valueStr) ? current.substring(valueStr.length()) : current)
                : (current.endsWith(valueStr) ? current.substring(0, current.length() - valueStr.length()) : current);
        return JsonPathOps.setBytes(data, path, newStr);
    }

    /** ensure_prefix / ensure_suffix */
    private static byte[] ensureStringAffix(byte[] data, String path, Object value, boolean isPrefix) throws Exception {
        String current = requireStringAt(data, path);
        if (value == null) throw new IllegalArgumentException("ensure value is required");
        String valueStr = String.valueOf(value);
        if (valueStr.isEmpty()) throw new IllegalArgumentException("ensure value is required");
        if (isPrefix) {
            if (current.startsWith(valueStr)) return data;
            return JsonPathOps.setBytes(data, path, valueStr + current);
        }
        if (current.endsWith(valueStr)) return data;
        return JsonPathOps.setBytes(data, path, current + valueStr);
    }

    /** trim_space / to_lower / to_upper */
    private static byte[] transformStringValue(byte[] data, String path, java.util.function.UnaryOperator<String> transform) throws Exception {
        String current = requireStringAt(data, path);
        return JsonPathOps.setBytes(data, path, transform.apply(current));
    }

    /** replace */
    private static byte[] replaceStringValue(byte[] data, String path, String from, String to) throws Exception {
        String current = requireStringAt(data, path);
        if (from == null || from.isEmpty()) throw new IllegalArgumentException("replace from is required");
        return JsonPathOps.setBytes(data, path, current.replace(from, to == null ? "" : to));
    }

    /** regex_replace */
    private static byte[] regexReplaceStringValue(byte[] data, String path, String pattern, String replacement) throws Exception {
        String current = requireStringAt(data, path);
        if (pattern == null || pattern.isEmpty()) throw new IllegalArgumentException("regex pattern is required");
        Pattern re = Pattern.compile(pattern);
        return JsonPathOps.setBytes(data, path, re.matcher(current).replaceAll(replacement == null ? "" : replacement));
    }

    private static String requireStringAt(byte[] data, String path) throws Exception {
        JsonNode node = JsonPathOps.getNode(getMapper().readTree(data), path);
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException("operation not supported for non-string type at path: " + path);
        }
        return node.asText();
    }

    // ======================== prune_objects ======================== 
    /** prune_objects：递归删除满足条件的对象 */
    private static byte[] pruneObjects(byte[] data, String path, String contextJSON, Object value) throws Exception {
        PruneOptions options = parsePruneObjectsOptions(value);
        if (path == null || path.isEmpty()) {
            JsonNode root = getMapper().readTree(data);
            JsonNode cleaned = pruneObjectsNode(root, options, contextJSON, true);
            return getMapper().writeValueAsBytes(cleaned);
        }
        JsonNode root = getMapper().readTree(data);
        JsonNode target = JsonPathOps.getNode(root, path);
        if (target == null) return data;
        JsonNode cleaned = pruneObjectsNode(target, options, contextJSON, true);
        return JsonPathOps.setRawNode(data, path, cleaned);
    }

    private static final class PruneOptions {
        List<ConditionOperation> conditions = new ArrayList<>();
        String logic = "AND";
        boolean recursive = true;
    }

    @SuppressWarnings("unchecked")
    private static PruneOptions parsePruneObjectsOptions(Object value) {
        PruneOptions opts = new PruneOptions();
        if (value == null) {
            throw new IllegalArgumentException("prune_objects value is required");
        } else if (value instanceof String s) {
            String v = s.trim();
            if (v.isEmpty()) throw new IllegalArgumentException("prune_objects value is required");
            ConditionOperation cond = new ConditionOperation();
            cond.setPath("type");
            cond.setMode("full");
            cond.setValue(v);
            opts.conditions.add(cond);
        } else if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> raw = (Map<String, Object>) rawMap;
            if (raw.get("logic") instanceof String l && !l.trim().isEmpty()) opts.logic = l;
            if (raw.get("recursive") instanceof Boolean b) opts.recursive = b;
            if (raw.containsKey("conditions")) {
                opts.conditions.addAll(parseConditionOperationsFromRaw(raw.get("conditions")));
            }
            if (raw.get("where") instanceof Map<?, ?> whereRaw) {
                ((Map<String, Object>) whereRaw).forEach((k, v) -> {
                    if (k != null && !k.trim().isEmpty()) {
                        ConditionOperation cond = new ConditionOperation();
                        cond.setPath(k.trim());
                        cond.setMode("full");
                        cond.setValue(v);
                        opts.conditions.add(cond);
                    }
                });
            }
            if (raw.containsKey("type")) {
                ConditionOperation cond = new ConditionOperation();
                cond.setPath("type");
                cond.setMode("full");
                cond.setValue(raw.get("type"));
                opts.conditions.add(cond);
            }
        } else {
            throw new IllegalArgumentException("prune_objects value must be string or object");
        }
        if (opts.conditions.isEmpty()) {
            throw new IllegalArgumentException("prune_objects conditions are required");
        }
        return opts;
    }

    /** 返回清理后的节点（被删除时由父级跳过） */
    private static JsonNode pruneObjectsNode(JsonNode node, PruneOptions options, String contextJSON, boolean isRoot) throws Exception {
        if (node.isArray()) {
            ArrayNode result = getMapper().createArrayNode();
            for (JsonNode item : node) {
                if (item.isObject() && !pruneShouldKeep(item, options, contextJSON)) {
                    continue;
                }
                result.add(pruneObjectsNode(item, options, contextJSON, false));
            }
            return result;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (!options.recursive) return obj;
            ObjectNode result = getMapper().createObjectNode();
            java.util.Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                JsonNode child = e.getValue();
                if (child.isObject() && !pruneShouldKeep(child, options, contextJSON)) {
                    continue;
                }
                result.set(e.getKey(), pruneObjectsNode(child, options, contextJSON, false));
            }
            return result;
        }
        return node;
    }

    private static boolean pruneShouldKeep(JsonNode obj, PruneOptions options, String contextJSON) throws Exception {
        byte[] nodeBytes = getMapper().writeValueAsBytes(obj);
        // 满足条件 = 应被删除 → 返回 false 表示不保留
        return !checkConditions(nodeBytes, contextJSON, options.conditions, options.logic);
    }

    /** 解析 conditions（对象或数组） */
    @SuppressWarnings("unchecked")
    private static List<ConditionOperation> parseConditionOperationsFromRaw(Object raw) {
        List<ConditionOperation> result = new ArrayList<>();
        if (raw instanceof Map<?, ?> mapRaw) {
            ((Map<String, Object>) mapRaw).forEach((k, v) -> {
                if (k != null && !k.trim().isEmpty()) {
                    ConditionOperation cond = new ConditionOperation();
                    cond.setPath(k.trim());
                    cond.setMode("full");
                    cond.setValue(v);
                    result.add(cond);
                }
            });
            if (result.isEmpty()) throw new IllegalArgumentException("conditions object must contain at least one key");
        } else if (raw instanceof List<?> listRaw) {
            for (Object item : listRaw) {
                if (!(item instanceof Map)) throw new IllegalArgumentException("condition must be object");
                Map<String, Object> m = (Map<String, Object>) item;
                String path = Objects.toString(m.get("path"), "").trim();
                String mode = Objects.toString(m.get("mode"), "").trim();
                if (path.isEmpty() || mode.isEmpty()) throw new IllegalArgumentException("condition path/mode is required");
                ConditionOperation cond = new ConditionOperation();
                cond.setPath(path);
                cond.setMode(mode);
                if (m.containsKey("value")) cond.setValue(m.get("value"));
                if (m.get("invert") instanceof Boolean b) cond.setInvert(b);
                if (m.get("pass_missing_key") instanceof Boolean b2) cond.setPassMissingKey(b2);
                result.add(cond);
            }
        } else {
            throw new IllegalArgumentException("conditions must be an array or object");
        }
        return result;
    }

    // ======================== Header 上下文操作 ======================== 
    @SuppressWarnings("unchecked")
    private static Map<String, Object> ensureMapKeyInContext(Map<String, Object> context, String key) {
        if (context == null) return new LinkedHashMap<>();
        Object existing = context.get(key);
        if (existing instanceof Map) {
            return (Map<String, Object>) existing;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        context.put(key, result);
        return result;
    }

    private static String normalizeHeaderContextKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static String getHeaderValueFromContext(Map<String, Object> context, String headerName) {
        String key = normalizeHeaderContextKey(headerName);
        if (key.isEmpty()) return null;
        for (String ctxKey : new String[]{PARAM_OVERRIDE_CONTEXT_HEADER_OVERRIDE, PARAM_OVERRIDE_CONTEXT_REQUEST_HEADERS}) {
            Map<String, Object> source = ensureMapKeyInContext(context, ctxKey);
            Object raw = source.get(key);
            if (raw == null) continue;
            String value = String.valueOf(raw).trim();
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    private static void setHeaderOverrideInContext(Map<String, Object> context, String headerName, Object value, boolean keepOrigin) {
        String key = normalizeHeaderContextKey(headerName);
        if (key.isEmpty()) throw new IllegalArgumentException("header name is required");
        Map<String, Object> rawHeaders = ensureMapKeyInContext(context, PARAM_OVERRIDE_CONTEXT_HEADER_OVERRIDE);
        if (keepOrigin) {
            Object existing = rawHeaders.get(key);
            if (existing != null && !String.valueOf(existing).trim().isEmpty()) {
                return;
            }
        }
        if (value == null) throw new IllegalArgumentException("header value is required");
        String headerValue = String.valueOf(value).trim();
        if (headerValue.isEmpty()) {
            rawHeaders.remove(key);
            return;
        }
        rawHeaders.put(key, headerValue);
    }

    private static void deleteHeaderOverrideInContext(Map<String, Object> context, String headerName) {
        String key = normalizeHeaderContextKey(headerName);
        if (key.isEmpty()) throw new IllegalArgumentException("header name is required");
        ensureMapKeyInContext(context, PARAM_OVERRIDE_CONTEXT_HEADER_OVERRIDE).remove(key);
    }

    private static void copyHeaderInContext(Map<String, Object> context, String fromHeader, String toHeader, boolean keepOrigin) {
        String from = normalizeHeaderContextKey(fromHeader);
        String to = normalizeHeaderContextKey(toHeader);
        if (from.isEmpty() || to.isEmpty()) throw new IllegalArgumentException("copy_header from/to is required");
        String value = getHeaderValueFromContext(context, from);
        if (value == null) {
            // 源头不存在
            return;
        }
        setHeaderOverrideInContext(context, to, value, keepOrigin);
    }

    private static void moveHeaderInContext(Map<String, Object> context, String fromHeader, String toHeader, boolean keepOrigin) {
        String from = normalizeHeaderContextKey(fromHeader);
        String to = normalizeHeaderContextKey(toHeader);
        if (from.isEmpty() || to.isEmpty()) throw new IllegalArgumentException("move_header from/to is required");
        String value = getHeaderValueFromContext(context, from);
        if (value == null) return; // 源头不存在
        setHeaderOverrideInContext(context, to, value, keepOrigin);
        if (!from.equalsIgnoreCase(to)) {
            deleteHeaderOverrideInContext(context, from);
        }
    }

    /** 解析 pass_headers 头名称列表 */
    @SuppressWarnings("unchecked")
    private static List<String> parseHeaderPassThroughNames(Object value) throws Exception {
        if (value == null) throw new IllegalArgumentException("pass_headers value is required");
        List<String> names = new ArrayList<>();
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("pass_headers value is required");
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                Object parsed = getMapper().readValue(trimmed, Object.class);
                return parseHeaderPassThroughNames(parsed);
            }
            for (String part : trimmed.split(",")) {
                String n = normalizeHeaderContextKey(part);
                if (!n.isEmpty() && !names.contains(n)) names.add(n);
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) {
                String n = normalizeHeaderContextKey(String.valueOf(item));
                if (!n.isEmpty() && !names.contains(n)) names.add(n);
            }
        } else if (value instanceof Map<?, ?> mapRaw) {
            Map<String, Object> raw = (Map<String, Object>) mapRaw;
            for (String field : new String[]{"headers", "names", "header"}) {
                if (raw.containsKey(field)) {
                    for (String n : parseHeaderPassThroughNames(raw.get(field))) {
                        if (!names.contains(n)) names.add(n);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("pass_headers value must be string, array or object");
        }
        if (names.isEmpty()) throw new IllegalArgumentException("pass_headers value is invalid");
        return names;
    }

    // ======================== sync_fields ======================== 
    private record SyncTarget(String kind, String key) {
    }

    private static SyncTarget parseSyncTarget(String spec) {
        String raw = spec == null ? "" : spec.trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("sync_fields target is required");
        int idx = raw.indexOf(':');
        if (idx < 0) {
            return new SyncTarget("json", raw);
        }
        String kind = raw.substring(0, idx).trim().toLowerCase(Locale.ROOT);
        String key = raw.substring(idx + 1).trim();
        if (key.isEmpty()) throw new IllegalArgumentException("sync_fields target key is required: " + raw);
        return switch (kind) {
            case "json", "body" -> new SyncTarget("json", key);
            case "header" -> new SyncTarget("header", key);
            default -> throw new IllegalArgumentException("sync_fields target prefix is invalid: " + raw);
        };
    }

    /** sync_fields：双向同步缺失的字段 */
    private static byte[] syncFieldsBetweenTargets(byte[] data, Map<String, Object> context, String fromSpec, String toSpec) throws Exception {
        SyncTarget fromTarget = parseSyncTarget(fromSpec);
        SyncTarget toTarget = parseSyncTarget(toSpec);

        JsonNode fromValue = readSyncTargetValue(data, context, fromTarget);
        JsonNode toValue = readSyncTargetValue(data, context, toTarget);

        if (fromValue != null && toValue == null) {
            return writeSyncTargetValue(data, context, toTarget, fromValue);
        }
        if (toValue != null && fromValue == null) {
            return writeSyncTargetValue(data, context, fromTarget, toValue);
        }
        return data;
    }

    /** 读取同步目标值，存在且非空返回节点，否则 null */
    private static JsonNode readSyncTargetValue(byte[] data, Map<String, Object> context, SyncTarget target) throws Exception {
        if ("json".equals(target.kind())) {
            String path = processNegativeIndex(data, target.key());
            JsonNode node = JsonPathOps.getNode(getMapper().readTree(data), path);
            if (node == null || node.isNull()) return null;
            if (node.isTextual() && node.asText().trim().isEmpty()) return null;
            return node;
        }
        // header
        String value = getHeaderValueFromContext(context, target.key());
        if (value == null || value.trim().isEmpty()) return null;
        return getMapper().valueToTree(value);
    }

    private static byte[] writeSyncTargetValue(byte[] data, Map<String, Object> context, SyncTarget target, JsonNode value) throws Exception {
        if ("json".equals(target.kind())) {
            String path = processNegativeIndex(data, target.key());
            return JsonPathOps.setRawNode(data, path, value);
        }
        // header
        setHeaderOverrideInContext(context, target.key(), value.isTextual() ? value.asText() : value.toString(), false);
        return data;
    }

    /**
     * 检查条件列表      */
    private static boolean checkConditions(byte[] data, String contextJSON,
                                           List<ConditionOperation> conditions, String logic) throws Exception {
        if (conditions.isEmpty()) return true;

        List<Boolean> results = new ArrayList<>();
        for (ConditionOperation condition : conditions) {
            results.add(checkSingleCondition(data, contextJSON, condition));
        }

        if ("AND".equalsIgnoreCase(logic)) {
            return results.stream().allMatch(Boolean::booleanValue);
        }
        return results.stream().anyMatch(Boolean::booleanValue);
    }

    /**
     * 检查单个条件。      * <p>
     * 先在请求体按 JSON 路径（支持负数索引）取值，取不到时回退到 contextJSON。
     */
    private static boolean checkSingleCondition(byte[] data, String contextJSON,
                                                 ConditionOperation condition) throws Exception {
        // 处理负数索引后按路径取值
        String path = processNegativeIndex(data, condition.getPath());
        JsonNode valueNode = JsonPathOps.getNode(getMapper().readTree(data), path);

        if (valueNode == null && contextJSON != null && !contextJSON.isEmpty()) {
            valueNode = JsonPathOps.getNode(getMapper().readTree(contextJSON), condition.getPath());
        }

        if (valueNode == null) {
            return condition.isPassMissingKey();
        }

        JsonNode targetNode = getMapper().valueToTree(condition.getValue());
        boolean result = compareJsonValues(valueNode, targetNode, condition.getMode().toLowerCase(Locale.ROOT));

        return condition.isInvert() != result;
    }

    /**
     * 比较两个 JsonNode      */
    private static boolean compareJsonValues(JsonNode valueNode, JsonNode targetNode, String mode) throws Exception {
        switch (mode.toLowerCase()) {
            case "full" -> {
                return compareEqual(valueNode, targetNode);
            }
            case "prefix" -> {
                return valueNode.asText().startsWith(targetNode.asText());
            }
            case "suffix" -> {
                return valueNode.asText().endsWith(targetNode.asText());
            }
            case "contains" -> {
                return valueNode.asText().contains(targetNode.asText());
            }
            case "gt" -> {
                return compareNumeric(valueNode, targetNode) > 0;
            }
            case "gte" -> {
                return compareNumeric(valueNode, targetNode) >= 0;
            }
            case "lt" -> {
                return compareNumeric(valueNode, targetNode) < 0;
            }
            case "lte" -> {
                return compareNumeric(valueNode, targetNode) <= 0;
            }
            default -> throw new IllegalArgumentException("Unsupported comparison mode: " + mode);
        }
    }

    private static int compareNumeric(JsonNode a, JsonNode b) {
        if (!a.isNumber() || !b.isNumber()) {
            throw new IllegalArgumentException("Numeric comparison requires both values to be numbers");
        }
        return Double.compare(a.asDouble(), b.asDouble());
    }

    /**
     * 全等比较。      * <p>
     * null 特殊处理：两者皆 null 为 true；布尔值按布尔比较；类型不一致报错。
     */
    private static boolean compareEqual(JsonNode jsonValue, JsonNode targetValue) {
        boolean jsonNull = jsonValue == null || jsonValue.isNull();
        boolean targetNull = targetValue == null || targetValue.isNull();
        if (jsonNull || targetNull) {
            return jsonNull && targetNull;
        }
        if (jsonValue.isBoolean() && targetValue.isBoolean()) {
            return jsonValue.asBoolean() == targetValue.asBoolean();
        }
        if (jsonValue.isNumber() && targetValue.isNumber()) {
            return jsonValue.asDouble() == targetValue.asDouble();
        }
        if (jsonValue.isTextual() && targetValue.isTextual()) {
            return jsonValue.asText().equals(targetValue.asText());
        }
        // 其余类型按节点深度相等比较
        return jsonValue.equals(targetValue);
    }

    // ======================== 审计 ========================

    private static boolean shouldEnableParamOverrideAudit(Map<String, Object> paramOverride) {
        if (yaoshu.token.constant.CommonConstants.debugEnabled) {
            return true;
        }
        if (paramOverride == null || paramOverride.isEmpty()) return false;
        List<ParamOperation> operations = tryParseOperations(paramOverride);
        if (operations != null) {
            for (ParamOperation op : operations) {
                if (shouldAuditParamPath(op.getPath()) || shouldAuditParamPath(op.getFrom()) || shouldAuditParamPath(op.getTo())) {
                    return true;
                }
            }
            for (String key : buildLegacyParamOverride(paramOverride).keySet()) {
                if (shouldAuditParamPath(key)) return true;
            }
            return false;
        }
        for (String key : paramOverride.keySet()) {
            if (shouldAuditParamPath(key)) return true;
        }
        return false;
    }

    private static boolean shouldAuditParamPath(String path) {
        if (path == null) return false;
        if (yaoshu.token.constant.CommonConstants.debugEnabled) return true;
        path = path.trim();
        if (path.isEmpty()) return false;
        for (String prefix : SENSITIVE_PATH_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    private static String buildAuditLine(String mode, String path, String from, String to, Object value) {
        mode = mode != null ? mode.trim() : "";
        path = path != null ? path.trim() : "";

        switch (mode) {
            case "set" -> { return path.isEmpty() ? null : "set " + path + " = " + formatAuditValue(value); }
            case "delete" -> { return path.isEmpty() ? null : "delete " + path; }
            case "copy" -> { return "copy " + from + " -> " + to; }
            case "move" -> { return "move " + from + " -> " + to; }
            case "return_error" -> { return "return_error " + formatAuditValue(value); }
            default -> { return path.isEmpty() ? mode : mode + " " + path; }
        }
    }

    private static String formatAuditValue(Object value) {
        if (value == null) return "<empty>";
        if (value instanceof String) return (String) value;
        try {
            return getMapper().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    // ======================== RelayInfo 上下文 ========================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getParamOverrideMap(RelayInfo info) {
        if (info == null) return Collections.emptyMap();
        return info.getParamOverride() != null ? info.getParamOverride() : Collections.emptyMap();
    }

    /**
     * 构建参数覆写上下文。      * <p>
     * 内置字段：model、upstream_model、original_model、request_path、request_headers、
     * header_override、retry_index、is_retry、retry、last_error 系列、is_channel_test。
     */
    private static Map<String, Object> buildParamOverrideContext(RelayInfo info) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (info == null) return ctx;

        // 模型名：上游模型优先，回退到原始模型
        String upstreamModel = info.getUpstreamModelName();
        if (upstreamModel != null && !upstreamModel.isEmpty()) {
            ctx.put("model", upstreamModel);
            ctx.put("upstream_model", upstreamModel);
        }
        String originModel = info.getOriginModelName();
        if (originModel != null && !originModel.isEmpty()) {
            ctx.put("original_model", originModel);
            ctx.putIfAbsent("model", originModel);
        }

        if (info.getRequestURLPath() != null && !info.getRequestURLPath().isEmpty()) {
            ctx.put("request_path", info.getRequestURLPath());
        }

        // 请求头上下文（小写键名）
        ctx.put(PARAM_OVERRIDE_CONTEXT_REQUEST_HEADERS, buildRequestHeadersContext(info.getRequestHeaders()));

        // 生效的 header override
        ctx.put(PARAM_OVERRIDE_CONTEXT_HEADER_OVERRIDE, getEffectiveHeaderOverride(info));

        // 重试上下文
        int retryIndex = info.getRetryIndex();
        ctx.put("retry_index", retryIndex);
        ctx.put("is_retry", retryIndex > 0);
        Map<String, Object> retry = new LinkedHashMap<>();
        retry.put("index", retryIndex);
        retry.put("is_retry", retryIndex > 0);
        ctx.put("retry", retry);

        // 上次错误上下文
        RelayException lastError = info.getLastError();
        if (lastError != null) {
            String code = lastError.getErrorCode();
            String errorType = lastError.getErrorType();
            Map<String, Object> le = new LinkedHashMap<>();
            le.put("status_code", lastError.getStatusCode());
            le.put("message", lastError.getMessage());
            le.put("code", code);
            le.put("error_code", code);
            le.put("type", errorType);
            le.put("error_type", errorType);
            le.put("skip_retry", lastError.isSkipRetry());
            ctx.put("last_error", le);
            ctx.put("last_error_status_code", lastError.getStatusCode());
            ctx.put("last_error_message", lastError.getMessage());
            ctx.put("last_error_code", code);
            ctx.put("last_error_type", errorType);
        }

        ctx.put("is_channel_test", info.isChannelTest());
        return ctx;
    }

    /** 构建小写键名的请求头上下文 */
    private static Map<String, Object> buildRequestHeadersContext(Map<String, String> headers) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (headers == null || headers.isEmpty()) return result;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String key = normalizeHeaderContextKey(e.getKey());
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (key.isEmpty() || value.isEmpty()) continue;
            result.put(key, value);
        }
        return result;
    }

    /** 获取生效的 header override */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> getEffectiveHeaderOverride(RelayInfo info) {
        if (info == null) return new LinkedHashMap<>();
        Map<String, Object> source = info.isUseRuntimeHeadersOverride()
                ? info.getRuntimeHeadersOverride()
                : info.getHeadersOverride();
        return sanitizeHeaderOverrideMap(source);
    }

    /** 规范化 header override map（键名小写、空值剔除） */
    private static Map<String, Object> sanitizeHeaderOverrideMap(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) return target;
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = normalizeHeaderContextKey(e.getKey());
            if (key.isEmpty()) continue;
            String value = e.getValue() == null ? "" : String.valueOf(e.getValue()).trim();
            if (value.isEmpty()) {
                // 透传规则 key（* / re: / regex: 前缀）允许保留空值
                if (isHeaderPassthroughRuleKey(key)) {
                    target.put(key, "");
                }
                continue;
            }
            target.put(key, value);
        }
        return target;
    }

    private static boolean isHeaderPassthroughRuleKey(String key) {
        if (key == null) return false;
        key = key.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return false;
        return "*".equals(key) || key.startsWith("re:") || key.startsWith("regex:");
    }

    /**
     * 从上下文同步 Runtime Header Override 回 RelayInfo      */
    @SuppressWarnings("unchecked")
    static void syncRuntimeHeaderOverrideFromContext(RelayInfo info, Map<String, Object> ctx) {
        if (info == null || ctx == null) return;
        Object headerOverride = ctx.get(PARAM_OVERRIDE_CONTEXT_HEADER_OVERRIDE);
        if (headerOverride instanceof Map) {
            info.setRuntimeHeadersOverride((Map<String, Object>) headerOverride);
            info.setUseRuntimeHeadersOverride(true);
        }
    }
}
