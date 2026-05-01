package com.mychat.controller;

import com.mychat.service.impl.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/file")
public class FileController {
    private final VideoService videoService;
    private final ChatClient chatClient;

    // 视频总结接口
    @PostMapping("/video/summarize")
    public Flux<String> summarizeVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "请详细总结这个视频的内容") String prompt,
            @RequestParam(value = "interval", defaultValue = "2") int intervalSec) {
        return videoService.summarizeVideo(file, prompt, intervalSec);
    }
}
