package com.mychat.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "文件向量化返回值(Vo)")
public class DocumentResponseVO {
    @Schema(description="文件唯一标识")
    String documentId;
    @Schema(description="文件名")
    String filename;
    @Schema(description="返回提示语")
    String message;
    @Schema(description="分片数量")
    int segmentCount;
}
