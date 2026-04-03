package com.traffic.controller;

import com.traffic.dto.LaneStatusDTO;
import com.traffic.service.DensitySignalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private DensitySignalService densitySignalService;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<LaneStatusDTO> lanes = densitySignalService.getAllLaneStatus();
        model.addAttribute("lanes", lanes);
        model.addAttribute("isProcessing", densitySignalService.isProcessing());
        return "dashboard";
    }
}
