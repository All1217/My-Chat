package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.AgentRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Map;

/** Agent Run Mapper。 */
@Mapper
public interface AgentRunMapper extends BaseMapper<AgentRun> {

    /** 仅允许更高版本推进 checkpoint，防止并发恢复发生旧状态覆盖。 */
    @Update("""
            UPDATE agent_run
               SET current_step = #{currentStep},
                   checkpoint_version = #{checkpointVersion},
                   checkpoint = #{checkpoint, typeHandler=com.mychat.mapper.typehandler.JsonbMapTypeHandler},
                   updated_at = #{updatedAt}
             WHERE id = #{runId}
               AND checkpoint_version < #{checkpointVersion}
            """)
    int advanceCheckpoint(
            @Param("runId") String runId,
            @Param("currentStep") int currentStep,
            @Param("checkpointVersion") int checkpointVersion,
            @Param("checkpoint") Map<String, Object> checkpoint,
            @Param("updatedAt") LocalDateTime updatedAt);
}
