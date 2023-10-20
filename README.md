# Mybatis-Tenants-Plugin

## Language versions

- [English](README.en.md)
- [Chinese](README.zh.md)

Mybatis-Tenants-Plugin is a spring-boot-starter plugin based on MyBatis's interceptor interface expansion. It is a

dedicated solution for handling multi-tenant issues. By customizing the way of obtaining tenants, it provides developers
with a simpler and quicker way to adapt and manage multi-tenant applications.

The plugin focuses on how to manage the data of multiple tenants more conveniently when developing multi-tenant

applications. The plugin allows you to avoid a lot of repetitive code writing, greatly improving development efficiency,
so that you can focus more on the implementation of business logic, rather than data access.

The goal of designing this plugin is to allow developers to handle multi-tenant issues more freely and flexibly, and
provide an extensible, easy-to-use, and easy-to-integrate method to solve such issues.

## Features

The plugin is ready to use out of the box. For tables in the database with certain fields, the plugin automatically adds
tenant fields for filtering when executing the query/insertion sql.

Here is an example: assume `table1` is a multi-tenant table, and the multi-tenant field is `tenants_id`. The original sql is:

```mysql
  select *
  from table1 t1
           inner join
           (select * from table1 where id in (1, 2, 3)) t12 on t1.id = t12.id
```

After the plugin is installed and configured properly, the actual executed sql will be as follows. The plugin will help
developers filter the parameters of multiple tenants.

```mysql
  select *
  from table1 t1
           inner join
           (select * from table1 where id in (1, 2, 3) and tenants_id = ?) t12 on t1.id = t12.id
  where t1.tenants_id = ?
```

_*In this example, the actual runtime assignment of `tenants_id = ?` is related to the project's inheritance implementation of TenantUserIdentity._

## Installation and Configuration
You need the following conditions to run this project:

- Java SDK version 8 (Other versions to be tested)
- Spring Framework
- Mybatis Framework


## Supported Databases
- MySQL
- To be expanded and tested
  To install this plugin, please add the jar file to your project dependencies. Or you can add the following coordinates to your pom.xml file:

```xml
<dependency>
    <groupId>com.github.ashin092</groupId>
    <artifactId>mybatis-tenants-plugin</artifactId>
    <version>1.0.1</version>
</dependency>
```

## Usage

### Implement the abstract class TenantUserIdentity
`TenantUserIdentity` provides an abstract method for users to customize how to get tenants:
```java 
    /**
    * Retrieve the tenant user identity.
    *
    * @return The tenant user identity as a number.
    */
    public abstract Long getTenantUserIdentity();
```

You need to implement the `TenantUserIdentity` abstract class and provide the specific implementation for the `getTenantUserIdentity` method. The framework will automatically recognize and use this implementation for sql parsing and multi-tenant injection. The framework actually uses the chain of responsibility pattern to obtain the tenant user identity, so there can be multiple subclasses of `TenantUserIdentity`. If a specific implementation throws an exception or returns `null`, it will be handled at the next responsibility point.


You also need to register `TenantUserIdentity` as a Bean for the Spring container. Declaring `@Component` and other annotations on the class, or `@Bean` in the configuration class, can enable the framework to recognize and use this implementation.


Method one: Mark the Spring component on the implementation class, such as `@Component` or `@Service`.

```java
    @Component
    public class TenantUsersGetImpl extends TenantUserIdentity{
        
        @Override
        public Long getTenantUserIdentity() {
            // Your implementation...
        }
    }
```
Method two: Declare it in the configuration code using the Bean.
```java
    @Configuration
    public class TenantUsersConfig {

    @Bean
    public TenantUsersGetImpl addTenantUsersGetImpl(){
        return new TenantUsersGetImpl();
        }
    }
```

When multiple implementations need to handle the order of responsibility, please use `@TenantChainOrder` to adjust the order of responsibility. **The smaller the value, the higher the order**.

### Add interceptor to Mybatis intercept plugins

Actually, for users to use it out of the box, **if you don't have a need to adjust the order of the interceptors, this part can be ignored directly**. The following explanation is for situations where there are multiple mybatis interceptors and you need to adjust the order in which the interceptors take effect.

Please note that the artifact of pageHelper in the example code is pagehelper and not `pagehelper-spring-boot-starter`, because `pagehelper-spring-boot-starter` automatically loads the interceptor for the user, and it is impossible to control the order of the interceptors. Do not use `pagehelper-spring-boot-starter`.

**In general, the order of `pagehelper-spring-boot-starter` vs this plugin does not matter.**

Here is some example code:

```java

@Configuration
public class MybatisPluginAutoConfiguration {

	final List<SqlSessionFactory> sqlSessionFactoryList;

	@Bean
	@ConfigurationProperties(prefix = "pagehelper")
	public Properties pageHelperProperties() {
		return new Properties();
	}

    // To prevent the interceptor from being registered repeatedly, please make sure that `tenant.interceptor-auto-register` is already set to false
	@PostConstruct
	public void addMyInterceptor() {
		// Declaration of other interceptors (here is pageHelper).
		PageInterceptor pageInterceptor = new PageInterceptor();
		//Declaration of the multi-tenant interceptor object.
		TenantSqlInterceptor tenantSqlInterceptor = new TenantSqlInterceptor();
		pageInterceptor.setProperties(this.pageHelperProperties());
		for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
			// The actual working mode of mybatis interceptor is multi-layered proxy for the original implementation.
			// When added, it is a proxy to the pageInterceptor for the original implementation, and then the tenantSqlInterceptor proxy to the pageInterceptor.
			// Therefore, the final execution order is actually to execute tenantSqlInterceptor first, then execute pageInterceptor.
			sqlSessionFactory.getConfiguration().addInterceptor(pageInterceptor);
			sqlSessionFactory.getConfiguration().addInterceptor(tenantSqlInterceptor);
		}
	}

	public MybatisPluginAutoConfiguration(List<SqlSessionFactory> sqlSessionFactoryList) {
		this.sqlSessionFactoryList = sqlSessionFactoryList;
	}
}
```

### Configuration Parameter Description

Let's present an example using a .properties file:
* tenant.filter-additional: Filter additional filtering suffix. Filtering compliance mapper method name + this value will also be effective to the rules of [Multi-tenant Filter - TenantFilter]. This configuration appears mainly to prevent the use of PageHelper + filters when, mapper method added a filter logo. And because PageHelper generates sql as sqlId+_COUNT, it fails to successfully filter, and finally the pagination is far from expected.

* tenant.interceptor-auto-register: Indicates whether the interceptor should automatically register.
  By default, the interceptor registers automatically when the software executes. However, in some cases, it may be necessary to manually register the interceptor. This variable provides the flexibility to enable or disable automatic registration behavior.
  If this variable is set to `true`, the interceptor will automatically register. If the value is `false`, the interceptor will not automatically register, and manual registration must be performed.


* tenant.scan-mode: The multi-tenant sql interference mode. Auto automatic mode(default): Scan the entire library, and handle tables that have relevant multi-tenant fields automatically. This method will be slower to start than the specified mode, with the size of the number table gradually increasing.When starting. Assign specified mode: Do not automatically scan tables, and handle the tables in the tenant-include-tables list as multi-tenant tables for specific processing.


* tenant.target-columns: Multi-tenant related fields, multiple can be specified. Normally there should only be one, temporarily reserved for compatibility.


* tenant.target-tables: The scope of the multi-tenant specified table does not take effect under automatic mode. Such as table1,table2,table3


## Contact and Support

If you have any questions, doubts, or suggestions about Mybatis-Tenants-Plugin, please feel free to contact me. You can do so through the following methods:

- Submit a [GitHub issue](https://github.com/ashin092/Mybatis-Tenants-Plugin/issues)
- Contact us via email: xrh9110@gmail.com

  Your feedback is welcome and we will respond to your questions as soon as possible.

