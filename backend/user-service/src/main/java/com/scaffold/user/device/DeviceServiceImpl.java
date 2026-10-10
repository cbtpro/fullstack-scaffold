package com.scaffold.user.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.api.PageQuery;
import com.scaffold.common.api.PageResult;
import com.scaffold.common.api.ResultCode;
import com.scaffold.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 设备管理服务实现。归属校验融入 WHERE 条件（eq(id).eq(userId)），
 * 单次 SQL 原子完成，避免先查后改的竞态；归属不符也按 NOT_FOUND 处理，防信息泄露。
 */
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final UserDeviceMapper deviceMapper;

    @Override
    public PageResult<UserDevice> page(PageQuery query, Long userId) {
        Page<UserDevice> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getUserId, userId)
                .orderByDesc(UserDevice::getLastLoginTime);
        IPage<UserDevice> result = deviceMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void trust(Long id, Long userId) {
        updateRiskLevel(id, userId, RiskLevel.TRUSTED);
    }

    @Override
    public void revoke(Long id, Long userId) {
        updateRiskLevel(id, userId, RiskLevel.REVOKED);
    }

    @Override
    public void remove(Long id, Long userId) {
        LambdaQueryWrapper<UserDevice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDevice::getId, id)
                .eq(UserDevice::getUserId, userId);
        int affected = deviceMapper.delete(wrapper);
        if (affected == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "设备不存在");
        }
    }

    private void updateRiskLevel(Long id, Long userId, RiskLevel level) {
        LambdaUpdateWrapper<UserDevice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDevice::getId, id)
                .eq(UserDevice::getUserId, userId)
                .set(UserDevice::getRiskLevel, level.name());
        int affected = deviceMapper.update(null, wrapper);
        if (affected == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "设备不存在");
        }
    }
}
