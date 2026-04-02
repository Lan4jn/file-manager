package com.lan4jn.file.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class ShareViewController {

    private static final Logger log = LoggerFactory.getLogger(ShareViewController.class);

    @GetMapping("/share/{shareCode}")
    public RedirectView viewShare(@PathVariable String shareCode,
                                  @RequestParam(value = "password", required = false) String password) {
        log.info("Redirecting share {} to share page", shareCode);
        StringBuilder target = new StringBuilder("/share.html?shareCode=").append(shareCode);
        if (password != null && !password.isBlank()) {
            target.append("&password=").append(password);
        }
        return new RedirectView(target.toString());
    }
}
