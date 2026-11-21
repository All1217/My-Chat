package com.mychat.apps.papertrade.mail;

import lombok.Data;

/** 测试发信：to 为设置页当前输入的收件人，不要求先保存。 */
@Data
public class PaperMailTestRequest {

    private String to;
}
