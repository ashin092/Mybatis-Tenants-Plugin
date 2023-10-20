package com.tenants.plugin.core.interceptor;

import com.tenants.plugin.TenantProperties;
import com.tenants.plugin.annotation.TenantFilter;
import com.tenants.plugin.core.config.TenantAutoConfiguration;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 此类是一个拦截器，用于在运行时修改 SQL 查询以基于租户 ID 添加多租户筛选条件。
 * 它实现了 MyBatis 的拦截器接口。
 * <p>
 * 拦截器拦截 Executor 类的 “query” 方法，并在执行之前修改 SQL 查询。
 * 拦截器根据租户 ID 向 SQL 查询的 SELECT、JOIN 和 WHERE 子句添加筛选条件。（如果适用）
 * 多租户条件基于指定应包含多租户条件的目标表和列的配置。
 * 这可确保查询仅返回属于指定租户的数据。
 * <p>
 * TenantSqlInterceptor 使用 TenantConfig 类来获取多租户筛选所需的配置设置。
 * 配置设置包括目标表、目标列和其他筛选器。
 * <p>
 * TenantSqlInterceptor 从 Interceptor 接口实现拦截方法。
 * 当调用 Executor 类的“query”方法并截获执行流时，将调用此方法。
 * 它通过添加多租户筛选条件来修改 SQL 查询，然后继续执行原始查询。
 * <p>
 * TenantSqlInterceptor 还包括用于解析和修改 SQL 查询的帮助程序方法。
 * 这些方法处理 SQL 查询的不同部分，例如 FROM 子句、JOIN 子句和 WHERE 子句。
 * <p>
 * <p>
 * 要使用此拦截器，请在 MyBatis 配置中将其配置为拦截器：
 * <pre>
 * <code>
 * &lt;plugins&gt;
 *     &lt;plugin interceptor="com.example.TenantSqlInterceptor" /&gt;
 *  &lt;/plugins&gt;
 * </code>
 * </pre>
 *
 * @author xierh
 * 如果mybatis已由Spring托管，可以将其在代码中加入责任链;
 * <p>
 * 注意：此类的实现可能因多租户数据体系结构的特定要求而异。
 * @since 2023/10/11 10:23
 */
@Intercepts({@Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}), @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),})
public class TenantSqlInterceptor implements Interceptor {

    /**
     * 表示租户的配置。
     * <p>
     * 此类用于存储和访问特定于租户的配置设置。
     *
     * @see TenantSqlInterceptor#getTenantConfig
     * 它是一个单例类，只建议使用“getTenantConfig（）”方法访问。
     */
    static TenantAutoConfiguration configuration;

    static TenantProperties tenantProperties = null;

    /**
     * MyBatis拦截器，用于添加租户隔离信息，实现数据隔离。
     *
     * @return 下一责任链
     * @throws Throwable 如果在拦截过程中出现任何错误
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Executor executor = (Executor) invocation.getTarget();
        Object[] args = invocation.getArgs();

        // 获取查询语句相关信息
        MappedStatement ms = (MappedStatement) args[0];
        // 取到的parameter可能是Map,看是否为@Param进行了多参数绑定，是则已被封装为一个Map
        Object parameter = args[1];
        ResultHandler<?> resultHandler = (ResultHandler<?>) args[3];
        RowBounds rowBounds = (RowBounds) args[2];
        BoundSql boundSql;
        CacheKey cacheKey;
        // 根据参数个数，获取BoundSql和缓存key
        if (args.length == 4) {
            //  4个参数时，直接从参数中获取
            boundSql = ms.getBoundSql(parameter);
            cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
        } else {
            // 6个参数时，从提供的参数中提取
            cacheKey = (CacheKey) args[4];
            boundSql = (BoundSql) args[5];
        }
        // 检查租户设置，根据过滤注解，可能需要跳过本次sql处理
        if (!CollectionUtils.isEmpty(this.getTenantConfig().getNameNFilter())) {
            String sqlId = ms.getId().replaceFirst(tenantProperties.getFilterAdditional() + "$", "");
            TenantFilter tenantFilter = this.getTenantConfig().getNameNFilter().get(sqlId);
            if (tenantFilter != null && !tenantFilter.exclude()) {
                // 使用注解进行不处理的跳过到下个责任处理点
                return executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            }
        }

        // 否则，开始处理SQL，添加租户ID
        String tenantsSql = boundSql.getSql();
        try {
            // 解析SQL语句并为其添加租户表达式
            Statement stmt = CCJSqlParserUtil.parse(tenantsSql);
            Select selectStmt = (Select) stmt;
            SelectBody selectBody = selectStmt.getSelectBody();
            addTenantExpress2SB(selectBody, this.getTenantConfig().tenantUserImplement.doGetTenantUserIdentity());
            tenantsSql = selectBody.toString();
        } catch (JSQLParserException e) {
            // 解析失败，忽略并执行原始SQL
//            log.info("多租户信息处理失败，执行原sql", e);
            return executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
        }
        // 将处理过的SQL语句设置回BoundSql然后执行查询
        Field field = boundSql.getClass().getDeclaredField("sql");
        field.setAccessible(true);
        field.set(boundSql, tenantsSql);
        return executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
    }

    /**
     * 返回表或子查询的名称。
     * 如果 fromItem 是子查询，它将检查别名。如果可用，它将返回别名。
     * 否则，它将返回 fromItem 的字符串表示形式。
     * 如果 fromItem 是一个表，则返回表的名称。
     *
     * @param fromItem 表示表或子查询的 FromItem 对象
     * @return 表或子查询的名称
     */
    private String getTableOrSubQueryName(FromItem fromItem) {
        if (fromItem instanceof SubSelect) {
            Alias alias = fromItem.getAlias();
            if (alias != null && alias.getName() != null) {
                return alias.getName();
            } else {
                return fromItem.toString();
            }
        } else if (fromItem instanceof Table) {
            return ((Table) fromItem).getName();
        } else {
            return fromItem.toString();
        }
    }

    private String getAliasName(FromItem fromItem) {
        return fromItem.getAlias() != null ? fromItem.getAlias().getName() : null;
    }

    /**
     * 将租户条件添加到查询中。
     * 遍历查询的SelectBody，递归处理PlainSelect和SetOperationList。
     * 对于PlainSelect，检查fromItem是否是子查询或表，获取表或子查询的名称，
     * 如果表或子查询与配置的目标表匹配，则添加租户条件到where子句中，或者根据别名添加租户条件。
     * 对于Join，获取右表的名称，如果右表与配置的目标表匹配，则添加租户条件到on子句中。
     * 对于SetOperationList，递归处理其中的每个SelectBody。
     * 输出处理后的selectBody。
     *
     * @param selectBody 查询的SelectBody对象
     * @param tenantId   租户ID
     */
    private void addTenantExpress2SB(SelectBody selectBody, long tenantId) {
        // 如果SQL查询语句是纯的Select语句，无Union或其他set操作
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;
            FromItem fromItem = plainSelect.getFromItem();
            String fromTableName = getTableOrSubQueryName(fromItem);
            String fromAliasName = getAliasName(fromItem);

            // 如果from部分是子查询，给子查询添加租户ID
            if (fromItem instanceof SubSelect) {
                SubSelect subSelect = (SubSelect) fromItem;
                SelectBody subSelectBody = subSelect.getSelectBody();
                addTenantExpress2SB(subSelectBody, tenantId);
            }

            // 处理每个join不分
            List<Join> joins = plainSelect.getJoins();
            LongValue tenantIdLV = new LongValue(tenantId);
            if (joins != null) {
                for (Join join : joins) {
                    fromItem = join.getRightItem();
                    // //如果join的右边部分是子查询，给子查询添加租户ID
                    //  如 select from xx join (select .. from ) 时，该回调逻辑提供对join子查询解析的功能。
                    if (fromItem instanceof SubSelect) {
                        SubSelect subSelect = (SubSelect) fromItem;
                        SelectBody subSelectBody = subSelect.getSelectBody();
                        addTenantExpress2SB(subSelectBody, tenantId);
                    }

                    //如果join的右边部分和指定的租户表相同，给这部分语句添加租户ID
                    String joinedTableName = getTableOrSubQueryName(fromItem);
                    if (!tenantProperties.getTargetTables().contains(joinedTableName)) {
                        continue;
                    }
                    if (getAliasName(fromItem) != null) {
                        joinedTableName = getAliasName(fromItem);
                    }
                    // 在on部分添加租户ID，如果on部分为空，初始化on部分；否则在现有的基础上添加租户ID
                    Collection<Expression> onExpressions = join.getOnExpressions();
                    Expression newCondition = new EqualsTo(new Column(joinedTableName + "." + tenantProperties.getTargetColumns().get(0)), tenantIdLV);
                    // 如果onExpressions为空，添加ON条件 、 否则是AND
                    if (onExpressions == null || onExpressions.isEmpty()) {
                        Expression onCondition = new EqualsTo(new Column(joinedTableName + "." + tenantProperties.getTargetColumns().get(0)), tenantIdLV);
                        join.setOnExpressions(Collections.singleton(onCondition));
                    } else { // 否则，添加AND条件
                        AndExpression newExpression = new AndExpression(join.getOnExpression(), newCondition);
                        join.setOnExpressions(Collections.singleton(newExpression));
                    }
                    if (fromItem instanceof SubSelect) {
                        addTenantExpress2SB(((SubSelect) fromItem).getSelectBody(), tenantId);
                    }
                }
            }
            // from部分添加租户id
            if (tenantProperties.getTargetTables().contains(fromTableName)) {
                Expression where = plainSelect.getWhere();
                String fromName = fromAliasName == null ? fromTableName : fromAliasName;
                if (where != null) {
                    AndExpression newWhere = new AndExpression(where, new EqualsTo(new Column(fromName + "." + tenantProperties.getTargetColumns().get(0)), tenantIdLV));
                    plainSelect.setWhere(newWhere);
                } else {
                    plainSelect.setWhere(new EqualsTo(new Column(fromName + "." + tenantProperties.getTargetColumns().get(0)), tenantIdLV));
                }
            }
            // 如果SQL查询语句不仅仅是纯的Select语句，包含Union或其他set操作，就把操作的每部分单独处理
        } else if (selectBody instanceof SetOperationList) {
            List<SelectBody> selectBodies = ((SetOperationList) selectBody).getSelects();
            for (SelectBody body : selectBodies) {
                addTenantExpress2SB(body, tenantId);
            }
        }
    }

    /**
     * 获取租户配置对象。
     * 如果租户配置对象为null，则使用懒加载方式加载租户配置实例。
     *
     * @return 租户配置对象
     */
    private TenantAutoConfiguration getTenantConfig() {
        if (TenantSqlInterceptor.configuration == null) {
            TenantSqlInterceptor.configuration = TenantAutoConfiguration.getInstance();
            TenantSqlInterceptor.tenantProperties = TenantSqlInterceptor.configuration.getTenantProperties();
        }
        return configuration;
    }
}
