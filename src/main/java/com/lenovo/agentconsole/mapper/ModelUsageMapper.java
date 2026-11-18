package com.lenovo.agentconsole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lenovo.agentconsole.domain.entity.ModelUsage;
import org.apache.ibatis.annotations.Update;

public interface ModelUsageMapper extends BaseMapper<ModelUsage> {

    @Update("UPDATE model_usage u JOIN agent_instance a ON u.thread_id = a.external_id AND a.platform = 'codex' "
            + "SET u.model = a.model WHERE u.platform = 'codex' AND (u.model IS NULL OR u.model = '') AND a.model IS NOT NULL")
    int backfillCodexModel();
}
