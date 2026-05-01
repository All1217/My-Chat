package com.mychat.tools;

import com.mychat.utils.FfmpegUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class FFmpegTool {
    @Autowired
    private FfmpegUtil ffmpegUtil;

    /**
     * 从视频文件中抽取关键帧，返回帧图片的保存路径列表
     *
     * @param videoPath   视频文件的完整路径
     * @param intervalSec 抽帧间隔（秒），例如2表示每2秒抽一帧
     * @return 帧图片文件的完整路径列表，每张图片为JPEG格式
     */
    @Tool(description = "从视频文件中抽取关键帧，返回帧图片的保存路径列表。参数：videoPath-视频文件路径，intervalSec-抽帧间隔秒数")
    public List<String> extractFrames(String videoPath, int intervalSec) {
        log.info("AI请求抽帧: videoPath={}, intervalSec={}", videoPath, intervalSec);
        try {
            // 使用FfmpegUtil执行抽帧
            List<String> frames = ffmpegUtil.extractFrames(videoPath, intervalSec);
            log.info("抽帧完成，共 {} 帧", frames.size());
            return frames;
        } catch (Exception e) {
            log.error("抽帧失败", e);
            throw new RuntimeException("视频抽帧失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从视频文件中抽取关键帧，并保存到workspace目录
     * 本产品的工作空间目录为 src/main/resources/workspace
     *
     * @param videoPath   视频文件的完整路径
     * @param intervalSec 抽帧间隔（秒）
     * @param sessionId   会话ID，用于组织输出目录
     * @return 帧图片文件的完整路径列表
     */
    @Tool(description = "从视频文件中抽取关键帧并保存到工作空间。参数：videoPath-视频文件路径，intervalSec-抽帧间隔秒数，sessionId-会话ID用于组织目录")
    public List<String> extractFramesToWorkspace(String videoPath, int intervalSec, String sessionId) {
        log.info("AI请求抽帧到工作空间: videoPath={}, intervalSec={}, sessionId={}",
                videoPath, intervalSec, sessionId);
        try {
            // 构建workspace中的输出目录
            String userDir = System.getProperty("user.dir");
            Path workspaceDir = Paths.get(userDir, "src", "main", "resources", "workspace",
                    "frames", sessionId, UUID.randomUUID().toString());
            Files.createDirectories(workspaceDir);
            // 使用FfmpegUtil执行抽帧，指定输出目录
            List<String> frames = ffmpegUtil.extractFrames(videoPath, intervalSec,
                    workspaceDir.toAbsolutePath().toString());
            log.info("抽帧完成，共 {} 帧，保存位置: {}", frames.size(), workspaceDir);
            return frames;
        } catch (Exception e) {
            log.error("抽帧失败", e);
            throw new RuntimeException("视频抽帧失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取视频文件的基本信息（时长、大小等）
     *
     * @param videoPath 视频文件的完整路径
     * @return 视频信息字符串
     */
    @Tool(description = "获取视频文件的基本信息，包括时长、大小等。参数：videoPath-视频文件路径")
    public String getVideoInfo(String videoPath) {
        try {
            File videoFile = new File(videoPath);
            if (!videoFile.exists()) {
                return "视频文件不存在: " + videoPath;
            }
            long fileSizeBytes = videoFile.length();
            double fileSizeMB = fileSizeBytes / (1024.0 * 1024.0);

            // 使用ffprobe获取视频时长
            double duration = ffmpegUtil.getVideoDuration(videoPath);

            return String.format(
                    "视频信息:\n- 文件路径: %s\n- 文件大小: %.2f MB\n- 时长: %.1f 秒\n- 文件名: %s",
                    videoPath, fileSizeMB, duration, videoFile.getName()
            );
        } catch (Exception e) {
            log.error("获取视频信息失败", e);
            return "获取视频信息失败: " + e.getMessage();
        }
    }
}
