package com.logaudit.mapper;

import com.logaudit.entity.User;
import org.apache.ibatis.annotations.Param;

/** 用户数据访问。 */
public interface UserMapper {
    User findByUsername(@Param("username") String username);

    int countUsers();

    int insert(User user);
}
