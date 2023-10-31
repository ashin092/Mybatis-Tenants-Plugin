package com.github.tenants.plugin.core.config;

import com.github.tenants.plugin.TenantProperties;
import com.github.tenants.plugin.annotation.TenantFilter;
import com.github.tenants.plugin.cache.PluginCache;
import com.github.tenants.plugin.comparator.TenantChainOrderComparator;
import com.github.tenants.plugin.core.MybatisInterceptorAutoRegister;
import com.github.tenants.plugin.core.TenantUserIdentity;
import com.github.tenants.plugin.core.interceptor.TenantSqlInterceptor;
import com.github.tenants.plugin.ex.TenantException;
import com.github.tenants.plugin.mapper.StructureMapper;
import com.github.tenants.plugin.util.MybatisUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.omg.CORBA.SystemException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.*;


/**
 * 此类负责配置应用程序的多租户功能。
 * 它用@Configuration注释，以指示它是一个配置类。
 * 它还带有@EnableConfigurationProperties注释，以便能够使用 TenantProperties 类进行配置
 * 它以类路径中存在的类 SqlSessionFactory 和 TenantProperties 为条件。
 *
 * @author xierh
 * @since 2023/10/13 12:02
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
@ConditionalOnClass({SqlSessionFactory.class, SqlSessionFactory.class, TenantProperties.class})
public class TenantAutoConfiguration {

    public TenantUserIdentity tenantUserImplement;

    /**
     * mapper方法名与过滤注解关系映射。
     * 由于实现的是mybatis的intercept，运行时获取方法上的注解比较麻烦，不如直接先载入内存。
     */
    private Map<String, TenantFilter> nameNFilter;

    final List<SqlSessionFactory> sqlSessionFactoryList;

    final SqlSessionFactory sqlSessionFactory;

    final TenantProperties tenantProperties;

    final ApplicationContext context;

    /**
     * 初始化租户配置实例。
     *
     * @throws SystemException 如果目标租户列配置为空
     */
    @Bean
    public TenantSqlInterceptor tenantSqlInterceptorReg() {
        if (tenantProperties.getTargetColumns() == null) {
            throw new TenantException("no multi tenant related fields are specified");
        }
        if (tenantProperties.getScanMode().equals(TenantProperties.TenantMode.AUTO)) {
            Map<Class<?>, ?> classMybatisMapperProxyFactoryMap = MybatisUtils.getMapperRegistry(sqlSessionFactory);
            //将mybatis-plus的mapper获取到，获取每一个方法上的MybatisInterceptorAnnotation注解的type与对应方法全限路径。
            this.nameNFilter = new HashMap<>();
            for (Class<?> aClass : classMybatisMapperProxyFactoryMap.keySet()) {
                for (Method method : aClass.getMethods()) {
                    TenantFilter annotation = method.getAnnotation(TenantFilter.class);
                    if (annotation != null) {
                        String name = method.getName();
                        this.nameNFilter.put(aClass.getName() + "." + name, annotation);
                    }
                }
            }
            // 创建 SqlSession
            try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
                org.apache.ibatis.session.Configuration configuration = sqlSession.getConfiguration();
                if (!configuration.hasMapper(StructureMapper.class)) {
                    configuration.addMapper(StructureMapper.class);
                }
                StructureMapper structureMapper = sqlSession.getMapper(StructureMapper.class);
                for (String targetColumn : tenantProperties.getTargetColumns()) {
                    List<String> tableNames = structureMapper.queryTablesByColumnName(targetColumn);
                    tenantProperties.getTargetTables().addAll(tableNames);
                }
            }
        }
        this.frameworkStart();
        new PluginCache(nameNFilter, tenantProperties, tenantUserImplement);
        return new TenantSqlInterceptor();
    }

    @Bean
    public MybatisInterceptorAutoRegister miaReg(TenantSqlInterceptor tenantSqlInterceptor) {
        return new MybatisInterceptorAutoRegister(tenantProperties, sqlSessionFactoryList, tenantSqlInterceptor);
    }

    /**
     * 开始框架初始化。
     * 该方法在容器启动后自动执行，用于初始化租户相关配置。
     * 如果获取不到目标租户列配置或租户实现类，则会抛出异常。
     */
    private void frameworkStart() {
        Map<String, TenantUserIdentity> beansOfType = context.getBeansOfType(TenantUserIdentity.class);
        if (beansOfType.isEmpty()) {
            throw new TenantException("no implementation of core.com.bitchain.tenants.plugin.TenantUserIdentity found");
        }
        List<TenantUserIdentity> tuiList = new ArrayList<>(beansOfType.values());
        tuiList.sort(new TenantChainOrderComparator());

        Iterator<TenantUserIdentity> iterator = tuiList.iterator();
        TenantUserIdentity current = iterator.next();
        while (iterator.hasNext()) {
            TenantUserIdentity next = iterator.next();
            current.setNext(next);
            current = next;
        }
        this.tenantUserImplement = current;
    }

    public Map<String, TenantFilter> getNameNFilter() {
        return this.nameNFilter;
    }

    public TenantProperties getTenantProperties() {
        return this.tenantProperties;
    }


    public TenantAutoConfiguration(List<SqlSessionFactory> sqlSessionFactoryList, SqlSessionFactory sqlSessionFactory, ApplicationContext context, TenantProperties tenantProperties) {
        this.sqlSessionFactoryList = sqlSessionFactoryList;
        this.sqlSessionFactory = sqlSessionFactory;
        this.context = context;
        this.tenantProperties = tenantProperties;
    }
}
