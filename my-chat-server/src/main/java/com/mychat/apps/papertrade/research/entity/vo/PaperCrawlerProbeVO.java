package com.mychat.apps.papertrade.research.entity.vo;

import lombok.Data;

/**
 * 本地爬虫 MCP 连通探测结果。
 * status：DISABLED / NO_CLIENT / ERROR / OK
 */
@Data
public class PaperCrawlerProbeVO {

    private boolean ok;
    private String status;
    private String message;
    /** 成功时附带适配器等摘要；失败时可放异常片段。 */
    private String detail;

    /** 构造探测结果。 */
    public static PaperCrawlerProbeVO of(boolean ok, String status, String message, String detail) {
        PaperCrawlerProbeVO vo = new PaperCrawlerProbeVO();
        vo.setOk(ok);
        vo.setStatus(status);
        vo.setMessage(message);
        vo.setDetail(detail);
        return vo;
    }
}
