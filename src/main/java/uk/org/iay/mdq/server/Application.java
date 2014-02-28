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

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * Application bootstrap class.
 */
//Checkstyle: FinalClass|HideUtilityClassConstructor OFF (required for Spring Boot)
@Configuration
@EnableAutoConfiguration
@ComponentScan
@ImportResource("classpath:beans.xml")
public class Application {
// Checkstyle: FinalClass|HideUtilityClassConstructor ON
    
    /**
     * Establishes a bean post-processor to adjust the parsing of URLs so that
     * things like <code>/entities/x%2fy</code> work as desired.
     * 
     * @return a bean post-processor bean
     */
    @Bean
    public BeanPostProcessor mvcConfigurationPostProcessor() {
        return new MVCConfigurationPostProcessor();
    }
    
    /**
     * Main entry point; invokes the web server using Spring Boot.
     * 
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        /*
         * Construct the application.
         */
        final SpringApplication app = new SpringApplication(Application.class);
        
        /*
         * Customize the application.
         */
        app.setShowBanner(false);
        
        /*
         * Start the application.
         */
        final ApplicationContext ctx = app.run(args);

        System.out.println("Let's inspect the beans provided by Spring Boot:");

        String[] beanNames = ctx.getBeanDefinitionNames();
        Arrays.sort(beanNames);
        for (String beanName : beanNames) {
            System.out.println(beanName);
        }
    }

}
