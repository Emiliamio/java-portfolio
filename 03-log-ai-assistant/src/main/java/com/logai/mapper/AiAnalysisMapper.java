package com.logai.mapper;

import com.logai.entity.AiAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAnalysisMapper {

    int insert(AiAnalysis analysis);

    AiAnalysis findById(@Param("id") Long id);

    /** 查询指定用户的最近记录（历史隔离）。 */
    List<AiAnalysis> findRecentByUser(@Param("username") String username, @Param("limit") int limit);

    /** 查询最近记录（无用户名过滤，兼容旧逻辑）。 */
    List<AiAnalysis> findRecent(@Param("limit") int limit);

    /** 指定用户查看自己的单条记录。 */
    AiAnalysis findByIdAndUser(@Param("id") Long id, @Param("username") String username);

    int countTotal();

    int countByUser(@Param("username") String username);

    int deleteById(@Param("id") Long id);
}
