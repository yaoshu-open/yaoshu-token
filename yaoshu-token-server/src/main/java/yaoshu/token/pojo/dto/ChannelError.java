package yaoshu.token.pojo.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

/**
 * 渠道错误信息  */
@Data
public class ChannelError {

    private int channelId;
    private int channelType;
    private String channelName;
    private boolean isMultiKey;
    private boolean autoBan;
    private String usingKey;

    public static ChannelError of(int channelId, int channelType, String channelName,
                                   boolean isMultiKey, String usingKey, boolean autoBan) {
        ChannelError e = new ChannelError();
        e.setChannelId(channelId);
        e.setChannelType(channelType);
        e.setChannelName(channelName);
        e.setMultiKey(isMultiKey);
        e.setUsingKey(usingKey);
        e.setAutoBan(autoBan);
        return e;
    }
}
