package com.github.tenants.plugin.cache;

import com.github.tenants.plugin.TenantProperties;
import com.github.tenants.plugin.annotation.TenantFilter;
import com.github.tenants.plugin.core.TenantUserIdentity;

import java.util.Map;

/**
 * @author xierh
 * @since 2023/10/31 17:00
 */
public class PluginCache {

    private static PluginCache inst = null;

    /**
     * mapper方法名与过滤注解关系映射。
     * 由于实现的是mybatis的intercept，运行时获取方法上的注解比较麻烦，不如直接先载入内存。
     */
    private Map<String, TenantFilter> nameNFilter;

    private TenantProperties tenantProperties;

    public TenantUserIdentity tenantUserImplement;

    public PluginCache(Map<String, TenantFilter> nameNFilter, TenantProperties tenantProperties, TenantUserIdentity tenantUserImplement) {
        this.nameNFilter = nameNFilter;
        this.tenantProperties = tenantProperties;
        this.tenantUserImplement = tenantUserImplement;
        PluginCache.inst = this;
    }

    public Map<String, TenantFilter> getNameNFilter() {
        return nameNFilter;
    }

    public TenantProperties getTenantProperties() {
        return tenantProperties;
    }

    public static PluginCache getInst() {
        return inst;
    }
}
