package com.restaurant.configuration.api;
import java.time.Instant; import java.util.Map;
public record BrandingResponse(Map<String,Object> branding,long tenantVersion,Long outletVersion,String effectiveVersion,Instant updatedAt){}
