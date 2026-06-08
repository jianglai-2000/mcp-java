package io.mcp.server.demo;

import java.nio.file.Path;

/**
 * Sandbox for file system operations.
 * <p>
 * When a root directory is configured, all file operations are restricted
 * to paths inside that root. Path traversal attacks (e.g., {@code ../../etc/passwd})
 * are blocked.
 * <p>
 * Configure via environment variable {@code MCP_FILE_ROOT} or
 * system property {@code mcp.file.root}.
 */
public class FileSandbox {

    private static final Path ROOT = resolveRoot();

    private static Path resolveRoot() {
        String rootStr = System.getProperty("mcp.file.root");
        if (rootStr == null || rootStr.isBlank()) {
            rootStr = System.getenv("MCP_FILE_ROOT");
        }
        if (rootStr == null || rootStr.isBlank()) {
            return null; // no sandbox
        }
        Path root = Path.of(rootStr).normalize().toAbsolutePath();
        if (!root.toFile().exists()) {
            System.err.println("WARNING: File sandbox root does not exist: " + root);
        }
        return root;
    }

    /**
     * Resolve and validate a user-supplied path against the sandbox root.
     *
     * @param userPath the path provided by the user/tool caller
     * @return the resolved absolute path if valid
     * @throws SecurityException if the path is outside the sandbox
     */
    public static Path resolve(String userPath) {
        Path resolved = Path.of(userPath).normalize().toAbsolutePath();

        if (ROOT != null) {
            if (!resolved.startsWith(ROOT)) {
                throw new SecurityException("Access denied: path '" + userPath
                        + "' is outside the allowed directory '" + ROOT + "'");
            }
        }

        return resolved;
    }

    /**
     * Check if a sandbox is active.
     */
    public static boolean isEnabled() {
        return ROOT != null;
    }

    /**
     * Get the sandbox root path, or null if not configured.
     */
    public static Path getRoot() {
        return ROOT;
    }
}
