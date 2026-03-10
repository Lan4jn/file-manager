package com.poss.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FileManagerApplication {

    public static void main(String[] args) {
        // 从环境变量读取 ENV_FILE 设置 active profile
        String envFile = System.getenv("ENV_FILE");
        SpringApplication app = new SpringApplication(FileManagerApplication.class);
        if (envFile != null && !envFile.isEmpty()) {
            app.setAdditionalProfiles(envFile);
            System.out.println("[FileManager] Active profile set to: " + envFile);
        }
        app.run(args);
    }
}
