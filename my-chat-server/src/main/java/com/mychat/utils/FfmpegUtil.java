package com.mychat.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Component
public class FfmpegUtil {
    private String ffmpegPath;
    private String ffprobePath;

    @PostConstruct
    public void init() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String exeSuffix = os.contains("win") ? ".exe" : "";
            String osDir = os.contains("win") ? "windows" :
                    os.contains("mac") ? "mac" : "linux";

            Path tempDir = Files.createTempDirectory("ffmpeg-");
            tempDir.toFile().deleteOnExit();

            // 复制 ffmpeg
            String ffmpegResource = "/ffmpeg/" + osDir + "/ffmpeg" + exeSuffix;
            Path ffmpegTarget = tempDir.resolve("ffmpeg" + exeSuffix);
            try (InputStream in = getClass().getResourceAsStream(ffmpegResource)) {
                if (in == null) {
                    ffmpegPath = findSystemFfmpeg();
                    log.info("使用系统FFmpeg: {}", ffmpegPath);
                    return;
                }
                Files.copy(in, ffmpegTarget, StandardCopyOption.REPLACE_EXISTING);
            }

            // 复制 ffprobe（在ffmpeg的bin目录下通常也有ffprobe）
            String ffprobeResource = "/ffmpeg/" + osDir + "/ffprobe" + exeSuffix;
            Path ffprobeTarget = tempDir.resolve("ffprobe" + exeSuffix);
            try (InputStream in = getClass().getResourceAsStream(ffprobeResource)) {
                if (in != null) {
                    Files.copy(in, ffprobeTarget, StandardCopyOption.REPLACE_EXISTING);
                    if (!os.contains("win")) {
                        ffprobeTarget.toFile().setExecutable(true);
                    }
                    ffprobePath = ffprobeTarget.toAbsolutePath().toString();
                }
            }

            // 设置可执行权限
            if (!os.contains("win")) {
                ffmpegTarget.toFile().setExecutable(true);
            }

            ffmpegPath = ffmpegTarget.toAbsolutePath().toString();

            // 如果ffprobe没有单独的资源文件，尝试从ffmpeg路径推导
            if (ffprobePath == null) {
                ffprobePath = ffmpegPath.replace("ffmpeg" + exeSuffix, "ffprobe" + exeSuffix);
            }

            log.info("FFmpeg初始化成功: {}", ffmpegPath);
            log.info("FFprobe初始化成功: {}", ffprobePath);

        } catch (Exception e) {
            log.error("FFmpeg初始化失败", e);
            throw new RuntimeException("FFmpeg初始化失败", e);
        }
    }

    private String findSystemFfmpeg() throws Exception {
        String[] commands = System.getProperty("os.name").toLowerCase().contains("win")
                ? new String[]{"where", "ffmpeg"}
                : new String[]{"which", "ffmpeg"};

        Process process = new ProcessBuilder(commands)
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String path = reader.readLine();
            if (path != null && !path.isEmpty()) {
                return path.trim();
            }
        }
        throw new RuntimeException("系统中未找到FFmpeg");
    }

    /**
     * 抽取视频关键帧
     *
     * @param videoPath   视频文件路径
     * @param intervalSec 抽帧间隔（秒）
     * @return 帧图片文件路径列表
     */
    public List<String> extractFrames(String videoPath, int intervalSec) {
        List<String> frames = new ArrayList<>();

        try {
            // 创建输出目录
            String outputDir = System.getProperty("java.io.tmpdir") + "/frames/" + UUID.randomUUID();
            Files.createDirectories(Paths.get(outputDir));

            // FFmpeg命令
            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(videoPath);
            command.add("-vf");
            command.add("fps=1/" + intervalSec);
            command.add("-q:v");
            command.add("2");
            command.add(outputDir + "/frame_%03d.jpg");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg抽帧失败，退出码: " + exitCode);
            }

            // 收集生成的帧文件
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                    Paths.get(outputDir), "*.jpg")) {
                for (Path path : stream) {
                    frames.add(path.toAbsolutePath().toString());
                }
            }

            frames.sort(Comparator.naturalOrder());
            log.info("抽帧完成，共 {} 帧", frames.size());

        } catch (Exception e) {
            log.error("抽帧失败", e);
            throw new RuntimeException("视频抽帧失败", e);
        }

        return frames;
    }

    /**
     * 抽取视频关键帧（可指定输出目录）
     *
     * @param videoPath   视频文件路径
     * @param intervalSec 抽帧间隔（秒）
     * @param outputDir   输出目录路径
     * @return 帧图片文件路径列表
     */
    public List<String> extractFrames(String videoPath, int intervalSec, String outputDir) {
        List<String> frames = new ArrayList<>();

        try {
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            List<String> command = new ArrayList<>();
            command.add(ffmpegPath);
            command.add("-i");
            command.add(videoPath);
            command.add("-vf");
            command.add("fps=1/" + intervalSec);
            command.add("-q:v");
            command.add("2");
            command.add(outputPath.toAbsolutePath() + "/frame_%03d.jpg");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.debug("FFmpeg: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("FFmpeg抽帧失败，退出码: " + exitCode);
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(outputPath, "*.jpg")) {
                for (Path path : stream) {
                    frames.add(path.toAbsolutePath().toString());
                }
            }

            frames.sort(Comparator.naturalOrder());
            log.info("抽帧完成，共 {} 帧", frames.size());

        } catch (Exception e) {
            log.error("抽帧失败", e);
            throw new RuntimeException("视频抽帧失败", e);
        }

        return frames;
    }

    /**
     * 清理临时帧文件
     */
    public void cleanupFrames(List<String> framePaths) {
        for (String path : framePaths) {
            try {
                Files.deleteIfExists(Paths.get(path));
            } catch (IOException e) {
                log.warn("清理帧文件失败: {}", path);
            }
        }
    }

    /**
     * 获取视频时长（秒）
     *
     * @param videoPath 视频文件路径
     * @return 视频时长（秒）
     */
    public double getVideoDuration(String videoPath) {
        try {
            List<String> command = new ArrayList<>();
            command.add(ffprobePath != null ? ffprobePath :
                    ffmpegPath.replace("ffmpeg", "ffprobe"));
            command.add("-v");
            command.add("error");
            command.add("-show_entries");
            command.add("format=duration");
            command.add("-of");
            command.add("default=noprint_wrappers=1:nokey=1");
            command.add(videoPath);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String duration = reader.readLine();
                if (duration != null && !duration.isEmpty()) {
                    return Double.parseDouble(duration.trim());
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("获取视频时长失败，退出码: " + exitCode);
            }

            return 0;
        } catch (Exception e) {
            log.error("获取视频时长失败", e);
            throw new RuntimeException("获取视频时长失败", e);
        }
    }
}
