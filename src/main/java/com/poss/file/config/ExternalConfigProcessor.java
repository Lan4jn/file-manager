package com.poss.file.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ExternalConfigProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 获取jar包所在目录
        String jarPath = System.getProperty("user.dir");
        // 读取环境变量 ENV_FILE
        String envFile = System.getenv("ENV_FILE");

        // 设置 active profiles
        if (envFile != null && !envFile.isEmpty()) {
            application.setAdditionalProfiles(envFile);
            System.out.println("[ExternalConfig] Active profile set to: " + envFile);
        }

        Map<String, Object> properties = new HashMap<>();

        // 1. 先加载默认配置（application.yml），优先级低
        String defaultConfigPath = jarPath + "/config/application.yml";
        File defaultConfigFile = new File(defaultConfigPath);
        if (defaultConfigFile.exists()) {
            try {
                Properties props = new Properties();
                props.load(new FileReader(defaultConfigFile));
                for (String key : props.stringPropertyNames()) {
                    properties.put(key, props.getProperty(key));
                }
                System.out.println("[ExternalConfig] Loaded default config from: " + defaultConfigPath);
            } catch (IOException e) {
                System.err.println("[ExternalConfig] Failed to load default config: " + e.getMessage());
            }
        }

        // 2. 再加载环境特定配置（application-{env}.yml），覆盖默认配置
        if (envFile != null && !envFile.isEmpty()) {
            String envConfigPath = jarPath + "/config/application-" + envFile + ".yml";
            File envConfigFile = new File(envConfigPath);
            if (envConfigFile.exists()) {
                try {
                    Properties props = new Properties();
                    props.load(new FileReader(envConfigFile));
                    for (String key : props.stringPropertyNames()) {
                        properties.put(key, props.getProperty(key));
                    }
                    System.out.println("[ExternalConfig] Loaded env config from: " + envConfigPath);
                } catch (IOException e) {
                    System.err.println("[ExternalConfig] Failed to load env config: " + e.getMessage());
                }
            } else {
                System.out.println("[ExternalConfig] Env config file not found: " + envConfigPath + " (using default)");
            }
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("externalProperties", properties));
        }
    }
}
