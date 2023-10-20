package com.tenants.plugin.annotation;

import com.tenants.plugin.core.TenantUserIdentity;

import java.lang.annotation.*;

/**
 * 表示基于租户 ID 的实现顺序。
 * 未加注解的相对于加了注解的实现，未加注解的总是排在后面进行载入。
 *
 * @see TenantUserIdentity
 * @author xierh
 * @since 2023/10/17 10:29
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface TenantChainOrder {

    /**
     *返回基于租户 ID 的实现的排序顺序。
     * 值越小，优先级越高。
     *
     * @return 实现的排序顺序
     */
    int value();
}
