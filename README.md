# Mybatis-Tenants-Plugin

Mybatis-Tenants-Plugin 是一个基于 MyBatis 的拦截器接口扩展的spring-boot-starter插件，它是专门解决处理多租户问题的解决方案。通过用户自定义获取租户的方式，它为开发者提供了一种更简单，快捷的方式来适配并管理多租户应用。

插件专注于在开发多租户应用程序时，如何更方便地管理多个租户的数据。该插件让你能够避免大量重复的代码编写，大大提高了开发效率，使得你可以更加专注于业务逻辑的实现，而不是数据访问。

设计该插件的目标是让开发者更自如、灵活地处理多租户的问题，并提供一种可扩展、易用和便于集成的方法以解决此类问题。

## Features

插件开箱即用，会将数据库中存在特定字段的表的查询/插入sql执行时，自动为sql加入租户字段以进行过滤。

举个栗子，假设`table1`是一张涉及多租户的表，多租户字段为`tenants_id`，原始sql是：
```mysql
  select * from table1 t1 
  inner join 
  (select * from table1 where id in (1,2,3)) t12 on t1.id = t12.id
```
安装配置好插件后，实际执行的sql将会变如下所示，插件会帮开发者将多租户的参数进行过滤。
```mysql
  select * from table1 t1 
  inner join 
  (select * from table1 where id in (1,2,3) and tenants_id = ?) t12 on t1.id = t12.id  where t1.tenants_id = ?
```
_*在这个例子中，`tenants_id = ?`的实际运行赋值与项目对`TenantUserIdentity`的继承实现有关_

## 安装和配置

你需要具备以下条件来运行这个项目：

- Java SDK version 8 （其他版本待测试）
- 框架 Spring
- 框架 mybatis（3.5.6 其他版本未测试）/  mybatisPlus

## 支持的数据库
- mysql
- 待拓展



要安装此插件，请将 jar 包添加到你的项目依赖中。
或在pom.xml文件中添加以下坐标:
```xml
    <dependency>
        <groupId>com.bitchain</groupId>
        <artifactId>mybatis-tenants-plugin</artifactId>
        <version>1.0.0</version>
    </dependency>
```

## 使用方法

### 实现抽象类`TenantUserIdentity`

`TenantUserIdentity`中提供了一个抽象方法给用户自定义获取租户的方式：

``` 
    /**
     * 检索租户用户标识。
     *
     * @return 作为数字的租户用户标识。
     */
    public abstract Long getTenantUserIdentity(); 
```

需要实现 `TenantUserIdentity` 抽象类，并提供对应的 `getTenantUserIdentity` 方法的具体实现。框架会自动识别并使用这个实现进行sql的解析和多租户注入。
框架内部实际是使用责任链模式来获取租户用户标识，因此可以有多个`TenantUserIdentity`的子类，当具体实现出现异常或者返回了`null`值时，会进行下一个责任点的处理。

你也需要将`TenantUserIdentity`的注册为Spring容器的Bean，在类上声明`@Component`等注解，或在配置类中`@Bean`均可以让框架识别使用该实现。

方式一：在实现类上标注Spring组件，@Component或@Service等。
```java
    @Component
    public class TenantUsersGetImpl extends TenantUserIdentity{
        
        @Override
        public Long getTenantUserIdentity() {
            // Your implementation...
        }
    }
```

方式二：配置代码中使用Bean声明。
```java
    @Configuration
    public class TenantUsersConfig {

    @Bean
    public TenantUsersGetImpl addTenantUsersGetImpl(){
        return new TenantUsersGetImpl();
        }
    }
```
当多个实现需要处理责任顺序时，请使用`@TenantChainOrder`调整责任顺序，**值越小，则顺序越靠前**。

### 添加拦截器到Mybatis拦截插件中

实际上，为了用户能够开箱即用，**如果用户不存在需要调整拦截器顺序的需求，这一部分可以直接忽略**，以下的说明是为了处理存在多个mybatis拦截器，需要调整拦截器生效顺序的情况。

为了保证灵活性和与其他类型拦截器的顺序可控，插件提供了一个可配置项`tenant.interceptor-auto-register`，该项默认为true，将该项调整为false即可让拦截器不进行自动注册。

如果有多个拦截器存在的情况下，同时需要控制拦截器执行的顺序，请参考下面的方式进行拦截器的注册。请注意，这里的示例代码中pageHelper的工件用的`pagehelper`而不是`pagehelper-spring-boot-starter`，由于`pagehelper-spring-boot-starter`已经自动帮用户加载了拦截器，已经无法控制拦截器顺序，请不要使用`pagehelper-spring-boot-starter`。

**一般来说，`pagehelper-spring-boot-starter`与本插件的顺序先后并没有任何影响。**

以下是示例代码：

```java
@Configuration
public class MybatisPluginAutoConfiguration {

	final List<SqlSessionFactory> sqlSessionFactoryList;

	@Bean
	@ConfigurationProperties(prefix = "pagehelper")
	public Properties pageHelperProperties() {
		return new Properties();
	}

    //为了防止拦截器反复注册，请确保`tenant.interceptor-auto-register`已经设置为false
	@PostConstruct
	public void addMyInterceptor() {
		// 其他拦截器（这里是pageHelper）的声明。
		PageInterceptor pageInterceptor = new PageInterceptor();
		//多租户拦截器的对象声明。
		TenantSqlInterceptor tenantSqlInterceptor = new TenantSqlInterceptor();
		pageInterceptor.setProperties(this.pageHelperProperties());
		for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
			//mybatis拦截器的工作模式实际上是多层对原始的代理，
			//加入的时候是pageInterceptor对原始实现进行代理处理，然后是tenantSqlInterceptor对pageInterceptor进行代理，
			//因此最后执行顺序实际是先执行tenantSqlInterceptor,再执行pageInterceptor
			sqlSessionFactory.getConfiguration().addInterceptor(pageInterceptor);
			sqlSessionFactory.getConfiguration().addInterceptor(tenantSqlInterceptor);
		}
	}

	public MybatisPluginAutoConfiguration(List<SqlSessionFactory> sqlSessionFactoryList) {
		this.sqlSessionFactoryList = sqlSessionFactoryList;
	}
}
```

## 配置参数说明
以下以.properties文件举例：
*  tenant.filter-additional：过滤器附加过滤后缀。 过滤符合mapper方法名 + 该项值的也会生效于【多租户过滤器 - TenantFilter】的规则。 该项配置的出现主要是为了防止使用PageHelper + 过滤器的时候，mapper method 加上了过滤标识。又因为PageHelper生成的sql为sqlId+_COUNT,导致无法成功过滤， 最后分页与预期大不同的情况。


* tenant.interceptor-auto-register：指示拦截器是否应自动注册。 <p> 默认情况下，拦截器在软件执行时自动注册。 但是，在某些情况下，可能需要手动注册拦截器。 此变量提供了启用或禁用自动注册行为的灵活性。 </p> <p> 如果此变量的值设置为 {@code true}，拦截器将自动注册。 如果值为 {@code false}，则拦截器不会自动注册，必须执行手动注册。


* tenant.scan-mode：多租户sql干涉模式。 Auto自动模式(默认)：扫描全库，存在符合多租户相关字段的表自动处理。 该方式启动时间会相较于指定模式慢，随着数库表大小逐渐增加。 Assign指定模式：不自动扫表，采用tenant-include-tables列表中的表作为多租户的表指定处理。


* tenant.target-columns:多租户相关字段，可以指定多个.正常情况下应该只有一个，暂时预留兼容情况。


* tenant.target-tables:多租户指定表范围，自动模式下不生效。如table1,table2,table3

## 联系和支持

如果您有任何关于Mybatis-Tenants-Plugin的问题、疑虑或建议，欢迎联系我。您可以通过以下方式：

- 提交一个 [GitHub issue](https://github.com/ashin092/Mybatis-Tenants-Plugin/issues)
- 通过电子邮件联系我们：xrh9110@gmail.com

对您的反馈表示欢迎，并会尽快回复您的问题。
