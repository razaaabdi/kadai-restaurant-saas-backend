package com.restaurant.platform.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.platform.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformReadService {
 private final JdbcTemplate jdbc; private final ObjectMapper json=new ObjectMapper();
 public PlatformReadService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 @Transactional(readOnly=true) public Map<String,Object> dashboard(){return object(single("select platform_dashboard()::text"));}
 @Transactional(readOnly=true) public List<Map<String,Object>> plans(){return array(single("select platform_plans()::text"));}
 @Transactional(readOnly=true) public List<Map<String,Object>> restaurants(String search,String status){return array(jdbc.queryForObject("select platform_restaurants(?,?)::text",String.class,search,status));}
 @Transactional(readOnly=true) public Map<String,Object> restaurant(UUID id){String value=jdbc.queryForObject("select platform_restaurant(?)::text",String.class,id);if(value==null)throw ApiException.notFound("RESTAURANT","Restaurant not found");return object(value);}
 @Transactional(readOnly=true) public List<Map<String,Object>> audits(String tenantId,String search){UUID id=tenantId==null||tenantId.isBlank()?null:UUID.fromString(tenantId);return array(jdbc.queryForObject("select platform_audits(?,?)::text",String.class,id,search));}
 private String single(String sql){return jdbc.queryForObject(sql,String.class);}
 private Map<String,Object> object(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid platform projection",e);}}
 private List<Map<String,Object>> array(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid platform projection",e);}}
}
