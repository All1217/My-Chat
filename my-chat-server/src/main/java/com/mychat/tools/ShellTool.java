package com.mychat.tools;

import com.mychat.utils.WorkspaceUtil;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * AI 可调用的系统 shell。用于构建、git、脚本；查看和改写文件用 {@link FileTools}。
 * 进程工作目录是当前工作区，超时后终止进程树。
 */
@Component
public class ShellTool {

    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_CHARS = 20_000;

    /** 只拦关机、格式化磁盘和 diskpart，不做命令白名单。 */
    private static final Pattern BLOCKED = Pattern.compile(
            "(?i)(\\bshutdown\\b|\\brestart-computer\\b|\\bstop-computer\\b"
                    + "|\\bdiskpart\\b|\\bformat-volume\\b|\\bmkfs\\b|\\bformat\\s+[a-z]:)");

    private final WorkspaceUtil workspaceUtil;

    public ShellTool(WorkspaceUtil workspaceUtil) {
        this.workspaceUtil = workspaceUtil;
    }

    /** 在工作区执行一条非交互命令，返回退出码和合并输出。 */
    @Tool(description = "在当前工作区执行一条非交互 shell 命令，返回退出码以及合并后的标准输出和标准错误。"
            + "用于构建、git、脚本。"
            + "查看、创建、修改、删除文件请使用 FileTools，不要用本工具读写文件。")
    public String executeCommand(
            @ToolParam(description = "要执行的命令，如 git status、mvn -q test") String command,
            ToolContext context) {
        if (command == null || command.isBlank()) {
            return "错误：命令不能为空";
        }
        String trimmed = command.trim();
        if (BLOCKED.matcher(trimmed).find()) {
            return "安全拦截: 拒绝执行关机、格式化磁盘或 diskpart";
        }
        Path cwd = workspaceUtil.getWorkspaceRoot(workDir(context));
        try {
            return run(cwd, trimmed);
        } catch (IOException exception) {
            return "执行失败: " + exception.getMessage();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "执行被中断";
        }
    }

    /** 从 ToolContext 读取 Runtime 显式工作区。 */
    private String workDir(ToolContext context) {
        if (context == null || context.getContext().get("workDir") == null) {
            return null;
        }
        return String.valueOf(context.getContext().get("workDir"));
    }

    /**
     * 启动 shell 并收集输出。
     * 先读管道再等待退出，避免输出缓冲区写满导致进程挂死。
     */
    private String run(Path cwd, String command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(commandLine(command));
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        boolean[] truncated = {false};
        Thread reader = new Thread(() -> readOutput(process, output, truncated), "shell-tool-output");
        reader.start();

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            destroy(process);
        }
        reader.join(2_000);

        String text = output.toString();
        if (truncated[0]) {
            text = text + "\n... [输出过长，已截断]";
        }
        if (!finished) {
            return "执行超时（" + TIMEOUT_SECONDS + " 秒），已终止进程\n退出码: 超时\n" + text;
        }
        return "退出码: " + process.exitValue() + "\n" + text;
    }

    /** 按操作系统包装命令，Windows 强制 UTF-8 输出。 */
    private List<String> commandLine(String command) {
        if (isWindows()) {
            String wrapped = "$OutputEncoding = [Console]::OutputEncoding = "
                    + "[System.Text.UTF8Encoding]::new($false); " + command;
            return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", wrapped);
        }
        return List.of("sh", "-c", command);
    }

    /** 当前进程是否运行在 Windows。 */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /** 持续读取合并输出，超过上限后只排空管道不再追加。 */
    private void readOutput(Process process, StringBuilder output, boolean[] truncated) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                if (truncated[0]) {
                    continue;
                }
                int room = MAX_OUTPUT_CHARS - output.length();
                if (count <= room) {
                    output.append(buffer, 0, count);
                } else {
                    if (room > 0) {
                        output.append(buffer, 0, room);
                    }
                    truncated[0] = true;
                }
            }
        } catch (IOException ignored) {
            // 进程被终止时读取结束
        }
    }

    /** 超时后先结束子进程，再结束命令本身。 */
    private void destroy(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
