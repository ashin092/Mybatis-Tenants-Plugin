package com.github.tenants.plugin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author xierh
 * @since 2023/10/23 11:49
 */
@Mapper
public interface StructureMapper {

    @Select("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME = #{columnName}")
    List<String> queryTablesByColumnName(String columnName);
}
