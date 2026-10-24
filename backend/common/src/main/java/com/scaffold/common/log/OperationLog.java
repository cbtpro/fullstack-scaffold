package com.scaffold.common.log;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。标注在需要审计的接口方法上，由各业务服务自行提供的切面采集并落库。
 *
 * <p>注解放在公共模块，只描述「记什么」；「怎么记」（落库、异步、脱敏实现）留给业务服务，
 * 避免公共模块反向依赖持久化组件。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 业务模块，如「用户管理」 */
    String module();

    /** 操作类型，如「新增」「删除」 */
    String action();

    /** 操作说明；为空时切面回退为方法签名 */
    String description() default "";

    /**
     * 是否记录请求参数。默认记录，切面会对密码/令牌一类的字段脱敏；
     * 参数体积过大或含二进制内容的方法建议置为 false。
     */
    boolean recordParams() default true;
}
