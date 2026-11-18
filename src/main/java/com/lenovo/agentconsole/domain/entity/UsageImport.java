package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("usage_import")
public class UsageImport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String fileName;
    private String fileHash;
    private Integer rowsCount;
    private BigDecimal credits;
    private String status;
    private String message;
    private LocalDateTime importedAt;
}
