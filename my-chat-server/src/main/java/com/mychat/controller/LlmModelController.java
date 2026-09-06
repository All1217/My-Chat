package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.entity.dto.LlmModelTestRequest;
import com.mychat.entity.dto.LlmModelUpsertRequest;
import com.mychat.service.model.LlmModelService;
import com.mychat.vo.LlmModelTestResultVO;
import com.mychat.vo.LlmModelVO;
import com.mychat.vo.LlmProviderPresetVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 大模型目录 API：列表、供应商预设、增删改、设默认、测通。 */
@Slf4j
@RestController
@RequestMapping("/ai/model")
@AllArgsConstructor
public class LlmModelController {

    private final LlmModelService llmModelService;

    @GetMapping("/list")
    public Result<List<LlmModelVO>> list() {
        return Result.ok(llmModelService.listMasked());
    }

    @GetMapping("/providers")
    public Result<List<LlmProviderPresetVO>> providers() {
        return Result.ok(llmModelService.listProviders());
    }

    @PostMapping("/create")
    public Result<LlmModelVO> create(@RequestBody LlmModelUpsertRequest request) {
        try {
            return Result.ok(llmModelService.create(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PostMapping("/update")
    public Result<LlmModelVO> update(@RequestBody LlmModelUpsertRequest request) {
        try {
            return Result.ok(llmModelService.updateModel(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam String id) {
        try {
            llmModelService.deleteModel(id);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PostMapping("/set-default")
    public Result<LlmModelVO> setDefault(@RequestParam String id) {
        try {
            return Result.ok(llmModelService.setDefault(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 用已存或未保存表单发短 prompt，确认 key / url / 模型名可用。 */
    @PostMapping("/test")
    public Result<LlmModelTestResultVO> test(@RequestBody LlmModelTestRequest request) {
        try {
            return Result.ok(llmModelService.testConnection(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("模型测通失败", e);
            return Result.fail(500, e.getMessage() != null ? e.getMessage() : "测通失败");
        }
    }
}
