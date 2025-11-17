package com.example.smartspendapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    // keep header preview
    @GetMapping("/header")
    public String headerPage(Model model) {
        model.addAttribute("name", "tamil");
        return "header";
    }

    // only map root to redirect to the actual dashboard handler in ExpenseController
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
}



