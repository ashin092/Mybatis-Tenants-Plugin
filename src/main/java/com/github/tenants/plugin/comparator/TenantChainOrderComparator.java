package com.github.tenants.plugin.comparator;

import com.tenants.plugin.annotation.TenantChainOrder;
import com.tenants.plugin.core.TenantUserIdentity;

import java.util.Comparator;

/**
 * 比较器实现，用于根据两个租户用户身份对象比较其租户链顺序值。具有较低顺序值的对象
 * 将被视为比具有更高阶值的对象具有更高优先级。
 *
 * @author xierh
 * @since 2023/10/17 10:56
 */
public class TenantChainOrderComparator implements Comparator<TenantUserIdentity> {

    @Override
    public int compare(TenantUserIdentity o1, TenantUserIdentity o2) {
        int order1 = getOrder(o1);
        int order2 = getOrder(o2);

        return Integer.compare(order1, order2);
    }

    private int getOrder(TenantUserIdentity obj) {
        TenantChainOrder order = obj.getClass().getAnnotation(TenantChainOrder.class);
        // 若存在类未加注解，value值设置为Integer.MAX_VALUE
        if (order == null) {
            return Integer.MAX_VALUE;
        } else {
            return order.value();
        }
    }
}
