package com.scaffold.user.device;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录设备实体。token 与设备指纹绑定的持久化载体，
 * 后续"可信登录列表"按 {@code risk_level} 维度扩展即可。
 */
@Data
@TableName("user_device")
public class UserDevice implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id */
    private Long userId;

    /** 设备标识（UUID），token claims 携带 */
    private String deviceId;

    /** 设备指纹（sha256 hex） */
    private String fingerprint;

    /** 最近登录 IP */
    private String ip;

    /** 风险等级：{@link RiskLevel} 枚举名 */
    private String riskLevel;

    /** 最近登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
