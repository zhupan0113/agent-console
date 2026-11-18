package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("model_usage")
public class ModelUsage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String externalId;
    private String threadId;
    private String turnId;
    private String model;
    private String provider;
    private String project;
    private Long inputTokens;
    private Long cachedTokens;
    private Long cacheWriteTokens;
    private Long outputTokens;
    private Long reasoningTokens;
    private Long totalTokens;
    private BigDecimal credits;
    private BigDecimal costAmount;
    private String currency;
    private String source;
    private String confidence;
    private Boolean priceMissing;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
