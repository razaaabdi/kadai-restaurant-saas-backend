package com.restaurant.catalog.api;

import com.restaurant.catalog.application.MenuImageService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class MenuImageController {
    private final MenuImageService images;

    public MenuImageController(MenuImageService images) { this.images = images; }

    @PostMapping(value = "/outlets/{outletId}/menu-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable UUID outletId, @RequestPart("file") MultipartFile file) {
        return images.upload(outletId, file);
    }

    @GetMapping("/public/menu-images/{imageId}")
    public ResponseEntity<byte[]> image(@PathVariable UUID imageId) {
        MenuImageService.ImageData image = images.get(imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic()).body(image.content());
    }
}
