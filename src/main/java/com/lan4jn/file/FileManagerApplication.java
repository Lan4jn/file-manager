package com.lan4jn.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FileManagerApplication {

    public static void main(String[] args) {
        // 浠庣幆澧冨彉閲忚鍙?ENV_FILE 璁剧疆 active profile
        String envFile = System.getenv("ENV_FILE");
        SpringApplication app = new SpringApplication(FileManagerApplication.class);
        if (envFile != null && !envFile.isEmpty()) {
            app.setAdditionalProfiles(envFile);
            System.out.println("[FileManager] Active profile set to: " + envFile);
        }
        app.run(args);
    }
}

