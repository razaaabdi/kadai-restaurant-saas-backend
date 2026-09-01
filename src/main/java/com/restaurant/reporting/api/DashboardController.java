package com.restaurant.reporting.api;

import com.restaurant.reporting.application.DashboardService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/outlets/{outletId}/dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    public DashboardController(DashboardService dashboard){this.dashboard=dashboard;}
    @GetMapping("/summary") public Map<String,Object> summary(@PathVariable UUID outletId,@RequestParam LocalDate from,@RequestParam LocalDate to){return dashboard.summary(outletId,from,to);}
    @GetMapping("/overview") public Map<String,Object> overview(@PathVariable UUID outletId,@RequestParam LocalDate from,@RequestParam LocalDate to){return dashboard.overview(outletId,from,to);}
    @GetMapping("/order-status") public Map<String,Object> status(@PathVariable UUID outletId,@RequestParam LocalDate from,@RequestParam LocalDate to){return dashboard.orderStatus(outletId,from,to);}
    @GetMapping("/recent-orders") public List<Map<String,Object>> recent(@PathVariable UUID outletId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="10") int limit){return dashboard.recentOrders(outletId,from,to,limit);}
    @GetMapping("/search") public List<Map<String,Object>> search(@PathVariable UUID outletId,@RequestParam String q){return dashboard.search(outletId,q);}
}
