package com.mychat.tools;

import com.mychat.utils.WorkspaceUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ToolContext;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ShellTool：空命令拒绝，临时目录中的 echo 返回输出和退出码。 */
class ShellToolTest {

    /** 空命令不启动进程。 */
    @Test
    void blankCommandReturnsError() {
        ShellTool tool = new ShellTool(new WorkspaceUtil());

        String out = tool.executeCommand("  ", null);

        assertTrue(out.contains("命令不能为空"));
    }

    /** 在指定工作区执行 echo，输出包含文本和退出码 0。 */
    @Test
    void echoReturnsOutputAndExitCode(@TempDir Path dir) {
        ShellTool tool = new ShellTool(new WorkspaceUtil());
        ToolContext context = new ToolContext(Map.of("workDir", dir.toString()));

        String out = tool.executeCommand("echo hello-shell", context);

        assertTrue(out.contains("hello-shell"), out);
        assertTrue(out.contains("退出码: 0"), out);
        assertFalse(out.contains("执行失败"), out);
    }
}
