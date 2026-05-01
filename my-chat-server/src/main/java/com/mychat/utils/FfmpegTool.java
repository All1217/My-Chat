package com.mychat.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class FfmpegTool {
    private String ffmpegPath;
    private String ffprobePath;

    @PostConstruct
    public void init() throws Exception {
        // 1. 检测操作系统
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch");

        String osDir;
        if (os.contains("win")) {
            osDir = "windows";
        } else if (os.contains("mac")) {
            osDir = "mac";
        } else {
            osDir = "linux";
        }

        // 2. 从classpath复制FFmpeg到临时目录
        Path tempDir = Files.createTempDirectory("ffmpeg-");
        tempDir.toFile().deleteOnExit();

        // 复制ffmpeg
        String ffmpegResource = "/ffmpeg/" + osDir + "/ffmpeg" + (os.contains("win") ? ".exe" : "");
        copyResource(ffmpegResource, tempDir.resolve("ffmpeg" + (os.contains("win") ? ".exe" : "")));

        // 复制ffprobe
        String ffprobeResource = "/ffmpeg/" + osDir + "/ffprobe" + (os.contains("win") ? ".exe" : "");
        copyResource(ffprobeResource, tempDir.resolve("ffprobe" + (os.contains("win") ? ".exe" : "")));

        // 3. 设置可执行权限（Linux/Mac）
        if (!os.contains("win")) {
            tempDir.resolve("ffmpeg").toFile().setExecutable(true);
            tempDir.resolve("ffprobe").toFile().setExecutable(true);
        }

        ffmpegPath = tempDir.resolve("ffmpeg" + (os.contains("win") ? ".exe" : "")).toAbsolutePath().toString();
        ffprobePath = tempDir.resolve("ffprobe" + (os.contains("win") ? ".exe" : "")).toAbsolutePath().toString();

        // 4. 验证安装
        verifyInstallation();

        log.info("FFmpeg初始化成功，路径: {}", ffmpegPath);
    }

    private void copyResource(String resourcePath, Path targetPath) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException("找不到资源: " + resourcePath);
            }
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void verifyInstallation() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-version");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.contains("ffmpeg")) {
                throw new RuntimeException("FFmpeg验证失败");
            }
            log.info("FFmpeg版本: {}", firstLine);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg验证失败，退出码: " + exitCode);
        }
    }

    /**
     * 获取FFmpeg路径
     */
    public String getFfmpegPath() {
        return ffmpegPath;
    }

    /**
     * 获取FFprobe路径
     */
    public String getFfprobePath() {
        return ffprobePath;
    }

    /**
     * 执行FFmpeg命令
     */
    public Process executeCommand(List<String> args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }
}
