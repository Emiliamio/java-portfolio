package com.logai.mapper;

import com.logai.entity.AiAnalysis;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAnalysisMapper {

    int insert(AiAnalysis analysis);

    AiAnalysis findById(@Param("id") Long id);

    List<AiAnalysis> findRecent(@Param("limit") int limit);

    int countTotal();

    int deleteById(@Param("id") Long id);
}
