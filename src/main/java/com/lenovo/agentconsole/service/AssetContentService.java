package com.lenovo.agentconsole.service;

import com.lenovo.agentconsole.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AssetContentService {

    private static final int MAX_CHARS = 200_000;
    private static final int MAX_FILES = 300;

    private final AppProperties props;

    public Map<String, Object> read(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!allowed(path)) {
            throw new IllegalArgumentException("不允许读取该路径");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }

        StringBuilder content = new StringBuilder();
        boolean truncated = false;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                int remain = MAX_CHARS - content.length();
                if (remain <= 0) {
                    truncated = true;
                    break;
                }
                content.append(buffer, 0, Math.min(read, remain));
                if (read > remain) {
                    truncated = true;
                    break;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取失败：" + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path.toString());
        result.put("name", path.getFileName().toString());
        result.put("content", content.toString());
        result.put("truncated", truncated);
        try {
            result.put("size", Files.size(path));
            result.put("modifiedAt", LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()));
        } catch (Exception ignored) {
            result.put("size", 0);
        }
        return result;
    }

    public List<Map<String, Object>> list(String rawDir) {
        if (rawDir == null || rawDir.isBlank()) {
            throw new IllegalArgumentException("dir 不能为空");
        }
        Path dir = Path.of(rawDir).toAbsolutePath().normalize();
        if (!allowed(dir)) {
            throw new IllegalArgumentException("不允许读取该目录");
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目录不存在：" + dir);
        }
        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir, 4)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                if (files.size() >= MAX_FILES) {
                    break;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", p.getFileName().toString());
                item.put("path", p.toString());
                item.put("relativePath", dir.relativize(p).toString());
                try {
                    item.put("size", Files.size(p));
                } catch (Exception e) {
                    item.put("size", 0);
                }
                files.add(item);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取目录失败：" + e.getMessage());
        }
        files.sort(Comparator.comparing(m -> String.valueOf(m.get("relativePath"))));
        return files;
    }

    private boolean allowed(Path path) {
        List<String> roots = Arrays.asList(
                props.getPaths().getCodexHome(),
                props.getPaths().getTraeHome(),
                props.getPaths().getTraeCnHome(),
                props.getPaths().getClaudeHome());
        for (String root : roots) {
            if (root == null || root.isBlank()) {
                continue;
            }
            try {
                if (path.startsWith(Path.of(root).toAbsolutePath().normalize())) {
                    return true;
                }
            } catch (Exception ignored) {
                // skip invalid root
            }
        }
        return false;
    }
}
