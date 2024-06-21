package com.github.tenants.plugin.annotation;

import java.lang.annotation.*;

/**
 * 租户筛选器注释用于标记不需要多租户处理的方法。
 *
 * @author xierh
 * @since 2023/10/11 11:07
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface TenantFilter {

    /**
     * 指示是否应为多租户处理排除该方法。
     *
     * @return 如果应排除该方法，则false，否则为 true。
     */
    boolean exclude() default false;
}
