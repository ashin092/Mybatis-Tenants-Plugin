package com.github.tenants.plugin.core;

import com.github.tenants.plugin.TenantProperties;
import com.github.tenants.plugin.core.interceptor.TenantSqlInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MybatisInterceptorAutoRegister 类负责注册 TenantSqlInterceptor
 * 作为所有已配置的 SqlSessionFactory 实例的侦听器。如果自动注册配置
 * 属性设置为 false，此类不执行任何操作。
 * <p>
 * TenantSqlInterceptor 拦截 SQL 语句，并根据当前租户添加租户标识符子句。
 * <p>
 * 此类用 @Configuration 注释，表示它是一个配置类，可以
 * 由弹簧容器处理。
 * <p>
 * 此类用 @RequiredArgsConstructor 注释，表示构造函数是用
 * 所有最终字段作为参数。这用于依赖关系注入。
 * <p>
 * 此类用 @ConditionalOnClass 注释，这确保此类仅由
 * Spring 容器，如果指定的类（TenantProperties 和 SqlSessionFactory）存在于类路径中。
 * <p>
 * 此类定义了两个字段：tenantProperties 和 sqlSessionFactoryList，它们通过构造函数注入。
 * <p>
 * mybatisInterceptorRegister（） 方法用 @PostConstruct 注释，表示此方法是
 * 在构造 Bean 并注入依赖关系后调用。此方法执行
 * 为每个已配置的 SqlSessionFactory 实例注册 TenantSqlInterceptor。
 *
 * @author xierh
 * @since 2023/10/17 17:26
 */
@Component
@ConditionalOnClass({TenantProperties.class, SqlSessionFactory.class})
public class MybatisInterceptorAutoRegister implements InitializingBean {

    final TenantProperties tenantProperties;

    final List<SqlSessionFactory> sqlSessionFactoryList;

    final TenantSqlInterceptor tenantSqlInterceptor;

    /**
     * 此方法将 TenantSqlInterceptor 注册为所有已配置的 SqlSessionFactory 实例的侦听器。
     * 如果拦截器自动注册配置属性设置为 false，则该方法根本不执行任何操作。
     * TenantSqlInterceptor 拦截 SQL 语句，并根据当前租户添加租户标识符子句。
     * 此方法在构造 Bean 并注入依赖项后调用，如@PostConstruct注释所示。
     * <p>
     *
     * @see TenantProperties#interceptorAutoRegister
     */
    public void mybatisInterceptorRegister() {
        if (!tenantProperties.isInterceptorAutoRegister()) {
            return;
        }
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
            sqlSessionFactory.getConfiguration().addInterceptor(tenantSqlInterceptor);
        }
    }

    public MybatisInterceptorAutoRegister(TenantProperties tenantProperties, List<SqlSessionFactory> sqlSessionFactoryList, TenantSqlInterceptor tenantSqlInterceptor) {
        this.tenantProperties = tenantProperties;
        this.sqlSessionFactoryList = sqlSessionFactoryList;
        this.tenantSqlInterceptor = tenantSqlInterceptor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.mybatisInterceptorRegister();
    }
}
