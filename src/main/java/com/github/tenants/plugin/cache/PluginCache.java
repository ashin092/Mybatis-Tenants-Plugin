package com.github.tenants.plugin.cache;

import com.github.tenants.plugin.TenantProperties;
import com.github.tenants.plugin.annotation.TenantFilter;
import com.github.tenants.plugin.core.TenantUserIdentity;
import com.github.tenants.plugin.ex.TenantException;
import com.github.tenants.plugin.mapper.StructureMapper;
import com.github.tenants.plugin.util.MybatisUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
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

    private final TenantProperties tenantProperties;

    public TenantUserIdentity tenantUserImplement;

    public PluginCache(List<SqlSessionFactory> sqlSessionFactoryList, TenantProperties tenantProperties, TenantUserIdentity tenantUserImplement) {
        if (tenantProperties.getTargetColumns() == null) {
            throw new TenantException("no multi tenant related fields are specified");
        }
        if (tenantProperties.getScanMode().equals(TenantProperties.TenantMode.AUTO)) {
            Map<Class<?>, ?> classMybatisMapperProxyFactoryMap = MybatisUtils.getMapperRegistry(sqlSessionFactoryList.get(0));
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
            try (SqlSession sqlSession = sqlSessionFactoryList.get(0).openSession()) {
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
