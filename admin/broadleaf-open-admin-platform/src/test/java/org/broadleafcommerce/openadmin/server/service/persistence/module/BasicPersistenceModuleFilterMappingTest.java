/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
 * %%
 * Copyright (C) 2009 - 2026 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.openadmin.server.service.persistence.module;

import org.broadleafcommerce.common.presentation.client.SupportedFieldType;
import org.broadleafcommerce.openadmin.dto.BasicFieldMetadata;
import org.broadleafcommerce.openadmin.dto.CriteriaTransferObject;
import org.broadleafcommerce.openadmin.dto.FieldMetadata;
import org.broadleafcommerce.openadmin.dto.FilterAndSortCriteria;
import org.broadleafcommerce.openadmin.server.service.persistence.module.criteria.FilterMapping;
import org.broadleafcommerce.openadmin.server.service.persistence.module.provider.FieldPersistenceProviderAdapter;
import org.broadleafcommerce.openadmin.server.service.persistence.module.provider.request.AddSearchMappingRequest;
import org.broadleafcommerce.openadmin.server.service.type.MetadataProviderResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BasicPersistenceModuleFilterMappingTest {

    private static final String ADMIN_USER = "org.broadleafcommerce.openadmin.server.security.domain.AdminUserImpl";

    private BasicPersistenceModule module;
    private RecordingFieldPersistenceProvider provider;
    private Map<String, FieldMetadata> mergedProperties;

    @Before
    public void setUp() {
        module = new BasicPersistenceModule() {
            @Override
            public FieldManager getFieldManager() {
                return null;
            }
        };
        provider = new RecordingFieldPersistenceProvider();
        module.defaultFieldPersistenceProvider = provider;

        mergedProperties = new HashMap<>();
        mergedProperties.put("id", metadata(SupportedFieldType.ID));
        mergedProperties.put("login", metadata(SupportedFieldType.STRING));
        mergedProperties.put("password", metadata(SupportedFieldType.PASSWORD));
        mergedProperties.put("passwordConfirm", metadata(SupportedFieldType.PASSWORD_CONFIRM));
        BasicFieldMetadata explicitPassword = metadata(SupportedFieldType.STRING);
        explicitPassword.setExplicitFieldType(SupportedFieldType.PASSWORD);
        mergedProperties.put("createdBy.password", explicitPassword);
    }

    @Test
    public void passwordFilterIsIgnoredWhileLoginFilterStillApplies() {
        CriteriaTransferObject cto = new CriteriaTransferObject();
        cto.add(new FilterAndSortCriteria("password", Arrays.asList("a")));
        cto.add(new FilterAndSortCriteria("login", Arrays.asList("admin")));

        List<FilterMapping> mappings = module.getFilterMappings(null, cto, ADMIN_USER, mergedProperties);

        assertEquals(Arrays.asList("login"), provider.searchedProperties);
        assertEquals(1, mappings.size());
        assertEquals("login", mappings.get(0).getFullPropertyName());
    }

    @Test
    public void passwordConfirmAndExplicitPasswordFieldsAreNotFilterable() {
        CriteriaTransferObject cto = new CriteriaTransferObject();
        cto.add(new FilterAndSortCriteria("passwordConfirm", Arrays.asList("a")));
        cto.add(new FilterAndSortCriteria("createdBy.password", Arrays.asList("a")));

        List<FilterMapping> mappings = module.getFilterMappings(null, cto, ADMIN_USER, mergedProperties);

        assertTrue(mappings.isEmpty());
        assertTrue(provider.searchedProperties.isEmpty());
    }

    @Test
    public void passwordSortIsIgnored() {
        CriteriaTransferObject cto = new CriteriaTransferObject();
        FilterAndSortCriteria sort = new FilterAndSortCriteria("password");
        sort.setSortAscending(true);
        cto.add(sort);

        List<FilterMapping> mappings = module.getFilterMappings(null, cto, ADMIN_USER, mergedProperties);

        assertTrue(mappings.isEmpty());
    }

    @Test
    public void isFilterablePropertyRejectsOnlyPasswordTypes() {
        assertFalse(module.isFilterableProperty(mergedProperties.get("password")));
        assertFalse(module.isFilterableProperty(mergedProperties.get("passwordConfirm")));
        assertFalse(module.isFilterableProperty(mergedProperties.get("createdBy.password")));
        assertTrue(module.isFilterableProperty(mergedProperties.get("login")));
        assertTrue(module.isFilterableProperty(mergedProperties.get("id")));
    }

    private static BasicFieldMetadata metadata(SupportedFieldType fieldType) {
        BasicFieldMetadata metadata = new BasicFieldMetadata();
        metadata.setFieldType(fieldType);
        return metadata;
    }

    private static class RecordingFieldPersistenceProvider extends FieldPersistenceProviderAdapter {

        private final List<String> searchedProperties = new ArrayList<>();

        @Override
        public MetadataProviderResponse addSearchMapping(AddSearchMappingRequest addSearchMappingRequest,
                                                         List<FilterMapping> filterMappings) {
            searchedProperties.add(addSearchMappingRequest.getPropertyName());
            filterMappings.add(new FilterMapping().withFullPropertyName(addSearchMappingRequest.getPropertyName()));
            return MetadataProviderResponse.HANDLED;
        }
    }
}
