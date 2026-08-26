package com.restaurant.platform.api;
import com.restaurant.platform.application.PlatformAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/v1/platform/auth") public class PlatformAuthController{
 private final PlatformAuthService auth; public PlatformAuthController(PlatformAuthService a){auth=a;}
 @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,String>b){return auth.login(b.get("username"),b.get("password"));}
 @PostMapping("/refresh") public Map<String,Object> refresh(@RequestBody Map<String,String>b){return auth.refresh(b.get("refreshToken"));}
 @PostMapping("/logout") public ResponseEntity<Void> logout(@RequestBody Map<String,String>b){auth.logout(b.get("refreshToken"));return ResponseEntity.noContent().build();}
 @PostMapping("/setup-password") public ResponseEntity<Void> setupPassword(@RequestBody Map<String,String>b){auth.setupPassword(b.get("token"),b.get("password"));return ResponseEntity.noContent().build();}
}
