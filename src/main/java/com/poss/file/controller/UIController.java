package com.poss.file.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UIController {

    @Value("${ui.mode:full}")
    private String uiMode;

    /**
     * 根据配置重定向到对应界面
     * - full: 完整功能界面 (index.html)
     * - lite: 低GPU轻量界面 (lite.html)
     */
    @GetMapping("/")
    public String index() {
        if ("lite".equalsIgnoreCase(uiMode)) {
            return "forward:/lite.html";
        }
        return "forward:/index.html";
    }

    /**
     * 完整功能界面入口
     */
    @GetMapping("/full")
    public String fullUI() {
        return "forward:/index.html";
    }

    /**
     * 轻量界面入口
     */
    @GetMapping("/lite")
    public String liteUI() {
        return "forward:/lite.html";
    }
}
