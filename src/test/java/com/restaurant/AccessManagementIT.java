package com.restaurant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AccessManagementIT extends AbstractIT {
 @Test @SuppressWarnings("unchecked")
 void ownerCanDelegateButManagerCannotEscalatePrivileges() {
  Http api=new Http("http://localhost:"+port);
  var onboard=api.post("/api/v1/onboarding","""
    {"name":"RBAC Cafe","slug":"rbac-cafe","email":"rbac-owner@test.com","password":"secret12","ownerName":"Owner"}
    """);
  String outlet=Http.uuid(onboard,"outletId");
  var ownerLogin=api.post("/api/v1/auth/login","{\"email\":\"rbac-owner@test.com\",\"password\":\"secret12\"}");
  assertThat((List<String>)ownerLogin.get("permissions")).contains("USER_MANAGE","BRANDING_TENANT_EDIT");
  api.auth(Http.uuid(ownerLogin,"accessToken"));
  var catalog=api.get("/api/v1/users/access-catalog");
  assertThat((List<?>)catalog.get("roles")).isNotEmpty();
  api.post("/api/v1/users","{"+"\"name\":\"Manager\",\"email\":\"rbac-manager@test.com\",\"employeeCode\":\"MGR001\",\"password\":\"manager1234\",\"role\":\"MANAGER\",\"outletIds\":[\""+outlet+"\"]}");
  var managerLogin=new Http("http://localhost:"+port).post("/api/v1/auth/login","{\"email\":\"rbac-manager@test.com\",\"password\":\"manager1234\"}");
  Http manager=new Http("http://localhost:"+port).auth(Http.uuid(managerLogin,"accessToken"));
  var managerPermissionChange=manager.putRaw("/api/v1/users/roles/CASHIER/permissions","{\"permissions\":[\"FLOOR_VIEW\"]}",0);
  assertThat(managerPermissionChange.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  var ownerPermissionChange=api.putRaw("/api/v1/users/roles/CASHIER/permissions","{\"permissions\":[\"FLOOR_VIEW\",\"ORDER_VIEW\"]}",0);
  assertThat(ownerPermissionChange.getStatusCode()).isEqualTo(HttpStatus.OK);
  assertThat(ownerPermissionChange.getBody()).contains("FLOOR_VIEW","ORDER_VIEW");
  var ownerPermissionChangeRejected=api.putRaw("/api/v1/users/roles/OWNER/permissions","{\"permissions\":[\"FLOOR_VIEW\"]}",0);
  assertThat(ownerPermissionChangeRejected.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  var escalation=manager.postRaw("/api/v1/users","{"+"\"name\":\"Fake Owner\",\"email\":\"fake-owner@test.com\",\"employeeCode\":\"OWN002\",\"password\":\"ownerpass123\",\"role\":\"OWNER\",\"outletIds\":[\""+outlet+"\"]}",null);
  assertThat(escalation.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  var cashier=manager.post("/api/v1/users","{"+"\"name\":\"Cashier\",\"email\":\"rbac-cashier@test.com\",\"employeeCode\":\"CSH001\",\"password\":\"cashier1234\",\"role\":\"CASHIER\",\"outletIds\":[\""+outlet+"\"]}");
  assertThat(cashier.get("role")).isEqualTo("CASHIER");
  String cashierId=Http.uuid(cashier,"id");
  var updated=api.putRaw("/api/v1/users/"+cashierId,"{\"name\":\"Cashier\",\"role\":\"CASHIER\",\"status\":\"SUSPENDED\",\"outletIds\":[\""+outlet+"\"],\"approvalLimits\":{\"discountBps\":250,\"refundPaise\":0,\"voidPaise\":0,\"stockAdjustmentPaise\":0}}",0);
  assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
  var suspendedLogin=new Http("http://localhost:"+port).postRaw("/api/v1/auth/login","{\"email\":\"rbac-cashier@test.com\",\"password\":\"cashier1234\"}",null);
  assertThat(suspendedLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
 }
}
