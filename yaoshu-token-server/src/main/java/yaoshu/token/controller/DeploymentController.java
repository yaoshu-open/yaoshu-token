package yaoshu.token.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import yaoshu.token.pojo.ipo.DeploymentIPO;
import yaoshu.token.service.DeploymentService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ai.yue.library.base.exception.ResultException;
import ai.yue.library.base.view.R;
import ai.yue.library.base.view.Result;

/**
 * 部署管理控制器  * <p>
 * 认证：AdminAuth（全部）
 */
@Slf4j
@RestController
@SaCheckRole("admin")
@RequestMapping("/api/deployments")
@RequiredArgsConstructor
public class DeploymentController {

    private final DeploymentService deploymentService;

    @GetMapping("/settings")
    public Result<?> getSettings() {
        return R.success(deploymentService.getSettings());
    }

    @PostMapping("/settings/test-connection")
    public Result<?> testConnection(@RequestBody(required = false) DeploymentIPO.TestConnection ipo) {
        String apiKey = ipo != null ? trimToNull(ipo.getApiKey()) : null;
        return R.success(deploymentService.testConnection(apiKey));
    }

    @GetMapping("/")
    public Result<?> getAll(@RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int p,
                                      @RequestParam(defaultValue = "50") int page_size) {
        return R.success(deploymentService.getAllDeployments(status, p, page_size));
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int p,
                                      @RequestParam(defaultValue = "50") int page_size) {
        return R.success(deploymentService.searchDeployments(keyword, status, p, page_size));
    }

    @PostMapping("/test-connection")
    public Result<?> testConnection2(@RequestBody(required = false) DeploymentIPO.TestConnection ipo) {
        String apiKey = ipo != null ? trimToNull(ipo.getApiKey()) : null;
        return R.success(deploymentService.testConnection(apiKey));
    }

    @GetMapping("/hardware-types")
    public Result<?> getHardwareTypes() {
        return R.success(deploymentService.getHardwareTypes());
    }

    @GetMapping("/locations")
    public Result<?> getLocations() {
        return R.success(deploymentService.getLocations());
    }

    @GetMapping("/available-replicas")
    public Result<?> getAvailableReplicas(@RequestParam int hardware_id,
                                                     @RequestParam(defaultValue = "1") int gpu_count) {
        return R.success(deploymentService.getAvailableReplicas(hardware_id, gpu_count));
    }

    @PostMapping("/price-estimation")
    public Result<?> priceEstimation(@RequestBody Map<String, Object> body) {
        return R.success(deploymentService.estimatePrice(body));
    }

    @GetMapping("/check-name")
    public Result<?> checkName(@RequestParam String name) {
        return R.success(deploymentService.checkName(name));
    }

    @PostMapping("/")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        return R.success(deploymentService.createDeployment(body));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable String id) {
        return R.success(deploymentService.getDeployment(id));
    }

    @GetMapping("/{id}/logs")
    public Result<?> getLogs(@PathVariable String id,
                                        @RequestParam String container_id) {
        return R.success(deploymentService.getDeploymentLogs(id, container_id));
    }

    @GetMapping("/{id}/containers")
    public Result<?> listContainers(@PathVariable String id) {
        return R.success(deploymentService.listContainers(id));
    }

    @GetMapping("/{id}/containers/{container_id}")
    public Result<?> getContainerDetails(@PathVariable String id,
                                                    @PathVariable("container_id") String containerId) {
        return R.success(deploymentService.getContainerDetails(id, containerId));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return R.success(deploymentService.updateDeployment(id, body));
    }

    @PutMapping("/{id}/name")
    public Result<?> updateName(@PathVariable String id, @Valid @RequestBody DeploymentIPO.UpdateName ipo) {
        String name = trimToNull(ipo.getName());
        if (name == null) throw new ResultException(R.errorPrompt("deployment name cannot be empty"));
        return R.success(deploymentService.updateDeploymentName(id, name));
    }

    @PostMapping("/{id}/extend")
    public Result<?> extend(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return R.success(deploymentService.extendDeployment(id,
                body == null ? Map.of() : body));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        return R.success(deploymentService.deleteDeployment(id));
    }

    // ======================== 辅助方法 ========================



    private String trimToNull(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

