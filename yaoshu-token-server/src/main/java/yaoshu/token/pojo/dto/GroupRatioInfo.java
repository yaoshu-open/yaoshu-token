package yaoshu.token.pojo.dto;

import lombok.Getter;

/**
 * 分组倍率信息  */
@Getter
public class GroupRatioInfo {

    private double groupRatio;
    private double groupSpecialRatio;
    private boolean hasSpecialRatio;

    public GroupRatioInfo() {
    }

    public GroupRatioInfo(double groupRatio, double groupSpecialRatio, boolean hasSpecialRatio) {
        this.groupRatio = groupRatio;
        this.groupSpecialRatio = groupSpecialRatio;
        this.hasSpecialRatio = hasSpecialRatio;
    }
}
