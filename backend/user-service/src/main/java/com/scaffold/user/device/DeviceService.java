package com.scaffold.user.device;

import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;

/**
 * 登录设备管理服务。按用户维度自服务：只能管理自己的设备。
 */
public interface DeviceService {

    /**
     * 分页查询指定用户的登录设备。
     *
     * @param query  分页参数
     * @param userId 用户 id
     * @return 设备分页结果
     */
    PageResult<UserDevice> page(PageQuery query, Long userId);

    /**
     * 将设备标记为可信。
     *
     * @param id     设备记录 id
     * @param userId 用户 id（归属校验）
     */
    void trust(Long id, Long userId);

    /**
     * 吊销设备。吊销后该设备无法登录。
     *
     * @param id     设备记录 id
     * @param userId 用户 id（归属校验）
     */
    void revoke(Long id, Long userId);

    /**
     * 移除设备记录。下次登录将重新登记为普通设备。
     *
     * @param id     设备记录 id
     * @param userId 用户 id（归属校验）
     */
    void remove(Long id, Long userId);
}
