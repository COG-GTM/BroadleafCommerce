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
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.broadleafcommerce.common.config.service.SystemPropertiesService;
import org.broadleafcommerce.core.search.dao.IndexFieldDao;
import org.broadleafcommerce.core.search.domain.FieldImpl;
import org.broadleafcommerce.core.search.domain.IndexFieldImpl;
import org.broadleafcommerce.core.search.domain.IndexFieldType;
import org.broadleafcommerce.core.search.domain.IndexFieldTypeImpl;
import org.broadleafcommerce.core.search.domain.SearchCriteria;
import org.broadleafcommerce.core.search.domain.solr.FieldType;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SolrHelperServiceImplSortTest {

    protected SolrHelperServiceImpl helper;
    protected IndexFieldDao indexFieldDao;
    protected SystemPropertiesService systemPropertiesService;
    protected String programmaticSortFields = "score";

    @Before
    public void setUp() {
        indexFieldDao = EasyMock.createNiceMock(IndexFieldDao.class);
        EasyMock.expect(indexFieldDao.getIndexFieldTypesByAbbreviation("price"))
                .andReturn(Collections.<IndexFieldType>singletonList(indexFieldType("price", FieldType.PRICE)))
                .anyTimes();
        EasyMock.expect(indexFieldDao.getIndexFieldTypesByAbbreviation(EasyMock.anyString()))
                .andReturn(Collections.<IndexFieldType>emptyList()).anyTimes();

        systemPropertiesService = EasyMock.createNiceMock(SystemPropertiesService.class);
        EasyMock.expect(systemPropertiesService.resolveSystemProperty("solr.sortable.field.types"))
                .andReturn("sort,s,p,i,l").anyTimes();
        EasyMock.expect(systemPropertiesService.resolveSystemProperty(EasyMock.eq("solr.sort.programmatic.fields"),
                EasyMock.anyString())).andAnswer(() -> programmaticSortFields).anyTimes();

        EasyMock.replay(indexFieldDao, systemPropertiesService);

        helper = new SolrHelperServiceImpl();
        helper.indexFieldDao = indexFieldDao;
        helper.systemPropertiesService = systemPropertiesService;
        helper.searchExtensionManager = new SolrSearchServiceExtensionManager();
    }

    protected IndexFieldType indexFieldType(String abbreviation, FieldType type) {
        FieldImpl field = new FieldImpl();
        field.setAbbreviation(abbreviation);
        IndexFieldImpl indexField = new IndexFieldImpl();
        indexField.setField(field);
        IndexFieldTypeImpl fieldType = new IndexFieldTypeImpl();
        fieldType.setIndexField(indexField);
        fieldType.setFieldType(type);
        return fieldType;
    }

    protected SolrQuery sort(String requestSort, String defaultSort) {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setSortQuery(requestSort);
        SolrQuery query = new SolrQuery("*:*");
        helper.attachSortClause(query, criteria, defaultSort);
        return query;
    }

    @Test
    public void managedIndexFieldIsSortedByGeneratedFieldName() {
        SolrQuery query = sort("price desc", null);
        assertEquals(Collections.singletonList(new SortClause("price_p", ORDER.desc)), query.getSorts());
        assertEquals("price_p desc", query.get("sort"));
    }

    @Test
    public void functionQuerySortFromRequestIsIgnored() {
        assertTrue(sort("sum(1,1) asc", null).getSorts().isEmpty());
        assertTrue(sort("strdist(en_name_s,aaaaaaaaaa,edit) desc", null).getSorts().isEmpty());
        assertTrue(sort("log(price_p) desc", null).getSorts().isEmpty());
        assertTrue(sort("ms(NOW) asc", null).getSorts().isEmpty());
        assertNull(sort("sum(1,1) asc", null).get("sort"));
    }

    @Test
    public void unmanagedIndexedFieldFromRequestIsIgnored() {
        assertTrue(sort("en_name_s desc", null).getSorts().isEmpty());
        assertTrue(sort("category_2002_sort asc", null).getSorts().isEmpty());
    }

    @Test
    public void allowlistedProgrammaticFieldFromRequestIsSorted() {
        assertEquals(Collections.singletonList(new SortClause("score", ORDER.desc)),
                sort("score desc", null).getSorts());

        programmaticSortFields = "score, popularity_i";
        assertEquals(Collections.singletonList(new SortClause("popularity_i", ORDER.asc)),
                sort("popularity_i asc", null).getSorts());
    }

    @Test
    public void allowlistedFieldStillMustBeAPlainFieldName() {
        programmaticSortFields = "score,sum(1";
        assertTrue(sort("sum(1,1) asc", null).getSorts().isEmpty());
    }

    @Test
    public void invalidSortsAreDroppedButValidOnesAreKept() {
        List<SortClause> sorts = sort("price asc,sum(1,1) desc,en_name_s asc,score desc", null).getSorts();
        assertEquals(2, sorts.size());
        assertEquals(new SortClause("price_p", ORDER.asc), sorts.get(0));
        assertEquals(new SortClause("score", ORDER.desc), sorts.get(1));
    }

    @Test
    public void applicationDefaultSortIsStillApplied() {
        assertEquals(Collections.singletonList(new SortClause("category_2002_sort", ORDER.asc)),
                sort(null, "category_2002_sort asc").getSorts());
    }

    @Test
    public void defaultSortMustStillBeAPlainFieldName() {
        assertTrue(sort(null, "sum(1) asc").getSorts().isEmpty());
    }
}
