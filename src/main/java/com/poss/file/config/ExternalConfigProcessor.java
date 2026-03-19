package com.poss.file.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
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

        Map<String, Object> properties = new LinkedHashMap<>();

        // 1. 先加载默认配置（YAML）
        loadPreferredConfig(properties, jarPath, "application", false);

        // 2. 再加载环境特定配置，覆盖默认配置
        if (envFile != null && !envFile.isEmpty()) {
            loadPreferredConfig(properties, jarPath, "application-" + envFile, true);
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("externalProperties", properties));
        }
    }

    private void loadPreferredConfig(Map<String, Object> result, String jarPath, String baseName, boolean isEnvConfig) {
        File configDir = new File(jarPath, "config");
        File yamlFile = new File(configDir, baseName + ".yml");
        File yamlAltFile = new File(configDir, baseName + ".yaml");
        if (yamlFile.exists()) {
            loadConfigFile(result, yamlFile, isEnvConfig);
            return;
        }

        if (yamlAltFile.exists()) {
            loadConfigFile(result, yamlAltFile, isEnvConfig);
        }
    }

    private void loadConfigFile(Map<String, Object> result, File configFile, boolean isEnvConfig) {
        String fileName = configFile.getName().toLowerCase();
        try {
            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                loadYamlFile(result, configFile);
                System.out.println("[ExternalConfig] Loaded " + (isEnvConfig ? "env" : "default") + " YAML config from: " + configFile.getAbsolutePath());
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[ExternalConfig] Failed to load config from " + configFile.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private void loadYamlFile(Map<String, Object> result, File file) throws IOException {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new FileSystemResource(file));
        Properties yamlProperties = yamlFactory.getObject();
        if (yamlProperties == null) {
            return;
        }
        for (String key : yamlProperties.stringPropertyNames()) {
            result.put(key, yamlProperties.getProperty(key));
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
