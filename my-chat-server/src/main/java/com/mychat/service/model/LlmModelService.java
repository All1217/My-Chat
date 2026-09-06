package com.mychat.service.model;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.dto.LlmModelTestRequest;
import com.mychat.entity.dto.LlmModelUpsertRequest;
import com.mychat.entity.po.LlmModel;
import com.mychat.vo.LlmModelTestResultVO;
import com.mychat.vo.LlmModelVO;
import com.mychat.vo.LlmProviderPresetVO;

import java.util.List;

/** 对话模型目录：CRUD、设默认、测通。 */
public interface LlmModelService extends IService<LlmModel> {

    /** 返回脱敏后的模型列表，默认行靠前。 */
    List<LlmModelVO> listMasked();

    /** 供应商预设，供设置页回填 Base URL。 */
    List<LlmProviderPresetVO> listProviders();

    /** 新建配置；空表或 isDefault 时设为全局默认。 */
    LlmModelVO create(LlmModelUpsertRequest request);

    /** 更新配置；apiKey 空则保留原值。 */
    LlmModelVO updateModel(LlmModelUpsertRequest request);

    /** 删除非默认配置；最后一条启用模型不可删。 */
    void deleteModel(String id);

    /** 把指定启用行设为全局默认并清 ChatClient 缓存。 */
    LlmModelVO setDefault(String id);

    /** 用已存或表单配置发一句短 prompt，不写缓存。 */
    LlmModelTestResultVO testConnection(LlmModelTestRequest request);
}
