package io.mcp.server.demo;

import java.nio.file.Path;

/**
 * 文件操作沙箱，保护服务器文件安全。
 *
 * 配置了 MCP_FILE_ROOT 后，所有文件操作（读、写、列表、查看信息）
 * 都被限制在该目录内，防止路径穿越攻击（如 ../../etc/passwd）。
 *
 * 配置方式（按优先级）：
 *   1. CLI 参数：--file-root /data
 *   2. 环境变量：set MCP_FILE_ROOT=C:\data
 *   3. Java 属性：-Dmcp.file.root=/data
 *
 * 不配置时，沙箱不启用，文件操作没有路径限制（默认行为）。
 */
public class FileSandbox {

    /** 沙箱根目录，null 表示不启用 */
    private static final Path ROOT = resolveRoot();

    private static Path resolveRoot() {
        // 优先级：系统属性 > 环境变量
        String rootStr = System.getProperty("mcp.file.root");
        if (rootStr == null || rootStr.isBlank()) {
            rootStr = System.getenv("MCP_FILE_ROOT");
        }
        if (rootStr == null || rootStr.isBlank()) {
            return null; // 未配置沙箱
        }
        Path root = Path.of(rootStr).normalize().toAbsolutePath();
        if (!root.toFile().exists()) {
            System.err.println("⚠ WARNING: File sandbox root does not exist: " + root);
            System.err.println("   File operations without restrictions will still work,");
            System.err.println("   but create the directory to enable sandbox protection.");
        }
        return root;
    }

    /**
     * 解析并验证用户传入的路径。
     *
     * @param userPath 用户传入的文件路径
     * @return 规范化后的绝对路径
     * @throws SecurityException 如果路径在沙箱范围外
     */
    public static Path resolve(String userPath) {
        Path resolved = Path.of(userPath).normalize().toAbsolutePath();

        if (ROOT != null) {
            // 检查路径是否在沙箱根目录下，防止路径穿越
            if (!resolved.startsWith(ROOT)) {
                throw new SecurityException("⛔ 访问被拒绝：路径 '" + userPath
                        + "' 不在允许的目录 '" + ROOT + "' 内");
            }
        }

        return resolved;
    }

    /**
     * 沙箱是否已启用。
     */
    public static boolean isEnabled() {
        return ROOT != null;
    }

    /**
     * 获取沙箱根目录路径。
     */
    public static Path getRoot() {
        return ROOT;
    }
}
