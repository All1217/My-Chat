package com.mychat.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 安全的只读 Shell 命令执行工具
 * 只允许白名单命令，防止 AI 执行危险操作
 */
@Component
public class ShellTool {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"))
            .toAbsolutePath().normalize();

    // 只读白名单命令（Windows PowerShell 版）
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "dir", "tree", "type", "more",
            "Get-ChildItem", "Get-Content", "Select-String",
            "git",                          // git log, git status, git diff 等只读操作
            "where", "findstr",
            "echo", "cat"                  // PowerShell 中 cat 是 Get-Content 别名
    );

    // 危险模式黑名单（即使在白名单命令中也禁止）
    private static final List<String> DANGEROUS_PATTERNS = List.of(
            "|", ">", ">>", "<", "&&", "||", ";", "`", "$(", "${",
            "rm ", "del ", "rmdir", "rd ",
            "git push", "git commit", "git merge", "git rebase",
            "curl ", "wget ", "Invoke-WebRequest", "Invoke-RestMethod",
            "Start-Process", "Stop-Process",
            "Format-", "Remove-", "Set-", "New-", "Write-",
            "> /dev", "> nul", "/dev/null"
    );

    @Tool(description = "在项目目录中执行只读的 PowerShell 命令。" +
            "可用命令: dir (列出目录), tree (目录树), type (查看文件), " +
            "git log/status/diff (Git只读操作), " +
            "Get-ChildItem -Recurse (递归列出), " +
            "Select-String (搜索文件内容/grep)。" +
            "示例: 'dir src/main/java', 'tree src', 'type pom.xml', 'git status', " +
            "'Select-String -Path src\\main\\java\\*.java -Pattern \"@Tool\"'")
    public String executeCommand(
            @ToolParam(description = "要执行的 PowerShell 命令，必须是只读操作") String command) {

        // 安全检查
        String sanitized = command.trim();
        String lowerCmd = sanitized.toLowerCase();

        // 检查是否包含危险模式
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lowerCmd.contains(pattern.toLowerCase())) {
                return "安全拦截：命令包含禁止的模式 '" + pattern + "'。" +
                        "仅允许只读操作。";
            }
        }

        // 检查命令是否在白名单中
        String baseCommand = sanitized.split("\\s+")[0].toLowerCase();
        // PowerShell 中可能有 .exe 后缀
        if (baseCommand.endsWith(".exe")) {
            baseCommand = baseCommand.substring(0, baseCommand.length() - 4);
        }

        boolean allowed = false;
        for (String allowedCmd : ALLOWED_COMMANDS) {
            // 不区分大小写匹配
            if (baseCommand.equalsIgnoreCase(allowedCmd)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            return "安全拦截：命令 '" + baseCommand + "' 不在白名单中。" +
                    "允许的命令: " + ALLOWED_COMMANDS;
        }

        // 执行命令
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile",
                    "-Command", sanitized);
            pb.directory(PROJECT_ROOT.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 超时控制：最多 15 秒
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "错误：命令执行超时（15秒限制）";
            }

            // 读取输出
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String output = reader.lines().collect(Collectors.joining("\n"));

                // 截断过长输出
                if (output.length() > 20_000) {
                    output = output.substring(0, 20_000)
                            + "\n\n... [输出过长，已截断]";
                }

                if (output.isBlank()) {
                    return "命令执行成功，但无输出。退出码: " + process.exitValue();
                }

                return "工作目录: " + PROJECT_ROOT + "\n" +
                        "退出码: " + process.exitValue() + "\n" +
                        "----------------------------------------\n" +
                        output;
            }

        } catch (Exception e) {
            return "命令执行失败: " + e.getMessage();
        }
    }
}
