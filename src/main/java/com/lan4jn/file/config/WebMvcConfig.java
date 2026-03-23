package com.lan4jn.file.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${minio.public-url}")
    private String publicUrl;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 鏍硅矾寰勬牴鎹畊i.mode閰嶇疆鍐冲畾璺宠浆
        registry.addViewController("/").setViewName("forward:/index.html");
        // lite鐣岄潰鍏ュ彛
        registry.addViewController("/lite").setViewName("forward:/lite.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 闈欐€佽祫婧?- 浣跨敤鏍囧噯 Spring Boot 璧勬簮浣嶇疆
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/img/**")
                .addResourceLocations("classpath:/static/img/");
        registry.addResourceHandler("/index.html")
                .addResourceLocations("classpath:/static/index.html");
        // 鏂囦欢棰勮璺緞鏄犲皠
        registry.addResourceHandler("/preview/**")
                .addResourceLocations(publicUrl + "/");
    }
}

