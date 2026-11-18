package com.lenovo.agentconsole.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lenovo.agentconsole.config.AppProperties;
import com.lenovo.agentconsole.domain.entity.McpServer;
import com.lenovo.agentconsole.domain.entity.Skill;
import com.lenovo.agentconsole.service.AssetStore;
import com.lenovo.agentconsole.support.HashUtil;
import com.lenovo.agentconsole.support.PathUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class TraeAdapter {

    private final AppProperties props;
    private final AssetStore assetStore;
    private final ObjectMapper om = new ObjectMapper();

    public int scanAssets() {
        return scanMcp() + scanSkills();
    }

    private int scanSkills() {
        String home = props.getPaths().getTraeCnHome();
        if (home == null || home.isBlank()) {
            return 0;
        }
        Path root = Path.of(home).resolve("skills");
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> stream = Files.walk(root, 4)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .toList();
            for (Path file : files) {
                try {
                    byte[] bytes = Files.readAllBytes(file);
                    String content = new String(bytes, StandardCharsets.UTF_8);
                    String name = frontMatter(content, "name");
                    if (name == null || name.isBlank()) {
                        name = file.getParent().getFileName().toString();
                    }
                    Skill skill = new Skill();
                    skill.setPlatform("trae");
                    skill.setScope("global");
                    skill.setName(name);
                    skill.setDescription(PathUtil.truncate(frontMatter(content, "description"), 2000));
                    skill.setPath(file.toString());
                    skill.setPathHash(HashUtil.sha256(file.toString()));
                    skill.setChecksum(HashUtil.sha256(bytes));
                    skill.setEnabled(true);
                    skill.setSourceType(isPromptTemplate(name, file) ? "prompt" : "user");
                    skill.setUpdatedAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault()));
                    assetStore.upsertSkill(skill);
                    count++;
                } catch (Exception e) {
                    log.warn("trae skill parse failed: {}", file, e);
                }
            }
        } catch (Exception e) {
            log.warn("trae skills walk failed", e);
        }
        return count;
    }

    private boolean isPromptTemplate(String name, Path file) {
        String folder = file.getParent() == null ? "" : file.getParent().getFileName().toString();
        String text = (name + " " + folder).toLowerCase();
        return text.contains("prompt") || text.contains("提示词");
    }

    private String frontMatter(String content, String key) {
        if (content == null || !content.startsWith("---")) {
            return null;
        }
        int end = content.indexOf("\n---", 3);
        if (end < 0) {
            return null;
        }
        for (String line : content.substring(3, end).split("\n")) {
            String t = line.trim();
            if (t.startsWith(key + ":")) {
                String value = t.substring(key.length() + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private int scanMcp() {
        Path mcpFile = Path.of(props.getPaths().getTraeHome()).resolve("User").resolve("mcp.json");
        if (!Files.isRegularFile(mcpFile)) {
            return 0;
        }
        int count = 0;
        try {
            JsonNode root = om.readTree(mcpFile.toFile());
            JsonNode servers = root.path("mcpServers");
            Iterator<Map.Entry<String, JsonNode>> it = servers.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                JsonNode node = entry.getValue();
                McpServer server = new McpServer();
                server.setPlatform("trae");
                server.setScope("global");
                server.setName(entry.getKey());
                String command = node.path("command").asText(null);
                String url = node.path("url").asText(null);
                server.setCommand(command);
                server.setUrl(url);
                server.setTransport(command != null ? "stdio" : "http");
                server.setEnabled(true);
                server.setHealthStatus("unknown");
                server.setArgsJson(node.has("args") ? node.get("args").toString() : null);
                server.setEnvJson(maskEnv(node.path("env")));
                server.setConfigPath(mcpFile.toString());
                server.setPathHash(HashUtil.sha256(mcpFile + "#" + entry.getKey()));
                server.setUpdatedAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(mcpFile).toInstant(), ZoneId.systemDefault()));
                assetStore.upsertMcp(server);
                count++;
            }
        } catch (Exception e) {
            log.warn("trae mcp.json parse failed", e);
        }
        return count;
    }

    private String maskEnv(JsonNode env) {
        if (env == null || !env.isObject()) {
            return null;
        }
        Map<String, String> masked = new LinkedHashMap<>();
        env.fields().forEachRemaining(e -> masked.put(e.getKey(), HashUtil.mask(e.getValue().asText(""))));
        try {
            return om.writeValueAsString(masked);
        } catch (Exception e) {
            return null;
        }
    }
}
