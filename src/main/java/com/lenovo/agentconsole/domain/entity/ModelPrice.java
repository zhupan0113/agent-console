package com.lenovo.agentconsole.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("model_price")
public class ModelPrice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String provider;
    private String model;
    private BigDecimal inputPrice;
    private BigDecimal cachedPrice;
    private BigDecimal cacheWritePrice;
    private BigDecimal outputPrice;
    private String currency;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String remark;
}
