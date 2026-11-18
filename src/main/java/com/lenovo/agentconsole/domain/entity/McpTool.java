package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mcp_tool")
public class McpTool {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long serverId;
    private String name;
    private String description;
    private String schemaJson;
    private LocalDateTime discoveredAt;
}
