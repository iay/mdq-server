/*
 * Copyright (C) 2014 Ian A. Young.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.iay.mdq.server;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

/**
 * Application bootstrap class.
 */
//Checkstyle: FinalClass|HideUtilityClassConstructor OFF (required for Spring Boot)
@Configuration
@EnableAutoConfiguration
@ComponentScan
public class Application {
// Checkstyle: FinalClass|HideUtilityClassConstructor ON
    
    /**
     * Main entry point; invokes the web server using Spring Boot.
     * 
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        /*
         * Resources for additional bean definitions.
         */
        final ClassPathResource beans = new ClassPathResource("beans.xml");
        
        /*
         * Construct the application.
         */
        final SpringApplication app = new SpringApplication(Application.class, beans);
        
        /*
         * Customize the application.
         */
        app.setShowBanner(false);
        
        final ApplicationContext ctx = app.run(args);

        /*
         * Spring's default behaviour is to decode the URL passed to it by the
         * container, and then parse the path into components on "/" characters.
         * This means you can't use URL-encoded "/" characters (%2f) within
         * identifiers.
         * 
         * See, for example, https://jira.springsource.org/browse/SPR-11101
         * 
         * To fix this, poke the bean responsible until it does what we want.
         * Fortunately, the implementation looks at the appropriate properties
         * on every request so we can do this even after the application has started.
         */
        final AbstractHandlerMapping ahm = ctx.getBean("requestMappingHandlerMapping",
                AbstractHandlerMapping.class);
        ahm.setUrlDecode(false);
        
        System.out.println("Let's inspect the beans provided by Spring Boot:");

        String[] beanNames = ctx.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            System.out.println(beanName);
        }
    }

}
