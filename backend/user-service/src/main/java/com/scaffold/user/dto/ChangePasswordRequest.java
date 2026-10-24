package com.scaffold.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 本人修改密码请求。用户 id 不在请求体中，由 token 解析得到，防止越权改他人密码。
 *
 * @param oldPassword 原密码（明文）
 * @param newPassword 新密码（明文，6-32 位）
 */
@Schema(description = "本人修改密码请求")
public record ChangePasswordRequest(
        @Schema(description = "原密码", requiredMode = Schema.RequiredMode.REQUIRED)
        String oldPassword,
        @Schema(description = "新密码（6-32 位）", requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword) {
}
