package com.mychat.service.impl;

import com.mychat.utils.FfmpegUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final FfmpegUtil ffmpegUtil;
    private final ChatClient chatClient;

    /**
     * 抽取视频关键帧
     */
    public List<File> extractFrames(MultipartFile file, int intervalSec) throws Exception {
        // 1. 保存视频到临时文件
        Path tempVideo = Files.createTempFile("video-", ".mp4");
        file.transferTo(tempVideo.toFile());

        // 2. 调用FfmpegUtil抽帧
        List<String> framePaths = ffmpegUtil.extractFrames(
                tempVideo.toAbsolutePath().toString(),
                intervalSec
        );

        // 3. 将路径转为File对象
        List<File> frames = framePaths.stream()
                .map(File::new)
                .collect(Collectors.toList());

        // 4. 清理临时视频文件
        try {
            Files.deleteIfExists(tempVideo);
        } catch (Exception e) {
            log.warn("清理临时视频文件失败", e);
        }

        return frames;
    }

    /**
     * 视频总结（流式输出）
     */
    public Flux<String> summarizeVideo(MultipartFile file, String prompt, int intervalSec) {
        return Flux.create(sink -> {
            try {
                // 1. 保存上传的视频到临时文件
                Path tempVideo = Files.createTempFile("video-", ".mp4");
                file.transferTo(tempVideo.toFile());

                // 2. 抽帧
                List<String> framePaths = ffmpegUtil.extractFrames(
                        tempVideo.toAbsolutePath().toString(),
                        intervalSec
                );

                // 3. 将帧图片转为 Media 对象
                List<Media> medias = framePaths.stream()
                        .map(path -> Media.builder()
                                .mimeType(MimeType.valueOf("image/jpeg"))
                                .data(new File(path).toURI().toString())
                                .build())
                        .toList();

                // 4. 构造提示词
                String fullPrompt = String.format("""
                        这是一个视频分析任务。
                        视频已被抽取为关键帧图片（共%d张），请根据这些图片分析：
                        
                        1. 视频的主题是什么？
                        2. 画面中有哪些人物/物体？
                        3. 视频的核心内容是什么？
                        
                        用户的问题：%s
                        """, framePaths.size(), prompt);

                // 5. 调用多模态模型
                chatClient.prompt()
                        .user(u -> u.text(fullPrompt).media(medias.toArray(Media[]::new)))
                        .stream()
                        .content()
                        .subscribe(
                                sink::next,
                                error -> {
                                    log.error("分析失败", error);
                                    sink.error(error);
                                },
                                () -> {
                                    // 6. 清理临时文件
                                    try {
                                        ffmpegUtil.cleanupFrames(framePaths);
                                        Files.deleteIfExists(tempVideo);
                                    } catch (Exception e) {
                                        log.warn("清理临时文件失败", e);
                                    }
                                    sink.complete();
                                }
                        );

            } catch (Exception e) {
                log.error("视频处理失败", e);
                sink.error(e);
            }
        });
    }
}