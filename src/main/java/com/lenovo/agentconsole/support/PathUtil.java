package com.lenovo.agentconsole.support;

import java.nio.file.Path;

public final class PathUtil {

    private PathUtil() {
    }

    public static String projectName(String cwd) {
        if (cwd == null || cwd.isBlank()) {
            return "unknown";
        }
        try {
            Path p = Path.of(cwd);
            Path name = p.getFileName();
            return name == null ? cwd : name.toString();
        } catch (Exception e) {
            return cwd;
        }
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
