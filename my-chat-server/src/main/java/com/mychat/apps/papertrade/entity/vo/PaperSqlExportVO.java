package com.mychat.apps.papertrade.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 历史表 SQL 导出结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaperSqlExportVO {

    /** 表名。 */
    private String tableName;

    /** 建议下载文件名，如 paper_research_run_20261116_213000.sql。 */
    private String filename;

    /** 全表 INSERT 脚本正文。 */
    private String content;
}
