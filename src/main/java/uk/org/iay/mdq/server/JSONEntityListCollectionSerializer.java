/*
 * Copyright (C) 2015 Ian A. Young.
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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;
import javax.xml.namespace.QName;

import net.shibboleth.metadata.Item;
import net.shibboleth.metadata.ItemCollectionSerializer;
import net.shibboleth.metadata.dom.saml.SAMLMetadataSupport;
import net.shibboleth.metadata.dom.saml.mdrpi.RegistrationAuthority;
import net.shibboleth.metadata.dom.saml.mdui.MDUISupport;
import net.shibboleth.shared.xml.AttributeSupport;
import net.shibboleth.shared.xml.ElementSupport;
import net.shibboleth.shared.xml.XMLConstants;

import org.w3c.dom.Element;

/**
 * An {@link ItemCollectionSerializer} that serializes a collection of SAML entities
 * into a JSON representation.
 * 
 * The generation of <code>registrarID</code> members relies on {@link RegistrationAuthority}
 * item metadata.
 */
class JSONEntityListCollectionSerializer implements ItemCollectionSerializer<Element> {

    /** QName of the IDPSSODescriptor element. */
    private static final @Nonnull QName IDP_SSO_DESCRIPTOR_NAME =
            new QName(SAMLMetadataSupport.MD_NS, "IDPSSODescriptor");

    /** QName of the SPSSODescriptor element. */
    private static final @Nonnull QName SP_SSO_DESCRIPTOR_NAME =
            new QName(SAMLMetadataSupport.MD_NS, "SPSSODescriptor");

    /** QName of the AttributeAuthorityDescriptor element. */
    private static final @Nonnull QName AA_DESCRIPTOR_NAME =
            new QName(SAMLMetadataSupport.MD_NS, "AttributeAuthorityDescriptor");

    /** QName of the mdui:UIInfo element. */
    private static final @Nonnull QName MDUI_UIINFO_NAME = new QName(MDUISupport.MDUI_NS, "UIInfo");

    /** Configured JSON generator factory. */
    private final JsonGeneratorFactory factory;

    /**
     * Constructor.
     * 
     * @param prettyPrinting whether to generate pretty-printed JSON
     */
    public JSONEntityListCollectionSerializer(final boolean prettyPrinting) {
        final Map<String, String> generatorConfig = new HashMap<>();
        if (prettyPrinting) {
            generatorConfig.put(JsonGenerator.PRETTY_PRINTING, "true");
        }
        factory = Json.createGeneratorFactory(generatorConfig);
    }
    
    /**
     * Extract an English language display name for a role descriptor.
     * 
     * The first mdui:DisplayName with an xml:lang="en" is returned.
     * 
     * @param role SAML role descriptor to process
     * @return a display name for the role descriptor, or <code>null</code>
     */
    @Nullable
    private String extractDisplayName(@Nonnull final Element role) {
        final Element extensions = ElementSupport.getFirstChildElement(role, SAMLMetadataSupport.EXTENSIONS_NAME);
        if (extensions != null) {
            final Element mdui = ElementSupport.getFirstChildElement(extensions, MDUI_UIINFO_NAME);
            if (mdui != null) {
                final List<Element> displayNames = ElementSupport.getChildElements(mdui, MDUISupport.DISPLAYNAME_NAME);
                for (final Element displayName : displayNames) {
                    final String lang = AttributeSupport.getAttributeValue(displayName,
                            XMLConstants.XML_LANG_ATTRIB_NAME);
                    if ("en".equals(lang)) {
                        return displayName.getTextContent();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extract the first role descriptor of the given type, and append it to the list
     * of roles to be displayed.
     * 
     * @param roles {@link List} of roles to be displayed
     * @param entity {@link Element} representing the entity
     * @param name {@link QName} for the role to be extracted
     */
    private void extractRole(final List<Element> roles,
            final @Nonnull Element entity, final @Nonnull QName name) {
        final Element role = ElementSupport.getFirstChildElement(entity, name);
        if (role != null) {
            roles.add(role);
        }
    }

    /**
     * Write a JSON object corresponding to a SAML role descriptor.
     * 
     * @param gen JSON generator to write to
     * @param role SAML role descriptor to process
     */
    private void writeRole(@Nonnull final JsonGenerator gen, @Nullable final Element role) {
        if (role != null) {
            gen.writeStartObject();
                gen.write("type", role.getLocalName());
                final String displayName = extractDisplayName(role);
                if (displayName != null) {
                    gen.write("displayName", displayName);
                }
            gen.writeEnd();
        }
    }

    /**
     * Write a JSON object corresponding to the {@link Item} to the {@link JsonGenerator}.
     * 
     * @param gen JSON generator to write to
     * @param entity the SAML entity descriptor
     * @param registrationAuthority the registration authority responsible for the entity, or <code>null</code>.
     */
    private void writeEntity(final JsonGenerator gen, final @Nonnull Element entity,
            final String registrationAuthority) {
        if (SAMLMetadataSupport.isEntityDescriptor(entity)) {
            gen.writeStartObject();
            gen.write("entityID", entity.getAttribute("entityID"));
            if (registrationAuthority != null) {
                gen.write("registrarID", registrationAuthority);
            }
            final List<Element> roles = new ArrayList<>();
            extractRole(roles, entity, IDP_SSO_DESCRIPTOR_NAME);
            extractRole(roles, entity, SP_SSO_DESCRIPTOR_NAME);
            extractRole(roles, entity, AA_DESCRIPTOR_NAME);
            if (!roles.isEmpty()) {
                gen.writeStartArray("roles");
                    for (final Element role : roles) {
                        writeRole(gen, role);
                    }
                gen.writeEnd();
            }
            gen.writeEnd();
        }
    }

    /**
     * Extract the registration authority for an entity, if present.
     * 
     * @param item entity from which to extract the registration authority
     * @return registration authority name, or <code>null</code>
     */
    @Nullable
    private String extractRegistrationAuthority(@Nonnull final Item<Element> item) {
        final List<RegistrationAuthority> registrars = item.getItemMetadata().get(RegistrationAuthority.class);
        if (registrars.isEmpty()) {
            return null;
        } else {
            return registrars.get(0).getRegistrationAuthority();
        }
    }

    @Override
    public void serializeCollection(@Nonnull final Collection<Item<Element>> items,
            @Nonnull final OutputStream output) {
        final JsonGenerator gen = factory.createGenerator(output);
        gen.writeStartArray();
            for (final Item<Element> item : items) {
                writeEntity(gen, item.unwrap(), extractRegistrationAuthority(item));
            }
        gen.writeEnd();
        gen.close();
    }
    
}
