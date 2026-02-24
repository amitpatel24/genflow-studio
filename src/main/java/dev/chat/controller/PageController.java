package dev.chat.controller;

import dev.chat.service.GroqService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final GroqService groqService;

    public PageController(GroqService groqService) {
        this.groqService = groqService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("models", groqService.getAvailableModels());
        return "index";
    }
}
