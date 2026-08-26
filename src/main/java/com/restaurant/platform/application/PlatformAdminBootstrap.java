package com.restaurant.platform.application;
import org.springframework.boot.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
@Component public class PlatformAdminBootstrap implements ApplicationRunner{
 private final PlatformAuthService auth;private final Environment env;public PlatformAdminBootstrap(PlatformAuthService a,Environment e){auth=a;env=e;}
 public void run(ApplicationArguments args){String email=env.getProperty("APP_PLATFORM_ADMIN_EMAIL"),password=env.getProperty("APP_PLATFORM_ADMIN_PASSWORD");if(email==null&&password==null)return;auth.bootstrap(email,password,env.getProperty("APP_PLATFORM_ADMIN_NAME"));}
}
