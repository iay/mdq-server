/*
 * Copyright (C) 2024 Ian A. Young.
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

import java.util.Set;

import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class JettyCustomizer implements WebServerFactoryCustomizer<JettyServletWebServerFactory> {

    @Override
    public void customize(JettyServletWebServerFactory factory) {
        factory.addServerCustomizers(this::customizeUriCompliance);
    }

    private void customizeUriCompliance(Server server) {
        for (Connector connector : server.getConnectors()) {
            connector.getConnectionFactories().stream()
                    .filter(factory -> factory instanceof HttpConnectionFactory)
                    .forEach(factory -> {
                        HttpConfiguration httpConfig = ((HttpConnectionFactory) factory).getHttpConfiguration();
                        httpConfig.setUriCompliance(UriCompliance.from(Set.of(
                                UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR,
                                UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING)));
                    });
        }
    }
}
