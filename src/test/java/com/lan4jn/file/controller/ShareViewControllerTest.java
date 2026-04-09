package com.lan4jn.file.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.view.RedirectView;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShareViewControllerTest {

    @Test
    void shouldPreservePasswordInRedirectUrl() {
        ShareViewController controller = new ShareViewController();

        RedirectView view = controller.viewShare("ABC12345", "2468");

        assertEquals("/share.html?shareCode=ABC12345&password=2468", view.getUrl());
    }

    @Test
    void shouldRedirectWithoutPasswordWhenPasswordMissing() {
        ShareViewController controller = new ShareViewController();

        RedirectView view = controller.viewShare("ABC12345", null);

        assertEquals("/share.html?shareCode=ABC12345", view.getUrl());
    }

    @Test
    void shouldEncodePasswordInRedirectUrl() {
        ShareViewController controller = new ShareViewController();

        RedirectView view = controller.viewShare("ABC12345", "12 3#");

        assertEquals("/share.html?shareCode=ABC12345&password=12%203%23", view.getUrl());
    }
}
