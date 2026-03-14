package com.poss.file.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
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

        Map<String, Object> properties = new HashMap<>();

        // 1. 先加载默认配置（优先 yml，然后 properties），优先级低
        loadConfigFile(properties, jarPath + "/config/application.yml", false);
        loadConfigFile(properties, jarPath + "/config/application.properties", false);

        // 2. 再加载环境特定配置，覆盖默认配置
        if (envFile != null && !envFile.isEmpty()) {
            loadConfigFile(properties, jarPath + "/config/application-" + envFile + ".yml", true);
            loadConfigFile(properties, jarPath + "/config/application-" + envFile + ".properties", true);
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("externalProperties", properties));
        }
    }

    private void loadConfigFile(Map<String, Object> result, String filePath, boolean isEnvConfig) {
        File configFile = new File(filePath);
        if (!configFile.exists()) {
            return;
        }

        String fileName = configFile.getName().toLowerCase();
        try {
            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                loadYamlFile(result, configFile);
                System.out.println("[ExternalConfig] Loaded " + (isEnvConfig ? "env" : "default") + " config from: " + filePath);
            } else if (fileName.endsWith(".properties")) {
                loadPropertiesFile(result, configFile);
                System.out.println("[ExternalConfig] Loaded " + (isEnvConfig ? "env" : "default") + " config from: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("[ExternalConfig] Failed to load config from " + filePath + ": " + e.getMessage());
        }
    }

    private void loadYamlFile(Map<String, Object> result, File file) throws IOException {
        Yaml yaml = new Yaml();
        try (InputStream is = new FileInputStream(file)) {
            Map<String, Object> yamlMap = yaml.load(is);
            if (yamlMap != null) {
                flattenMap("", yamlMap, result);
            }
        }
    }

    private void loadPropertiesFile(Map<String, Object> result, File file) throws IOException {
        Properties props = new Properties();
        try (FileReader reader = new FileReader(file)) {
            props.load(reader);
            for (String key : props.stringPropertyNames()) {
                result.put(key, props.getProperty(key));
            }
        }
    }

    private void flattenMap(String prefix, Map<String, Object> source, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                flattenMap(key, nestedMap, result);
            } else if (value != null) {
                result.put(key, value.toString());
            }
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
