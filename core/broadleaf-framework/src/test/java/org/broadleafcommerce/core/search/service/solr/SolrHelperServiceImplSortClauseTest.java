/*-
 * #%L
 * BroadleafCommerce Framework
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
package org.broadleafcommerce.core.search.service.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.broadleafcommerce.common.config.service.SystemPropertiesService;
import org.broadleafcommerce.core.search.dao.IndexFieldDao;
import org.broadleafcommerce.core.search.domain.FieldImpl;
import org.broadleafcommerce.core.search.domain.IndexField;
import org.broadleafcommerce.core.search.domain.IndexFieldImpl;
import org.broadleafcommerce.core.search.domain.IndexFieldType;
import org.broadleafcommerce.core.search.domain.IndexFieldTypeImpl;
import org.broadleafcommerce.core.search.domain.SearchCriteria;
import org.broadleafcommerce.core.search.domain.solr.FieldType;
import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;

public class SolrHelperServiceImplSortClauseTest extends TestCase {

    protected SolrHelperServiceImpl service;

    @Override
    protected void setUp() {
        SystemPropertiesService systemPropertiesService = EasyMock.createNiceMock(SystemPropertiesService.class);
        EasyMock.expect(systemPropertiesService.resolveSystemProperty("solr.sortable.field.types"))
                .andReturn("sort,s,p,i,l").anyTimes();
        EasyMock.expect(systemPropertiesService.resolveSystemProperty("solr.sort.allowed.unmanaged.fields", "score"))
                .andReturn("score, popularity_i").anyTimes();

        IndexFieldDao indexFieldDao = EasyMock.createNiceMock(IndexFieldDao.class);
        EasyMock.expect(indexFieldDao.getIndexFieldTypesByAbbreviation("price"))
                .andReturn(Collections.singletonList(indexFieldType("price", FieldType.PRICE))).anyTimes();
        EasyMock.expect(indexFieldDao.getIndexFieldTypesByAbbreviation(EasyMock.anyString()))
                .andReturn(Collections.<IndexFieldType>emptyList()).anyTimes();
        EasyMock.replay(systemPropertiesService, indexFieldDao);

        service = new SolrHelperServiceImpl() {
            @Override
            public String getPropertyNameForIndexField(IndexField field, FieldType fieldType) {
                return field.getField().getAbbreviation() + "_" + fieldType.getType();
            }
        };
        service.systemPropertiesService = systemPropertiesService;
        service.indexFieldDao = indexFieldDao;
        service.searchExtensionManager = new SolrSearchServiceExtensionManager();
    }

    public void testManagedIndexFieldIsSorted() {
        assertEquals(Collections.singletonList("price_p desc"), sorts("price desc", null));
    }

    public void testUnmanagedRequestedFieldIsIgnored() {
        assertEquals(Collections.emptyList(), sorts("someRandomField asc", null));
        assertEquals(Collections.emptyList(), sorts("description_t desc", null));
    }

    public void testFunctionQueryIsIgnored() {
        assertEquals(Collections.emptyList(), sorts("ord(name_t) desc", null));
        assertEquals(Collections.emptyList(), sorts("{!func}name_t desc", null));
        assertEquals(Collections.emptyList(), sorts("field(name_t) asc", null));
    }

    public void testAllowlistedUnmanagedFieldIsSorted() {
        assertEquals(Collections.singletonList("popularity_i desc"), sorts("popularity_i desc", null));
        assertEquals(Collections.singletonList("score desc"), sorts("score desc", null));
    }

    public void testOnlyInvalidFieldsAreDroppedFromMixedSort() {
        assertEquals(Collections.singletonList("price_p asc"), sorts("ord(name_t) desc,price asc", null));
    }

    public void testFallsBackToDefaultSortWhenNoRequestedSortIsUsable() {
        assertEquals(Collections.singletonList("category_1_sort_i asc"),
                sorts("ord(name_t) desc", "category_1_sort_i asc"));
    }

    public void testDefaultSortIsTrusted() {
        assertEquals(Collections.singletonList("category_1_sort_i asc"), sorts(null, "category_1_sort_i asc"));
    }

    public void testDefaultSortMustStillBePlainFieldName() {
        assertEquals(Collections.emptyList(), sorts(null, "ord(name_t) asc"));
    }

    protected List<String> sorts(String requestedSort, String defaultSort) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSortQuery(requestedSort);
        SolrQuery query = new SolrQuery();
        service.attachSortClause(query, criteria, defaultSort);
        List<String> result = new ArrayList<>();
        for (SortClause clause : query.getSorts()) {
            result.add(clause.getItem() + " " + clause.getOrder());
        }
        return result;
    }

    protected static IndexFieldType indexFieldType(String abbreviation, FieldType fieldType) {
        FieldImpl field = new FieldImpl();
        field.setAbbreviation(abbreviation);
        IndexFieldImpl indexField = new IndexFieldImpl();
        indexField.setField(field);
        IndexFieldTypeImpl type = new IndexFieldTypeImpl();
        type.setIndexField(indexField);
        type.setFieldType(fieldType);
        return type;
    }
}
