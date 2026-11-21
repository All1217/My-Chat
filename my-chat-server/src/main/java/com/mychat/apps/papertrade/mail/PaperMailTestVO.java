package com.mychat.apps.papertrade.mail;

import lombok.Data;

/** 发信结果：SENT / SKIPPED / FAILED，message 给人看、不含密码。 */
@Data
public class PaperMailTestVO {

    private String status;
    private String message;

    /** 构造发信结果。 */
    public static PaperMailTestVO of(String status, String message) {
        PaperMailTestVO vo = new PaperMailTestVO();
        vo.setStatus(status);
        vo.setMessage(message);
        return vo;
    }
}
