package com.logai.mapper;

import com.logai.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户数据访问（共享 user 表，只读）。 */
@Mapper
public interface UserMapper {
    User findByUsername(@Param("username") String username);
}
