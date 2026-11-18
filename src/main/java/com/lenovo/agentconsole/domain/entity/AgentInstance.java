package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_instance")
public class AgentInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String externalId;
    private String name;
    private String kind;
    private String scope;
    private String cwd;
    private String project;
    private String model;
    private String provider;
    private String role;
    private String status;
    private Boolean archived;
    private Long tokensUsed;
    private BigDecimal costAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String rawJson;
}
