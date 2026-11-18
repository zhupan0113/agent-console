package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_server")
public class McpServer {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String scope;
    private String name;
    private String transport;
    private String command;
    private String argsJson;
    private String envJson;
    private String url;
    private Boolean enabled;
    private String healthStatus;
    private String configPath;
    private String pathHash;
    private LocalDateTime updatedAt;
}
