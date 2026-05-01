package com.mychat.service.impl;

import com.mychat.utils.FfmpegUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VideoService 集成测试
 * 测试从文件上传到抽帧的完整流程
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VideoServiceTest {

    @Autowired
    private VideoService videoService;

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    @DisplayName("测试VideoService的extractFrames方法")
    void testExtractFrames() throws Exception {
        // 从classpath加载测试视频
        InputStream videoStream = getClass().getResourceAsStream("/test-video.mp4");
        if (videoStream == null) {
            log.warn("测试视频文件不存在，跳过测试");
            return;
        }

        // 创建MockMultipartFile
        MockMultipartFile multipartFile = new MockMultipartFile(
                "video",
                "test-video.mp4",
                "video/mp4",
                videoStream
        );

        // 调用VideoService的extractFrames方法
        List<java.io.File> frames = videoService.extractFrames(multipartFile, 2);

        // 验证
        assertThat(frames).isNotNull();
        assertThat(frames).isNotEmpty();

        log.info("VideoService抽帧测试通过，共 {} 帧", frames.size());

        for (java.io.File frame : frames) {
            assertThat(frame).exists();
            assertThat(frame.length()).isGreaterThan(0);
            log.info("帧文件: {} ({} bytes)", frame.getName(), frame.length());
        }
    }
}
