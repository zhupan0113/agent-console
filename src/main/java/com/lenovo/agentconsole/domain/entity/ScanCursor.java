package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scan_cursor")
public class ScanCursor {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceKey;
    private String filePath;
    private Long fileSize;
    private Long byteOffset;
    private LocalDateTime updatedAt;
}
