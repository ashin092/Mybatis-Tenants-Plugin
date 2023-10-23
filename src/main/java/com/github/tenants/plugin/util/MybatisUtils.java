package com.github.tenants.plugin.util;

import com.github.tenants.plugin.ex.TenantException;
import org.apache.ibatis.session.SqlSessionFactory;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * MyBatis 框架的工具类。
 *
 * @author xierh
 * @since 2023/10/11 11:03
 */
public class MybatisUtils {


    /**
     * 反射性地从“MybatisMapperRegistry”类中检索“knownMappers”字段。
     * “knownMappers”字段是一个映射，用于保存实体类及其相应的映射器接口之间的映射。
     * 此方法允许通过“MybatisMapperProxyFactory”类访问此映射。
     *
     * @param sqlSessionFactory 用于检索“knownMappers”字段的 SQL 会话工厂。
     * @return 包含实体类与其相应的“MybatisMapperProxyFactory”实例之间映射的映射。
     * @throws TenantException 如果在检索“已知映射器”字段时发生错误。
     */
    public static Map<Class<?>, ?> getMapperRegistry(SqlSessionFactory sqlSessionFactory) {
        Map<Class<?>, ?> knownMappers;
        try {
            Class<?> mapperRegistryClass = null;
            try {
                mapperRegistryClass = Class.forName("com.baomidou.mybatisplus.core.MybatisMapperRegistry");
            } catch (ClassNotFoundException ex) {
                mapperRegistryClass = Class.forName("org.apache.ibatis.binding.MapperRegistry");
            } finally {
                if (mapperRegistryClass == null) {
                    throw new TenantException("unable to get mybatis mapper list");
                }
            }
            Field mapperRegistryField = mapperRegistryClass.getDeclaredField("knownMappers");
            mapperRegistryField.setAccessible(true);

            Field mybatisMapperRegistryField;
            try {
                mybatisMapperRegistryField = sqlSessionFactory.getConfiguration().getClass()
                        .getDeclaredField("mapperRegistry");
            } catch (NoSuchFieldException ex) {
                mybatisMapperRegistryField = sqlSessionFactory.getConfiguration().getClass()
                        .getDeclaredField("mybatisMapperRegistry");
            }
            mybatisMapperRegistryField.setAccessible(true);
            Object mapperRegistryInstance = mybatisMapperRegistryField.get(sqlSessionFactory.getConfiguration());

            knownMappers = (Map<Class<?>, ?>) mapperRegistryField.get(mapperRegistryInstance);
        } catch (Exception e) {
            throw new TenantException("unable to get mybatis mapper list", e);
        }
        return knownMappers;
    }
}
