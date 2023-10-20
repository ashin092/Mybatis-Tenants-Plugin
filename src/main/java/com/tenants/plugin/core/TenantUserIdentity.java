package com.tenants.plugin.core;

import com.tenants.plugin.ex.TenantException;

/**
 * 这是一个表示租户用户标识的抽象类。
 * 它提供了检索租户用户标识和设置下一个租户用户标识的方法。
 * 需要自主继承该类实现抽象方法， 并将子类加载到Spring容器中。
 * @author xierh
 * @since 2023/10/13 12:07
 */
public abstract class TenantUserIdentity {

    /**
     * 检索租户用户标识。
     *
     * @return 作为数字的租户用户标识。
     */
    public abstract Long getTenantUserIdentity();

    protected TenantUserIdentity next;

    public void setNext(TenantUserIdentity next) {
        this.next = next;
    }

    final public long doGetTenantUserIdentity() {
        TenantUserIdentity current = this;
        do {
            try {
                Long result = current.getTenantUserIdentity();
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } while ((current = current.next) != null);
        // 如果该sql未排除
        throw new TenantException("no valid tenant user identity provided");
    }
}
