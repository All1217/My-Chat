package com.mychat.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FfmpegUtil 单元测试
 * 测试视频抽帧功能是否能正常运行
 */
@Slf4j
@SpringBootTest  // 加载Spring上下文，让FfmpegUtil的@PostConstruct生效
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)  // 按顺序执行测试
class FfmpegUtilTest {

    @Autowired
    private FfmpegUtil ffmpegUtil;

    @TempDir
    Path tempDir;  // 每个测试方法都会有一个临时目录

    /**
     * 测试1：测试FFmpeg初始化是否成功
     */
    @Test
    @Order(1)
    @DisplayName("测试FFmpeg初始化")
    void testFfmpegInitialization() {
        assertThat(ffmpegUtil).isNotNull();
        // 通过调用抽帧方法来验证FFmpeg是否可用
        // 如果初始化失败，会在@PostConstruct时抛出异常
        log.info("FFmpeg初始化测试通过");
    }

    /**
     * 测试2：使用真实视频文件测试抽帧
     * 需要将测试视频放在 src/test/resources/test-video.mp4
     */
    @Test
    @Order(2)
    @DisplayName("测试从classpath加载视频并抽帧")
    void testExtractFramesFromClasspathVideo() throws Exception {
        // 从classpath加载测试视频
        InputStream videoStream = getClass().getResourceAsStream("/test-video.mp4");

        // 如果测试视频不存在，跳过这个测试
        if (videoStream == null) {
            log.warn("测试视频文件不存在，跳过测试。请将测试视频放在 src/test/resources/test-video.mp4");
            return;
        }

        // 将视频复制到临时目录
        Path videoPath = tempDir.resolve("test-video.mp4");
        Files.copy(videoStream, videoPath, StandardCopyOption.REPLACE_EXISTING);

        // 执行抽帧
        List<String> frames = ffmpegUtil.extractFrames(
                videoPath.toAbsolutePath().toString(),
                2  // 每2秒抽一帧
        );

        // 验证结果
        assertThat(frames).isNotNull();
        log.info("抽帧完成，共 {} 帧", frames.size());

        // 验证帧文件存在且是图片
        for (String framePath : frames) {
            File frameFile = new File(framePath);
            assertThat(frameFile).exists();
            assertThat(frameFile.length()).isGreaterThan(0);
            assertThat(framePath).endsWith(".jpg");
            log.info("帧文件: {} ({} bytes)", framePath, frameFile.length());
        }

        // 清理
        ffmpegUtil.cleanupFrames(frames);
    }

    /**
     * 测试3：使用MockMultipartFile测试（模拟文件上传）
     */
    @Test
    @Order(3)
    @DisplayName("测试使用MockMultipartFile上传视频并抽帧")
    void testExtractFramesFromMultipartFile() throws Exception {
        // 从classpath加载测试视频
        InputStream videoStream = getClass().getResourceAsStream("/test-video.mp4");

        if (videoStream == null) {
            log.warn("测试视频文件不存在，跳过测试");
            return;
        }

        // 创建MockMultipartFile
        MockMultipartFile multipartFile = new MockMultipartFile(
                "video",                    // 参数名
                "test-video.mp4",          // 原始文件名
                "video/mp4",               // 内容类型
                videoStream                // 文件内容
        );

        // 保存到临时文件
        Path videoPath = tempDir.resolve("uploaded-video.mp4");
        multipartFile.transferTo(videoPath.toFile());

        // 执行抽帧（间隔1秒，对于短视频更合适）
        List<String> frames = ffmpegUtil.extractFrames(
                videoPath.toAbsolutePath().toString(),
                1
        );

        // 验证
        assertThat(frames).isNotNull();
        log.info("从MultipartFile抽帧完成，共 {} 帧", frames.size());

        // 验证每个帧文件
        for (String framePath : frames) {
            File frameFile = new File(framePath);
            assertThat(frameFile).exists();
            assertThat(frameFile.length()).isGreaterThan(0);
        }

        // 清理
        ffmpegUtil.cleanupFrames(frames);
    }

    /**
     * 测试4：测试不同抽帧间隔
     */
    @Test
    @Order(4)
    @DisplayName("测试不同抽帧间隔")
    void testDifferentIntervals() throws Exception {
        InputStream videoStream = getClass().getResourceAsStream("/test-video.mp4");
        if (videoStream == null) {
            log.warn("测试视频文件不存在，跳过测试");
            return;
        }

        Path videoPath = tempDir.resolve("test-video-interval.mp4");
        Files.copy(videoStream, videoPath, StandardCopyOption.REPLACE_EXISTING);

        // 测试间隔5秒
        List<String> frames5s = ffmpegUtil.extractFrames(
                videoPath.toAbsolutePath().toString(), 5);
        log.info("间隔5秒抽帧: {} 帧", frames5s.size());
        ffmpegUtil.cleanupFrames(frames5s);

        // 测试间隔1秒
        List<String> frames1s = ffmpegUtil.extractFrames(
                videoPath.toAbsolutePath().toString(), 1);
        log.info("间隔1秒抽帧: {} 帧", frames1s.size());
        ffmpegUtil.cleanupFrames(frames1s);

        // 间隔越大，帧数应该越少
        assertThat(frames5s.size()).isLessThanOrEqualTo(frames1s.size());
    }

    /**
     * 测试5：测试清理功能
     */
    @Test
    @Order(5)
    @DisplayName("测试清理帧文件")
    void testCleanupFrames() throws Exception {
        InputStream videoStream = getClass().getResourceAsStream("/test-video.mp4");
        if (videoStream == null) {
            log.warn("测试视频文件不存在，跳过测试");
            return;
        }

        Path videoPath = tempDir.resolve("test-video-cleanup.mp4");
        Files.copy(videoStream, videoPath, StandardCopyOption.REPLACE_EXISTING);

        // 抽帧
        List<String> frames = ffmpegUtil.extractFrames(
                videoPath.toAbsolutePath().toString(), 2);

        // 验证文件存在
        for (String framePath : frames) {
            assertThat(new File(framePath)).exists();
        }

        // 清理
        ffmpegUtil.cleanupFrames(frames);

        // 验证文件已被删除
        for (String framePath : frames) {
            assertThat(new File(framePath)).doesNotExist();
        }

        log.info("清理测试通过");
    }

    /**
     * 测试6：测试不存在的视频文件（异常情况）
     */
    @Test
    @Order(6)
    @DisplayName("测试不存在的视频文件应抛出异常")
    void testNonExistentVideo() {
        String nonExistentPath = tempDir.resolve("non-existent-video.mp4").toAbsolutePath().toString();

        Exception exception = assertThrows(RuntimeException.class, () -> {
            ffmpegUtil.extractFrames(nonExistentPath, 2);
        });

        assertThat(exception.getMessage()).contains("抽帧失败");
        log.info("异常测试通过: {}", exception.getMessage());
    }

    /**
     * 测试7：实际抽帧并保留数据，用于查看抽帧结果
     * 运行后会在指定目录生成帧图片，不会自动清理
     */
    @Test
    @Order(7)
    @DisplayName("实际抽帧测试 - 保留帧文件供查看")
    void testExtractFramesAndKeepFiles() throws Exception {
        // 1. 从classpath加载测试视频
        InputStream videoStream = getClass().getResourceAsStream("/test-video.mp4");
        if (videoStream == null) {
            log.warn("测试视频文件不存在，请将测试视频放在 src/test/resources/test-video.mp4");
            return;
        }

        // 2. 创建一个固定的输出目录（在项目目录下，方便查看）
        String userDir = System.getProperty("user.dir");  // 项目根目录
        Path outputDir = Paths.get(userDir, "test-output", "frames-" + System.currentTimeMillis());
        Files.createDirectories(outputDir);

        // 3. 将测试视频复制到输出目录
        Path videoPath = outputDir.resolve("test-video.mp4");
        Files.copy(videoStream, videoPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("测试视频已复制到: {}", videoPath.toAbsolutePath());

        // 4. 执行抽帧（间隔2秒）
        log.info("开始抽帧...");
        List<String> frames = ffmpegUtil.extractFrames(
                videoPath.toAbsolutePath().toString(),
                2  // 每2秒抽一帧
        );

        // 5. 输出详细信息
        log.info("==========================================");
        log.info("抽帧完成！共 {} 帧", frames.size());
        log.info("帧文件保存在: {}", outputDir.toAbsolutePath());
        log.info("==========================================");

        // 6. 打印每一帧的详细信息
        for (int i = 0; i < frames.size(); i++) {
            String framePath = frames.get(i);
            File frameFile = new File(framePath);

            log.info("帧 {}:", (i + 1));
            log.info("  文件名: {}", frameFile.getName());
            log.info("  完整路径: {}", frameFile.getAbsolutePath());
            log.info("  文件大小: {} bytes ({} KB)",
                    frameFile.length(),
                    String.format("%.2f", frameFile.length() / 1024.0));
            log.info("  最后修改: {}", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(frameFile.lastModified())));
            log.info("  ---");
        }

        // 7. 列出输出目录的所有文件
        log.info("输出目录内容:");
        try (java.util.stream.Stream<Path> files = Files.list(outputDir)) {
            files.forEach(path -> {
                File f = path.toFile();
                log.info("  {} ({} bytes)", f.getName(), f.length());
            });
        }

        // 8. 打印访问提示
        log.info("==========================================");
        log.info("提示：可以在文件管理器中打开以下目录查看帧图片：");
        log.info("  {}", outputDir.toAbsolutePath());
        log.info("==========================================");

        // 注意：这里没有调用 cleanupFrames，所以文件会被保留
        // 你可以手动去输出目录查看生成的帧图片
    }
}