package io.mcp.server.demo;

import io.mcp.server.annotation.McpParam;
import io.mcp.server.annotation.McpTool;
import io.mcp.server.annotation.McpToolProvider;

import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Practical demo tools for the MCP Java Server.
 * <p>
 * These tools provide useful capabilities when connected to an AI client:
 * - File operations (read, write, list)
 * - Text processing (count, encode, decode)
 * - System information
 * - Common utilities
 */
@McpToolProvider
public class DemoTools {

    // ========== FILE SYSTEM ==========

    @McpTool(name = "read_file", description = "Read the contents of a file")
    public String readFile(
            @McpParam(value = "path", description = "Absolute or relative path to the file") String path) {
        try {
            var safePath = FileSandbox.resolve(path);
            if (!safePath.toFile().isFile()) {
                return "❌ File not found: " + path;
            }
            return Files.readString(safePath);
        } catch (SecurityException e) {
            return "❌ " + e.getMessage();
        } catch (IOException e) {
            return "❌ Error reading file: " + e.getMessage();
        }
    }

    @McpTool(name = "write_file", description = "Write text content to a file (overwrites if exists)")
    public String writeFile(
            @McpParam(value = "path", description = "Path to the file") String path,
            @McpParam(value = "content", description = "Text content to write") String content) {
        try {
            var safePath = FileSandbox.resolve(path);
            Files.createDirectories(safePath.getParent());
            Files.writeString(safePath, content);
            return "✅ Written " + content.length() + " bytes to " + safePath;
        } catch (SecurityException e) {
            return "❌ " + e.getMessage();
        } catch (IOException e) {
            return "❌ Error writing file: " + e.getMessage();
        }
    }

    @McpTool(name = "list_dir", description = "List files and directories in a folder")
    public String listDir(
            @McpParam(value = "path", description = "Path to the directory") String path) {
        try {
            var safePath = FileSandbox.resolve(path);
            if (!safePath.toFile().isDirectory()) {
                return "❌ Directory not found: " + path;
            }
            var files = Files.list(safePath).sorted().toList();
            if (files.isEmpty()) return "(empty directory)";

            var sb = new StringBuilder();
            for (var f : files) {
                String type = Files.isDirectory(f) ? "📁" : "📄";
                long size = Files.isDirectory(f) ? 0 : Files.size(f);
                String sizeStr = size > 1024 ? (size / 1024) + " KB" : size + " B";
                sb.append(type).append("  ")
                        .append(f.getFileName())
                        .append("  (").append(sizeStr).append(")")
                        .append("\n");
            }
            return sb.toString().stripTrailing();
        } catch (SecurityException e) {
            return "❌ " + e.getMessage();
        } catch (IOException e) {
            return "❌ Error listing directory: " + e.getMessage();
        }
    }

    @McpTool(name = "file_info", description = "Get information about a file or directory")
    public String fileInfo(
            @McpParam(value = "path", description = "Path to the file/directory") String path) {
        try {
            var p = FileSandbox.resolve(path);
            if (!Files.exists(p)) return "❌ File not found: " + path;

            var sb = new StringBuilder();
            sb.append(Files.isDirectory(p) ? "📁 Directory" : "📄 File").append(": ").append(path).append("\n");
            sb.append("Size: ").append(Files.size(p)).append(" bytes\n");
            sb.append("Last modified: ").append(Files.getLastModifiedTime(p)).append("\n");
            if (Files.isReadable(p)) sb.append("Readable: yes\n");
            if (Files.isWritable(p)) sb.append("Writable: yes\n");
            return sb.toString().stripTrailing();
        } catch (IOException e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    // ========== TEXT PROCESSING ==========

    @McpTool(name = "word_count", description = "Count characters, words, and lines in text (max 1MB)")
    public String wordCount(
            @McpParam(value = "text", description = "Text to analyze") String text) {
        if (text == null || text.isBlank()) {
            return "Characters: 0\nWords: 0\nLines: 0";
        }
        if (text.length() > 1_000_000) {
            return "❌ Text too large (max 1MB)";
        }
        int chars = text.length();
        int words = text.trim().split("\\s+").length;
        int lines = text.split("\n", -1).length;
        return """
                Characters: %d
                Words:      %d
                Lines:      %d
                """.formatted(chars, words, lines).stripTrailing();
    }

    @McpTool(name = "base64_encode", description = "Encode text to Base64 (max 10MB)")
    public String base64Encode(
            @McpParam(value = "text", description = "Text to encode") String text) {
        if (text == null || text.isEmpty()) return "(empty)";
        if (text.length() > 10_000_000) return "❌ Text too large (max 10MB)";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @McpTool(name = "base64_decode", description = "Decode Base64 back to text")
    public String base64Decode(
            @McpParam(value = "encoded", description = "Base64-encoded string") String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "❌ Invalid Base64 input: " + e.getMessage();
        }
    }

    @McpTool(name = "url_encode", description = "URL-encode a string (max 1MB)")
    public String urlEncode(
            @McpParam(value = "text", description = "Text to URL-encode") String text) {
        if (text == null) return "";
        if (text.length() > 1_000_000) return "❌ Text too large (max 1MB)";
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    @McpTool(name = "url_decode", description = "URL-decode a string")
    public String urlDecode(
            @McpParam(value = "text", description = "URL-encoded text to decode") String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    // ========== SYSTEM INFO ==========

    @McpTool(name = "system_info", description = "Get OS, Java version, and memory information")
    public String systemInfo() {
        var rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory() / (1024 * 1024);
        long freeMem = rt.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;

        return """
                OS:       %s %s
                Java:     %s (%s)
                CPUs:     %d
                Memory:   %d MB used / %d MB total
                """.formatted(
                        System.getProperty("os.name"),
                        System.getProperty("os.version"),
                        System.getProperty("java.version"),
                        System.getProperty("java.vendor"),
                        rt.availableProcessors(),
                        usedMem, totalMem
                ).stripTrailing();
    }

    @McpTool(name = "env_var", description = "Get the value of an environment variable")
    public String envVar(
            @McpParam(value = "name", description = "Environment variable name") String name) {
        String value = System.getenv(name);
        if (value == null) return "❌ Environment variable not set: " + name;
        return name + " = " + value;
    }

    // ========== UTILITIES ==========

    @McpTool(name = "uuid", description = "Generate a random UUID (v4)")
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }

    @McpTool(name = "list_timezones", description = "List all available timezone IDs (optionally filtered by keyword)")
    public String listTimezones(
            @McpParam(value = "filter", description = "Optional keyword to filter timezones (e.g. 'Asia', 'Europe')") String filter) {
        var ids = java.time.ZoneId.getAvailableZoneIds().stream()
                .filter(id -> filter == null || filter.isBlank() || id.toLowerCase().contains(filter.toLowerCase()))
                .sorted()
                .toList();
        if (ids.isEmpty()) return "No matching timezones for: " + filter;
        return String.join("\n", ids);
    }

    @McpTool(name = "current_time", description = "Get the current time in a specific timezone")
    public String currentTime(
            @McpParam(value = "timezone", description = "Timezone ID like 'Asia/Shanghai' or 'UTC'. Defaults to system timezone if empty.")
                    String timezone) {
        try {
            var tz = (timezone == null || timezone.isBlank())
                    ? java.time.ZoneId.systemDefault()
                    : java.time.ZoneId.of(timezone);
            var now = LocalDateTime.now(tz);
            return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (" + tz + ")";
        } catch (Exception e) {
            return "❌ Invalid timezone: " + timezone;
        }
    }

    @McpTool(name = "calculate", description = "Evaluate a simple arithmetic expression (e.g., '2 + 3 * 4'). Only supports + - * / ( )")
    public String calculate(
            @McpParam(value = "expression", description = "Arithmetic expression. Supports + - * / ( ) and numbers only") String expression) {
        if (expression == null || expression.isBlank()) {
            return "❌ Please provide an expression";
        }
        // Strip whitespace and validate: only digits, +, -, *, /, ., (, ) allowed
        String cleaned = expression.replaceAll("\\s+", "");
        if (!cleaned.matches("[\\d+\\-*/.()]+")) {
            return "❌ Invalid expression: only numbers and + - * / ( ) are allowed";
        }
        try {
            double result = evalSimple(cleaned);
            // Display as integer if whole number
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return expression + " = " + (long) result;
            }
            return expression + " = " + result;
        } catch (Exception e) {
            return "❌ Error evaluating expression: " + e.getMessage();
        }
    }

    /**
     * Simple arithmetic evaluator—no ScriptEngine, no code injection.
     * Only handles + - * / ( ) with double precision. Thread-safe.
     */
    private static double evalSimple(String expr) {
        return addSub(expr, new int[]{0});
    }

    private static double addSub(String expr, int[] pos) {
        double result = mulDiv(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '+') {
                pos[0]++;
                result += mulDiv(expr, pos);
            } else if (op == '-') {
                pos[0]++;
                result -= mulDiv(expr, pos);
            } else {
                break;
            }
        }
        return result;
    }

    private static double mulDiv(String expr, int[] pos) {
        double result = parseNumber(expr, pos);
        while (pos[0] < expr.length()) {
            char op = expr.charAt(pos[0]);
            if (op == '*') {
                pos[0]++;
                result *= parseNumber(expr, pos);
            } else if (op == '/') {
                pos[0]++;
                double divisor = parseNumber(expr, pos);
                if (divisor == 0) throw new ArithmeticException("Division by zero");
                result /= divisor;
            } else {
                break;
            }
        }
        return result;
    }

    private static double parseNumber(String expr, int[] pos) {
        // Handle parentheses
        if (expr.charAt(pos[0]) == '(') {
            pos[0]++; // skip '('
            double result = addSub(expr, pos);
            if (pos[0] >= expr.length() || expr.charAt(pos[0]) != ')') {
                throw new IllegalArgumentException("Mismatched parentheses");
            }
            pos[0]++; // skip ')'
            return result;
        }
        // Handle leading minus sign (negative numbers)
        if (expr.charAt(pos[0]) == '-') {
            pos[0]++;
            return -parseNumber(expr, pos);
        }
        // Parse numeric value
        int start = pos[0];
        boolean hasDecimal = false;
        while (pos[0] < expr.length() && (Character.isDigit(expr.charAt(pos[0]))
                || (expr.charAt(pos[0]) == '.' && !hasDecimal))) {
            if (expr.charAt(pos[0]) == '.') hasDecimal = true;
            pos[0]++;
        }
        if (pos[0] == start) {
            throw new IllegalArgumentException("Expected number at position " + pos[0]);
        }
        return Double.parseDouble(expr.substring(start, pos[0]));
    }
}
