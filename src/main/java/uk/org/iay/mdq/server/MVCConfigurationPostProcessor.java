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

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
 
/**
 * Spring's default behavior is to decode the URL passed to it by the
 * container, and then parse the path into components on "/" characters.
 * This means you can't use URL-encoded "/" characters (%2f) within
 * identifiers.
 * 
 * <p>
 * See, for example, <a href="https://jira.springsource.org/browse/SPR-11101">
 * SPR-11101</a>.
 * </p>
 * 
 * <p>
 * To fix this, poke the bean responsible until it does what we want.
 * Fortunately, the implementation looks at the appropriate properties
 * on every request so we can do this even after the application has started.
 * </p>
 *
 * <p>
 * Originally from <a href="https://gist.github.com/MikeN123/8873622">this Gist</a>.
 * </p>
 */
public class MVCConfigurationPostProcessor implements BeanPostProcessor, PriorityOrdered {
 
    @Override
    public Object postProcessBeforeInitialization(final Object bean, final String beanName) {
        if (bean instanceof RequestMappingHandlerMapping) {
            final RequestMappingHandlerMapping requestMappingHandlerMapping = (RequestMappingHandlerMapping) bean;
 
            requestMappingHandlerMapping.setUseSuffixPatternMatch(false);
            requestMappingHandlerMapping.setUseTrailingSlashMatch(false);
 
            // URL decode after request mapping, not before.
            requestMappingHandlerMapping.setUrlDecode(false);
        }
 
        return bean;
    }
 
    @Override
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        return bean;
    }
 
    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }
}
