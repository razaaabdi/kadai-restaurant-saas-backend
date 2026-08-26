package com.restaurant.platform.api;
import com.restaurant.platform.application.PlatformReadService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/platform") public class PlatformReadController{
 private final PlatformReadService service;public PlatformReadController(PlatformReadService service){this.service=service;}
 @GetMapping("/dashboard")public Map<String,Object> dashboard(){return service.dashboard();}
 @GetMapping("/plans")public List<Map<String,Object>> plans(){return service.plans();}
 @GetMapping("/restaurants")public List<Map<String,Object>> restaurants(@RequestParam(required=false)String search,@RequestParam(required=false)String status){return service.restaurants(search,status);}
 @GetMapping("/restaurants/{id}")public Map<String,Object> restaurant(@PathVariable UUID id){return service.restaurant(id);}
 @GetMapping("/audit-logs")public List<Map<String,Object>> audits(@RequestParam(required=false)String tenantId,@RequestParam(required=false)String search){return service.audits(tenantId,search);}
}
