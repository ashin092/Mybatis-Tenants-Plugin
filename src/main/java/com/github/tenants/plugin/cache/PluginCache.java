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
 * PluginCache 是一个类，用于存储插件信息的缓存。
 *
 * @author xierh
 *  * @since 2023/10/31 17:00
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

    /**
     * 使用给定的参数构造一个 PluginCache 对象。
     *
     * @param sqlSessionFactoryList 要从中检索映射器注册表的 SqlSessionFactory 对象的列表
     * @param tenantProperties 包含多租户相关字段和设置的 TenantProperties 对象
     * @param tenantUserImplement 用于实现多租户的 TenantUserIdentity 对象
     *
     * @throws TenantException 如果 tenantProperties 中未指定多租户相关字段
     */
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
