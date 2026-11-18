package com.lenovo.agentconsole;

import com.lenovo.agentconsole.config.AppProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.lenovo.agentconsole.mapper")
@EnableConfigurationProperties(AppProperties.class)
@SpringBootApplication
public class AgentConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentConsoleApplication.class, args);
    }
}
