package com.mychat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "异步任务（API / SSE 载荷）")
public class AsyncJobVO {
    private String id;
    private String jobType;
    private String status;
    private String title;
    private String refId;
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
    private String finishedAt;
}
