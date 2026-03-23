package com.lan4jn.file.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UIController {

    @Value("${ui.mode:full}")
    private String uiMode;

    /**
     * 鏍规嵁閰嶇疆閲嶅畾鍚戝埌瀵瑰簲鐣岄潰
     * - full: 瀹屾暣鍔熻兘鐣岄潰 (index.html)
     * - lite: 浣嶨PU杞婚噺鐣岄潰 (lite.html)
     */
    @GetMapping("/")
    public String index() {
        if ("lite".equalsIgnoreCase(uiMode)) {
            return "forward:/lite.html";
        }
        return "forward:/index.html";
    }

    /**
     * 瀹屾暣鍔熻兘鐣岄潰鍏ュ彛
     */
    @GetMapping("/full")
    public String fullUI() {
        return "forward:/index.html";
    }

    /**
     * 杞婚噺鐣岄潰鍏ュ彛
     */
    @GetMapping("/lite")
    public String liteUI() {
        return "forward:/lite.html";
    }
}

