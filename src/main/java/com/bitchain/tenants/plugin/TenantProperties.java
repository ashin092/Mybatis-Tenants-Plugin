package com.bitchain.tenants.plugin;

import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 此类表示应用程序中多租户的配置。
 * 负责定义多租户模式、目标表和列，
 * 并将映射器方法名称映射到过滤器解释。
 * <p>
 * 配置是从属性中读取的，并在 bean 初始化阶段进行初始化。
 * <p>
 * 用法：
 * - 通过提供 SqlSessionFactory 和 JdbcTemplate 创建 TenantConfig 实例。
 * - 通过属性配置多租户模式和目标表/列。
 * - 确保指定目标列。
 * - 如果模式为AUTO，则将自动处理具有目标列的表。
 * - 如果模式为 ASSIGN，则仅处理 targetTables 属性中指定的表。
 * <p>
 * 参考该类中的常量和方法来访问配置的值。
 *
 * @author xierh
 * @since 2023/10/11 10:23
 */
@Configuration
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 多租户sql干涉模式。 Auto自动模式(默认)：扫描全库，存在符合多租户相关字段的表自动处理。
     * 该方式启动时间会相较于指定模式慢，随着数库表大小逐渐增加。
     * Assign指定模式：不自动扫表，采用tenant-include-tables列表中的表作为多租户的表指定处理
     */
    private TenantMode scanMode = TenantMode.AUTO;

    /**
     * 多租户指定表范围，自动模式下不生效
     */
    private List<String> targetTables = null;

    /**
     * 多租户相关字段，可以指定多个.正常情况下应该只有一个，暂时预留兼容情况。
     */
    private List<String> targetColumns;

    /**
     * 过滤器附加过滤后缀。
     * 过滤符合mapper方法名 + 该项值的也会生效于【多租户过滤器 - TenantFilter】的规则。
     * 该项配置的出现主要是为了防止使用PageHelper + 过滤器的时候，mapper method 加上了过滤标识。又因为PageHelper生成的sql为sqlId+_COUNT,导致无法成功过滤，
     * 最后分页与预期大不同的情况
     */
    private String filterAdditional = null;

    /**
     * 指示拦截器是否应自动注册。
     *
     * <p>
     * 默认情况下，拦截器在软件执行时自动注册。
     * 但是，在某些情况下，可能需要手动注册拦截器。
     * 此变量提供了启用或禁用自动注册行为的灵活性。
     * </p>
     *
     * <p>
     * 如果此变量的值设置为 {@code true}，拦截器将自动注册。
     * 如果值为 {@code false}，则拦截器不会自动注册，必须执行手动注册。
     * </p>
     *
     * @see Interceptor
     */
    private boolean interceptorAutoRegister = true;

    public enum TenantMode {
        /**
         * Auto自动模式：扫描全库，存在符合多租户相关字段的表自动处理。
         */
        AUTO,
        /**
         * 指定模式：不自动扫表，采用tenant-include-tables列表中的表作为多租户的表指定处理
         */
        ASSIGN
    }

    public boolean isInterceptorAutoRegister() {
        return interceptorAutoRegister;
    }

    public void setInterceptorAutoRegister(boolean interceptorAutoRegister) {
        this.interceptorAutoRegister = interceptorAutoRegister;
    }

    public TenantMode getScanMode() {
        return scanMode;
    }

    public void setScanMode(TenantMode scanMode) {
        this.scanMode = scanMode;
    }

    public List<String> getTargetTables() {
        return targetTables;
    }

    public void setTargetTables(List<String> targetTables) {
        this.targetTables = targetTables;
    }

    public List<String> getTargetColumns() {
        return targetColumns;
    }

    public void setTargetColumns(List<String> targetColumns) {
        this.targetColumns = targetColumns;
    }

    public String getFilterAdditional() {
        return filterAdditional;
    }

    public void setFilterAdditional(String filterAdditional) {
        this.filterAdditional = filterAdditional;
    }
}
