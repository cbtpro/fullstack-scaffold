package com.scaffold.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理员重置密码请求。目标用户 id 来自路径变量，无需原密码。
 *
 * @param newPassword 新密码（明文，6-32 位）
 */
@Schema(description = "管理员重置密码请求")
public record ResetPasswordRequest(
        @Schema(description = "新密码（6-32 位）", requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword) {
}
