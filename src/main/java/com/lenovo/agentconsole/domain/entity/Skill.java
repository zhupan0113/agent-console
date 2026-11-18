package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String scope;
    private String name;
    private String description;
    private String path;
    private String pathHash;
    private String checksum;
    private Boolean enabled;
    private String sourceType;
    private LocalDateTime updatedAt;
}
