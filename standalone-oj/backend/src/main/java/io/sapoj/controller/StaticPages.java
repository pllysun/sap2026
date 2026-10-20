package io.sapoj.controller;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The frontend bundles live inside the JAR; no association routes or reverse proxy is required. */
@Controller
public class StaticPages {
    @GetMapping({"/login", "/register", "/profile", "/oj", "/oj/**"})
    public String user() { return "forward:/index.html"; }
    @GetMapping("/admin") public String adminRedirect() { return "redirect:/admin/"; }
    @GetMapping({"/admin/", "/admin/login", "/admin/oj", "/admin/users", "/admin/audit", "/admin/settings", "/admin/storage"})
    public String admin() { return "forward:/admin/index.html"; }
}
