package com.scaffold.user.dto;

/**
 * 本人维护资料的入参。只暴露允许本人修改的三个字段：
 * 用户名、密码、状态属于账号管理范畴，不走此入口；id 由 token 解析，请求体不接受。
 */
public record ProfileUpdateRequest(String nickname, String email, String phone) {
}
