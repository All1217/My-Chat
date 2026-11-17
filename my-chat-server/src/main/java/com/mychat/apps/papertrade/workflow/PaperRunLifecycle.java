package com.mychat.apps.papertrade.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/** 领域 run 互斥与 PENDING→RUNNING 条件更新。 */
@Component
public class PaperRunLifecycle {

    /** 提交前检查是否已有活动轮次。 */
    public <T> void requireNoActive(
            BaseMapper<T> mapper,
            Function<LambdaQueryWrapper<T>, LambdaQueryWrapper<T>> activeQuery) {
        Long count = mapper.selectCount(activeQuery.apply(new LambdaQueryWrapper<>()));
        if (count != null && count > 0) {
            throw new IllegalStateException("已有进行中的同类轮次，请等待结束后再提交");
        }
    }

    /** 仅当当前为 PENDING 时切入 RUNNING；否则视为重复投递。 */
    public <T> boolean tryMarkRunning(
            BaseMapper<T> mapper,
            Function<LambdaUpdateWrapper<T>, LambdaUpdateWrapper<T>> pendingToRunning) {
        int updated = mapper.update(null, pendingToRunning.apply(new LambdaUpdateWrapper<>()));
        return updated > 0;
    }

    /** 活动状态集合。 */
    public static List<String> activeStatuses(String pending, String running) {
        return List.of(pending, running);
    }
}
