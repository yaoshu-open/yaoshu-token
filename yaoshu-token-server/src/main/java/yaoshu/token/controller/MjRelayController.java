package yaoshu.token.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.dto.MidjourneyDTO;
import yaoshu.token.relay.constant.RelayModeEnum;
import yaoshu.token.relay.handler.MidjourneyHandler;

import java.io.IOException;
import java.util.Map;

/**
 * Midjourney 中转控制器  * <p>
 * 认证：TokenAuth（全部）+ Distribute
 */
@RestController
@RequiredArgsConstructor
public class MjRelayController {

    private final MidjourneyHandler midjourneyHandler;

    @GetMapping("/mj/image/{id}")
    public void getImage(@PathVariable String id, HttpServletResponse response) throws IOException {
        midjourneyHandler.relayMidjourneyImage(id, response);
    }

    @PostMapping("/mj/notify")
    public Map<String, Object> notify(@RequestBody MidjourneyDTO.MidjourneyTaskDTO body) {
        Map<String, Object> result = midjourneyHandler.relayMidjourneyNotify(body);
        return result != null ? result : Map.of("code", 1, "description", "success");
    }

    @PostMapping("/mj/submit/action")
    public Object submitAction(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_ACTION);
    }

    @PostMapping("/mj/submit/shorten")
    public Object submitShorten(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_SHORTEN);
    }

    @PostMapping("/mj/submit/modal")
    public Object submitModal(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_MODAL);
    }

    @PostMapping("/mj/submit/imagine")
    public Object submitImagine(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_IMAGINE);
    }

    @PostMapping("/mj/submit/change")
    public Object submitChange(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_CHANGE);
    }

    @PostMapping("/mj/submit/simple-change")
    public Object submitSimpleChange(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_SIMPLE_CHANGE);
    }

    @PostMapping("/mj/submit/describe")
    public Object submitDescribe(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_DESCRIBE);
    }

    @PostMapping("/mj/submit/blend")
    public Object submitBlend(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_BLEND);
    }

    @PostMapping("/mj/submit/edits")
    public Object submitEdits(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_EDITS);
    }

    @PostMapping("/mj/submit/video")
    public Object submitVideo(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_VIDEO);
    }

    @GetMapping("/mj/task/{id}/fetch")
    public Object taskFetch(@PathVariable String id, jakarta.servlet.http.HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneyTaskFetch(request, id);
    }

    @GetMapping("/mj/task/{id}/image-seed")
    public Object taskImageSeed(@PathVariable String id, jakarta.servlet.http.HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneyTaskImageSeed(request, id);
    }

    @PostMapping("/mj/task/list-by-condition")
    public Object taskListByCondition(@RequestBody Map<String, Object> body, jakarta.servlet.http.HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneyTaskListByCondition(request, body);
    }

    @PostMapping("/mj/insight-face/swap")
    public Object insightFaceSwap(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relaySwapFace(request, body);
    }

    @PostMapping("/mj/submit/upload-discord-images")
    public Object uploadDiscordImages(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return midjourneyHandler.relayMidjourneySubmit(request, body, RelayModeEnum.MIDJOURNEY_UPLOAD);
    }
}
