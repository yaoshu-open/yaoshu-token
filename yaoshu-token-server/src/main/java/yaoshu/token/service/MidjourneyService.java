package yaoshu.token.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import ai.yue.library.web.util.ServletUtils;
import yaoshu.token.constant.MidjourneyConstants;
import yaoshu.token.mapper.MidjourneyMapper;
import yaoshu.token.pojo.dto.MidjourneyDTO;
import yaoshu.token.pojo.entity.Midjourney;
import yaoshu.token.relay.constant.RelayModeEnum;

import java.util.Collections;
import java.util.List;

/**
 * Midjourney 业务服务  * <p>
 * 核心职责：MJ 任务 CRUD、action→model 名称映射、任务状态管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MidjourneyService {

    private final MidjourneyMapper midjourneyMapper;

    /**
     * 将 MJ 动作名转换为模型名      * <p>
     * 例如 IMAGINE → mj_imagine, UPSCALE → mj_upscale
     */
    public static String covertMjpActionToModelName(String mjAction) {
        if (mjAction == null) return "mj_unknown";
        if (MidjourneyConstants.MJ_ACTION_SWAP_FACE.equals(mjAction)) {
            return "swap_face";
        }
        return "mj_" + mjAction.toLowerCase();
    }

    /**
     * MJ 请求模型解析结果。      *
     * @param modelName  解析出的模型名（fetch/notify 等无需选渠道的模式为空）
     * @param errorDesc  错误描述（非 null 表示解析失败）
     * @param success    是否解析成功（true 时即便 modelName 为空也表示无需选渠道）
     */
    public record MjRequestModel(String modelName, String errorDesc, boolean success) {
    }

    /**
     * 根据 relayMode 与请求体解析 MJ 模型名。      */
    public static MjRequestModel getMjRequestModel(int relayMode, MidjourneyDTO.MidjourneyRequest req) {
        String action;
        if (relayMode == RelayModeEnum.MIDJOURNEY_ACTION) {
            // plus 请求：从 customId 还原标准 action
            String err = coverPlusActionToNormalAction(req);
            if (err != null) {
                return new MjRequestModel("", err, false);
            }
            action = req.getAction();
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_IMAGINE) {
            action = MidjourneyConstants.MJ_ACTION_IMAGINE;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_VIDEO) {
            action = MidjourneyConstants.MJ_ACTION_VIDEO;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_EDITS) {
            action = MidjourneyConstants.MJ_ACTION_EDITS;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_DESCRIBE) {
            action = MidjourneyConstants.MJ_ACTION_DESCRIBE;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_BLEND) {
            action = MidjourneyConstants.MJ_ACTION_BLEND;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_SHORTEN) {
            action = MidjourneyConstants.MJ_ACTION_SHORTEN;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_CHANGE) {
            action = req.getAction();
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_MODAL) {
            action = MidjourneyConstants.MJ_ACTION_MODAL;
        } else if (relayMode == RelayModeEnum.SWAP_FACE) {
            action = MidjourneyConstants.MJ_ACTION_SWAP_FACE;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_UPLOAD) {
            action = MidjourneyConstants.MJ_ACTION_UPLOAD;
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_SIMPLE_CHANGE) {
            MidjourneyDTO.MidjourneyRequest params = convertSimpleChangeParams(req.getContent());
            if (params == null) {
                return new MjRequestModel("", "invalid_request", false);
            }
            action = params.getAction();
        } else if (relayMode == RelayModeEnum.MIDJOURNEY_TASK_FETCH
                || relayMode == RelayModeEnum.MIDJOURNEY_TASK_FETCH_BY_CONDITION
                || relayMode == RelayModeEnum.MIDJOURNEY_NOTIFY) {
            // 查询/通知类请求无需选渠道
            return new MjRequestModel("", null, true);
        } else {
            return new MjRequestModel("", "unknown_relay_action", false);
        }
        return new MjRequestModel(covertMjpActionToModelName(action), null, true);
    }

    /**
     * 将 plus action 的 customId 还原为标准 action。      * <p>
     * customId 形如 {@code MJ::JOB::upsample::2::xxxx}，解析后回填 req.action / req.index。
     *
     * @return 错误描述，成功返回 null
     */
    public static String coverPlusActionToNormalAction(MidjourneyDTO.MidjourneyRequest req) {
        String customId = req.getCustomId();
        if (customId == null || customId.isEmpty()) {
            return "custom_id_is_required";
        }
        String[] splits = customId.split("::");
        String action;
        if (splits.length > 2 && "JOB".equals(splits[1])) {
            action = splits[2];
        } else if (splits.length > 1) {
            action = splits[1];
        } else {
            return "unknown_action";
        }
        if (action.isEmpty()) {
            return "unknown_action";
        }

        if (action.contains("upsample")) {
            Integer index = parseIndex(splits, 3);
            if (index == null) return "index_parse_failed";
            req.setIndex(index);
            req.setAction(MidjourneyConstants.MJ_ACTION_UPSCALE);
        } else if (action.contains("variation")) {
            req.setIndex(1);
            if ("variation".equals(action)) {
                Integer index = parseIndex(splits, 3);
                if (index == null) return "index_parse_failed";
                req.setIndex(index);
                req.setAction(MidjourneyConstants.MJ_ACTION_VARIATION);
            } else if ("low_variation".equals(action)) {
                req.setAction(MidjourneyConstants.MJ_ACTION_LOW_VARIATION);
            } else if ("high_variation".equals(action)) {
                req.setAction(MidjourneyConstants.MJ_ACTION_HIGH_VARIATION);
            }
        } else if (action.contains("pan")) {
            req.setAction(MidjourneyConstants.MJ_ACTION_PAN);
            req.setIndex(1);
        } else if (action.contains("reroll")) {
            req.setAction(MidjourneyConstants.MJ_ACTION_REROLL);
            req.setIndex(1);
        } else if ("Outpaint".equals(action)) {
            req.setAction(MidjourneyConstants.MJ_ACTION_ZOOM);
            req.setIndex(1);
        } else if ("CustomZoom".equals(action)) {
            req.setAction(MidjourneyConstants.MJ_ACTION_CUSTOM_ZOOM);
            req.setIndex(1);
        } else if ("Inpaint".equals(action)) {
            req.setAction(MidjourneyConstants.MJ_ACTION_INPAINT);
            req.setIndex(1);
        } else {
            return "unknown_action:" + customId;
        }
        return null;
    }

    private static Integer parseIndex(String[] splits, int idx) {
        if (splits.length <= idx) return null;
        try {
            return Integer.parseInt(splits[idx]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析 simple-change 的 content 参数。      * <p>
     * content 形如 {@code <taskId> u1}（u=upscale/v=variation/r=reroll + 索引 1~4）。
     */
    public static MidjourneyDTO.MidjourneyRequest convertSimpleChangeParams(String content) {
        if (content == null) return null;
        String[] split = content.split(" ");
        if (split.length != 2) return null;

        String action = split[1].toLowerCase();
        if (action.isEmpty()) return null;
        MidjourneyDTO.MidjourneyRequest params = new MidjourneyDTO.MidjourneyRequest();
        params.setTaskId(split[0]);

        char first = action.charAt(0);
        if (first == 'u') {
            params.setAction("UPSCALE");
        } else if (first == 'v') {
            params.setAction("VARIATION");
        } else if ("r".equals(action)) {
            params.setAction("REROLL");
            return params;
        } else {
            return null;
        }

        if (action.length() < 2) return null;
        int index;
        try {
            index = Integer.parseInt(action.substring(1, 2));
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 1 || index > 4) return null;
        params.setIndex(index);
        return params;
    }

    /**
     * 通过 mjId 获取 MJ 任务记录      */
    public Midjourney getByOnlyMJId(String mjId) {
        if (mjId == null || mjId.isEmpty()) return null;
        return midjourneyMapper.selectOne(
                new LambdaQueryWrapper<Midjourney>()
                        .eq(Midjourney::getMjId, mjId)
                        .last("LIMIT 1"));
    }

    /**
     * 按 user_id + mj_id 获取 MJ 任务记录      */
    public Midjourney getByMJId(Integer userId, String mjId) {
        if (userId == null || mjId == null || mjId.isEmpty()) return null;
        return midjourneyMapper.selectOne(
                new LambdaQueryWrapper<Midjourney>()
                        .eq(Midjourney::getUserId, userId)
                        .eq(Midjourney::getMjId, mjId)
                        .last("LIMIT 1"));
    }

    /**
     * 按 user_id + 多个 mj_id 获取 MJ 任务记录      */
    public List<Midjourney> getByMJIds(Integer userId, List<String> mjIds) {
        if (userId == null || mjIds == null || mjIds.isEmpty()) return Collections.emptyList();
        return midjourneyMapper.selectList(
                new LambdaQueryWrapper<Midjourney>()
                        .eq(Midjourney::getUserId, userId)
                        .in(Midjourney::getMjId, mjIds));
    }

    /**
     * 获取所有未完成的任务      */
    public List<Midjourney> getAllUnFinishTasks() {
        return midjourneyMapper.selectList(
                new LambdaQueryWrapper<Midjourney>()
                        .in(Midjourney::getStatus, "NOT_START", "SUBMITTED", "IN_PROGRESS", "MODAL"));
    }

    /**
     * 查询用户的所有 MJ 任务（分页由业务层控制）      */
    public List<Midjourney> getAllUserTask(Integer userId,
                                            String mjId, Long startTimestamp, Long endTimestamp) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Midjourney> qw = new LambdaQueryWrapper<Midjourney>()
                .eq(Midjourney::getUserId, userId)
                .orderByDesc(Midjourney::getId);

        if (mjId != null && !mjId.isEmpty()) {
            qw.eq(Midjourney::getMjId, mjId);
        }
        if (startTimestamp != null) {
            qw.ge(Midjourney::getSubmitTime, startTimestamp);
        }
        if (endTimestamp != null) {
            qw.le(Midjourney::getSubmitTime, endTimestamp);
        }
        return midjourneyMapper.selectList(qw);
    }

    /**
     * 管理员查询全部 MJ 任务（分页）      * <p>
     * 支持按 channel_id / mj_id / 时间区间过滤。
     */
    public List<Midjourney> getAllTasks(Integer channelId, String mjId,
                                         Long startTimestamp, Long endTimestamp) {
        PageHelper.startPage(ServletUtils.getRequest());
        LambdaQueryWrapper<Midjourney> qw = new LambdaQueryWrapper<Midjourney>()
                .orderByDesc(Midjourney::getId);

        if (channelId != null && channelId > 0) {
            qw.eq(Midjourney::getChannelId, channelId);
        }
        if (mjId != null && !mjId.isEmpty()) {
            qw.eq(Midjourney::getMjId, mjId);
        }
        if (startTimestamp != null) {
            qw.ge(Midjourney::getSubmitTime, startTimestamp);
        }
        if (endTimestamp != null) {
            qw.le(Midjourney::getSubmitTime, endTimestamp);
        }
        return midjourneyMapper.selectList(qw);
    }

    /**
     * 批量更新 MJ 任务      */
    public void bulkUpdate(List<Integer> taskIds, String status, String progress, String failReason) {
        if (taskIds == null || taskIds.isEmpty()) return;
        Midjourney update = new Midjourney();
        update.setStatus(status);
        update.setProgress(progress);
        update.setFailReason(failReason);
        for (Integer taskId : taskIds) {
            update.setId(taskId);
            midjourneyMapper.updateById(update);
        }
    }

    /**
     * 保存 MJ 任务      */
    public void saveTask(Midjourney task) {
        midjourneyMapper.insert(task);
    }

    /**
     * 更新 MJ 任务      */
    public void updateTask(Midjourney task) {
        midjourneyMapper.updateById(task);
    }
}
