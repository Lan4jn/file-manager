package com.poss.file.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ExternalConfigProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 获取 jar 所在的目录
        String jarPath = getJarDirectory();
        String envFile = System.getenv("ENV_FILE");

        // 设置日志路径到 jar 包同级目录下的 logs 目录
        String logPath = jarPath + "/logs";
        System.setProperty("LOG_PATH", logPath);

        // 创建 logs 目录
        File logDir = new File(logPath);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        System.out.println("[ExternalConfig] Jar directory: " + jarPath);
        System.out.println("[ExternalConfig] Log directory: " + logPath);

        Map<String, Object> properties = new HashMap<>();

        // 1. 先加载默认配置（application.properties），优先级低
        String defaultConfigPath = jarPath + "/config/application.properties";
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

        // 2. 再加载环境特定配置（application-{env}.properties），覆盖默认配置
        if (envFile != null && !envFile.isEmpty()) {
            String envConfigPath = jarPath + "/config/application-" + envFile + ".properties";
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

    /**
     * 获取 jar 文件所在的目录
     */
    private String getJarDirectory() {
        URL url = getClass().getProtectionDomain().getCodeSource().getLocation();
        try {
            File jarFile = new File(url.toURI()).getCanonicalFile();
            if (jarFile.isFile()) {
                return jarFile.getParentFile().getAbsolutePath();
            }
        } catch (Exception e) {
            // 忽略异常
        }
        // 如果获取失败，使用 user.dir
        return System.getProperty("user.dir");
    }
}
