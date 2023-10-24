## [1.0.1] - under development
### Removed
- 依赖关系 `spring-boot-starter-jdbc`
### Changed
- 自动读取多租户表不再使用jdbcTemplate方式进行，因为项目基于mybatis，直接使用mybatis进行的sqlSession即可。
### Added
- 添加`excludeTables`配置项，排除不需要多租户的表。
- 添加插件对insert sql的多租户处理支持。

## [1.0.0] - 2023-10-19
### Changed
- 启用更新日志