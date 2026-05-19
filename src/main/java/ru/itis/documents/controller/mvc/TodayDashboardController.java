package ru.itis.documents.controller.mvc;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.TodayDashboardService;

@Controller
@RequiredArgsConstructor
public class TodayDashboardController {

    private final TodayDashboardService todayDashboardService;

    @GetMapping("/app/today")
    public String today(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(name = "noWaterDays", defaultValue = "7") int noWaterDays,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        var items = todayDashboardService.getTodayDue(userId);
        long plantsCount = items.stream().map(i -> i.plantId()).filter(id -> id != null).distinct().count();

        model.addAttribute("items", items);
        model.addAttribute("plantsCount", plantsCount);

        var noWaterPlants = todayDashboardService.getPlantsWithoutWateringDays(userId, noWaterDays);
        model.addAttribute("noWaterDays", noWaterDays);
        model.addAttribute("noWaterPlants", noWaterPlants);

        return "app/today";
    }
}