package com.lenovo.agentconsole.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lenovo.agentconsole.config.AppProperties;
import com.lenovo.agentconsole.domain.entity.AgentInstance;
import com.lenovo.agentconsole.domain.entity.McpServer;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import com.lenovo.agentconsole.domain.entity.ScanCursor;
import com.lenovo.agentconsole.domain.entity.Skill;
import com.lenovo.agentconsole.mapper.AgentInstanceMapper;
import com.lenovo.agentconsole.mapper.ModelUsageMapper;
import com.lenovo.agentconsole.mapper.ScanCursorMapper;
import com.lenovo.agentconsole.service.AssetStore;
import com.lenovo.agentconsole.service.PriceService;
import com.lenovo.agentconsole.support.HashUtil;
import com.lenovo.agentconsole.support.PathUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodexAdapter {

    private final AppProperties props;
    private final AssetStore assetStore;
    private final PriceService priceService;
    private final AgentInstanceMapper agentMapper;
    private final ModelUsageMapper usageMapper;
    private final ScanCursorMapper cursorMapper;
    private final ObjectMapper om = new ObjectMapper();

    public int scanAssets() {
        int count = 0;
        count += scanSkills(Path.of(props.getPaths().getCodexHome()).resolve("skills"), "global", 4);
        count += scanSkills(Path.of(props.getPaths().getCodexHome()).resolve("plugins/cache"), "plugin", 8);
        count += scanMcp();
        return count;
    }

    public int ingestUsage() {
        Path sessions = Path.of(props.getPaths().getCodexHome()).resolve("sessions");
        if (!Files.isDirectory(sessions)) {
            return 0;
        }
        int inserted = 0;
        try (Stream<Path> stream = Files.walk(sessions, 8)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("rollout-"))
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .toList();
            for (Path file : files) {
                try {
                    inserted += ingestFile(file);
                } catch (Exception e) {
                    log.warn("codex rollout parse failed: {}", file, e);
                }
            }
        } catch (Exception e) {
            log.warn("codex sessions scan failed", e);
        }
        return inserted;
    }

    private int scanSkills(Path root, String scope, int depth) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> stream = Files.walk(root, depth)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .toList();
            for (Path file : files) {
                try {
                    saveSkill(file, scope);
                    count++;
                } catch (Exception e) {
                    log.warn("skill parse failed: {}", file, e);
                }
            }
        } catch (Exception e) {
            log.warn("skill walk failed: {}", root, e);
        }
        return count;
    }

    private void saveSkill(Path file, String scope) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String content = new String(bytes, StandardCharsets.UTF_8);
        String name = frontMatter(content, "name");
        String desc = frontMatter(content, "description");
        if (name == null || name.isBlank()) {
            name = file.getParent().getFileName().toString();
        }
        Skill skill = new Skill();
        skill.setPlatform("codex");
        skill.setScope(scope);
        skill.setName(name);
        skill.setDescription(PathUtil.truncate(desc, 2000));
        skill.setPath(file.toString());
        skill.setPathHash(HashUtil.sha256(file.toString()));
        skill.setChecksum(HashUtil.sha256(bytes));
        skill.setEnabled(Boolean.TRUE);
        skill.setSourceType(file.toString().contains(".system") ? "system" : ("plugin".equals(scope) ? "plugin" : "user"));
        skill.setUpdatedAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault()));
        assetStore.upsertSkill(skill);
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
        Path configPath = Path.of(props.getPaths().getCodexHome()).resolve("config.toml");
        if (!Files.isRegularFile(configPath)) {
            return 0;
        }
        int count = 0;
        try {
            TomlParseResult toml = Toml.parse(configPath);
            if (toml.hasErrors()) {
                log.warn("codex config.toml has errors: {}", toml.errors());
            }
            TomlTable servers = toml.getTable("mcp_servers");
            if (servers == null) {
                return 0;
            }
            for (String name : servers.keySet()) {
                try {
                    TomlTable table = servers.getTable(name);
                    if (table == null) {
                        continue;
                    }
                    McpServer server = new McpServer();
                    server.setPlatform("codex");
                    server.setScope("global");
                    server.setName(name);
                    String command = table.getString("command");
                    String url = table.getString("url");
                    server.setCommand(command);
                    server.setUrl(url);
                    server.setTransport(command != null ? "stdio" : "http");
                    Boolean enabled = table.getBoolean("enabled");
                    server.setEnabled(enabled == null || enabled);
                    server.setArgsJson(argsJson(table.getArray("args")));
                    server.setEnvJson(envJson(table.getTable("env")));
                    server.setHealthStatus("unknown");
                    server.setConfigPath(configPath.toString());
                    server.setPathHash(HashUtil.sha256(configPath + "#" + name));
                    server.setUpdatedAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(configPath).toInstant(), ZoneId.systemDefault()));
                    assetStore.upsertMcp(server);
                    count++;
                } catch (Exception e) {
                    log.warn("codex mcp parse failed: {}", name, e);
                }
            }
        } catch (Exception e) {
            log.warn("codex config.toml parse failed", e);
        }
        return count;
    }

    private String argsJson(TomlArray array) {
        if (array == null) {
            return null;
        }
        List<String> args = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            args.add(String.valueOf(array.get(i)));
        }
        try {
            return om.writeValueAsString(args);
        } catch (Exception e) {
            return null;
        }
    }

    private String envJson(TomlTable env) {
        if (env == null) {
            return null;
        }
        Map<String, String> masked = new LinkedHashMap<>();
        for (String key : env.keySet()) {
            Object value = env.get(key);
            masked.put(key, HashUtil.mask(value == null ? "" : String.valueOf(value)));
        }
        try {
            return om.writeValueAsString(masked);
        } catch (Exception e) {
            return null;
        }
    }

    private int ingestFile(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) {
            return 0;
        }
        ScanCursor cursor = cursorMapper.selectOne(new LambdaQueryWrapper<ScanCursor>()
                .eq(ScanCursor::getSourceKey, "codex:" + file));
        int offset = cursor == null ? 0 : (int) Math.min(cursor.getByteOffset(), bytes.length);
        SessionMeta meta = readMeta(file);
        String sessionId = meta.sessionId != null ? meta.sessionId : file.getFileName().toString();

        String chunk = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
        int lastNewline = chunk.lastIndexOf('\n');
        if (lastNewline < 0) {
            repairAgentName(file, sessionId);
            return 0;
        }
        String complete = chunk.substring(0, lastNewline + 1);
        int consumed = complete.getBytes(StandardCharsets.UTF_8).length;
        String firstUserMessage = null;
        LocalDateTime lastTs = meta.createdAt;
        int inserted = 0;

        for (String line : complete.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode node;
            try {
                node = om.readTree(line);
            } catch (Exception e) {
                continue;
            }
            String type = node.path("type").asText("");
            JsonNode payload = node.path("payload");
            LocalDateTime ts = parseTs(node.path("timestamp").asText(null));
            if (ts != null) {
                lastTs = ts;
            }
            if ("response_item".equals(type)) {
                if (firstUserMessage == null) {
                    firstUserMessage = userMessage(payload);
                }
            } else if ("token_usage_record".equals(type)) {
                if (saveUsage(payload, meta, sessionId, ts)) {
                    inserted++;
                }
            } else if ("turn_context".equals(type)) {
                String model = payload.path("model").asText(null);
                if (model != null && meta.model == null) {
                    meta.model = model;
                }
            }
        }

        if (firstUserMessage == null) {
            AgentInstance existing = agentMapper.selectOne(new LambdaQueryWrapper<AgentInstance>()
                    .eq(AgentInstance::getPlatform, "codex")
                    .eq(AgentInstance::getExternalId, sessionId));
            if (existing != null && dirtyName(existing.getName())) {
                firstUserMessage = scanFirstUserMessage(file);
            }
        }

        upsertAgent(meta, sessionId, firstUserMessage, lastTs);

        ScanCursor updated = cursor == null ? new ScanCursor() : cursor;
        updated.setSourceKey("codex:" + file);
        updated.setFilePath(file.toString());
        updated.setFileSize((long) bytes.length);
        updated.setByteOffset((long) offset + consumed);
        updated.setUpdatedAt(LocalDateTime.now());
        if (cursor == null) {
            cursorMapper.insert(updated);
        } else {
            cursorMapper.updateById(updated);
        }
        return inserted;
    }

    private boolean saveUsage(JsonNode payload, SessionMeta meta, String sessionId, LocalDateTime ts) {
        JsonNode usage = payload.path("usage");
        if (usage.isMissingNode()) {
            return false;
        }
        String responseId = payload.path("response_id").asText(null);
        LocalDateTime occurredAt = ts == null ? LocalDateTime.now() : ts;
        long input = usage.path("input_tokens").asLong(0);
        long cached = usage.path("cached_input_tokens").asLong(0);
        long cacheWrite = usage.path("cache_write_input_tokens").asLong(0);
        long output = usage.path("output_tokens").asLong(0);
        long reasoning = usage.path("reasoning_output_tokens").asLong(0);
        long total = usage.path("total_tokens").asLong(input + cached + output);
        String model = payload.path("model").asText(meta.model);
        String provider = payload.path("provider").asText(meta.provider);

        ModelUsage row = new ModelUsage();
        row.setPlatform("codex");
        row.setExternalId(responseId != null && !responseId.isBlank()
                ? responseId
                : HashUtil.sha256(sessionId + "|" + occurredAt + "|" + total));
        row.setThreadId(sessionId);
        row.setTurnId(payload.path("turn_id").asText(null));
        row.setModel(model);
        row.setProvider(provider);
        row.setProject(PathUtil.projectName(meta.cwd));
        row.setInputTokens(input);
        row.setCachedTokens(cached);
        row.setCacheWriteTokens(cacheWrite);
        row.setOutputTokens(output);
        row.setReasoningTokens(reasoning);
        row.setTotalTokens(total);
        row.setCurrency("CNY");
        row.setSource("client_log");
        row.setConfidence("exact");
        row.setOccurredAt(occurredAt);
        row.setCreatedAt(LocalDateTime.now());

        Optional<BigDecimal> cost = priceService.cost(provider, model, occurredAt.toLocalDate(),
                input, cached, cacheWrite, output);
        row.setPriceMissing(cost.isEmpty());
        row.setCostAmount(cost.orElse(BigDecimal.ZERO));
        try {
            usageMapper.insert(row);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private String userMessage(JsonNode payload) {
        if (!"message".equals(payload.path("type").asText()) || !"user".equals(payload.path("role").asText())) {
            return null;
        }
        JsonNode content = payload.path("content");
        if (!content.isArray() || content.isEmpty()) {
            return null;
        }
        for (JsonNode item : content) {
            String text = item.path("text").asText(null);
            String cleaned = cleanUserText(text);
            if (cleaned != null) {
                return PathUtil.truncate(cleaned, 80);
            }
        }
        return null;
    }

    private String cleanUserText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text
                .replaceAll("(?s)<environment_context>.*?</environment_context>", " ")
                .replaceAll("(?s)<user_instructions>.*?</user_instructions>", " ");
        for (String line : cleaned.split("\\R")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private void repairAgentName(Path file, String sessionId) {
        AgentInstance existing = agentMapper.selectOne(new LambdaQueryWrapper<AgentInstance>()
                .eq(AgentInstance::getPlatform, "codex")
                .eq(AgentInstance::getExternalId, sessionId));
        if (existing == null || !dirtyName(existing.getName())) {
            return;
        }
        String message = scanFirstUserMessage(file);
        if (message != null) {
            existing.setName(message);
            agentMapper.updateById(existing);
        }
    }

    private boolean dirtyName(String name) {
        return name == null || name.startsWith("Codex ") || name.startsWith("<") || name.contains("environment_context");
    }

    private String scanFirstUserMessage(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count++ < 3000) {
                if (!line.contains("\"response_item\"")) {
                    continue;
                }
                JsonNode node;
                try {
                    node = om.readTree(line);
                } catch (Exception e) {
                    continue;
                }
                if (!"response_item".equals(node.path("type").asText())) {
                    continue;
                }
                String message = userMessage(node.path("payload"));
                if (message != null) {
                    return message;
                }
            }
        } catch (Exception e) {
            log.warn("scan first user message failed: {}", file, e);
        }
        return null;
    }

    private void upsertAgent(SessionMeta meta, String sessionId, String firstUserMessage, LocalDateTime lastTs) {
        AgentInstance agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentInstance>()
                .eq(AgentInstance::getPlatform, "codex")
                .eq(AgentInstance::getExternalId, sessionId));
        boolean create = agent == null;
        if (create) {
            agent = new AgentInstance();
            agent.setPlatform("codex");
            agent.setExternalId(sessionId);
            agent.setKind("thread");
            agent.setScope("global");
            agent.setStatus("active");
            agent.setArchived(false);
            agent.setTokensUsed(0L);
            agent.setCostAmount(BigDecimal.ZERO);
            agent.setName("Codex " + sessionId.substring(0, Math.min(8, sessionId.length())));
        }
        if (firstUserMessage != null && (create || dirtyName(agent.getName()))) {
            agent.setName(firstUserMessage);
        }
        if (meta.cwd != null) {
            agent.setCwd(meta.cwd);
            agent.setProject(PathUtil.projectName(meta.cwd));
        }
        if (meta.model != null) {
            agent.setModel(meta.model);
        }
        if (meta.provider != null) {
            agent.setProvider(meta.provider);
        }
        if (meta.createdAt != null) {
            agent.setCreatedAt(meta.createdAt);
        }
        agent.setUpdatedAt(lastTs == null ? LocalDateTime.now() : lastTs);
        ObjectNode raw = om.createObjectNode();
        raw.put("cwd", meta.cwd);
        raw.put("cliVersion", meta.cliVersion);
        raw.put("provider", meta.provider);
        raw.put("model", meta.model);
        agent.setRawJson(raw.toString());
        if (create) {
            agentMapper.insert(agent);
        } else {
            agentMapper.updateById(agent);
        }
    }

    private SessionMeta readMeta(Path file) {
        SessionMeta meta = new SessionMeta();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String first = reader.readLine();
            if (first == null || first.isBlank()) {
                return meta;
            }
            JsonNode node = om.readTree(first);
            if (!"session_meta".equals(node.path("type").asText())) {
                return meta;
            }
            JsonNode payload = node.path("payload");
            meta.sessionId = payload.path("id").asText(payload.path("session_id").asText(null));
            meta.cwd = payload.path("cwd").asText(null);
            meta.provider = payload.path("model_provider").asText(null);
            meta.cliVersion = payload.path("cli_version").asText(null);
            meta.model = payload.path("model").asText(null);
            if (meta.model == null) {
                meta.model = payload.path("collaboration_mode").path("model").asText(null);
            }
            meta.createdAt = parseTs(node.path("timestamp").asText(null));
        } catch (Exception e) {
            log.warn("codex meta parse failed: {}", file, e);
        }
        return meta;
    }

    private LocalDateTime parseTs(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (Exception e) {
                return LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
            }
        }
    }

    private static class SessionMeta {
        private String sessionId;
        private String cwd;
        private String provider;
        private String model;
        private String cliVersion;
        private LocalDateTime createdAt;
    }
}
