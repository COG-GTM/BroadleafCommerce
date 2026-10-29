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
import org.broadleafcommerce.core.search.domain.Field;
import org.broadleafcommerce.core.search.domain.SearchCriteria;
import org.broadleafcommerce.core.search.domain.SearchFacet;
import org.broadleafcommerce.core.search.domain.SearchFacetDTO;
import org.easymock.EasyMock;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class SolrHelperServiceImplActiveFacetFilterTest extends TestCase {

    private static final String SOLR_KEY = "price_td";

    private SolrHelperServiceImpl service;
    private Map<String, SearchFacetDTO> namedFacetMap;

    @Override
    protected void setUp() {
        service = new SolrHelperServiceImpl();
        service.searchExtensionManager = new SolrSearchServiceExtensionManager();

        Field field = EasyMock.createNiceMock(Field.class);
        EasyMock.expect(field.getAbbreviation()).andReturn("price").anyTimes();
        SearchFacet facet = EasyMock.createNiceMock(SearchFacet.class);
        EasyMock.expect(facet.getField()).andReturn(field).anyTimes();
        EasyMock.replay(field, facet);

        SearchFacetDTO dto = new SearchFacetDTO();
        dto.setFacet(facet);
        namedFacetMap = new HashMap<>();
        namedFacetMap.put(SOLR_KEY, dto);
    }

    private String[] attach(String... values) {
        Map<String, String[]> filterCriteria = new HashMap<>();
        filterCriteria.put("price", values);
        SearchCriteria criteria = new SearchCriteria();
        criteria.setFilterCriteria(filterCriteria);
        SolrQuery query = new SolrQuery();
        service.attachActiveFacetFilters(query, namedFacetMap, criteria);
        return query.getFilterQueries();
    }

    public void testValidRangeBecomesFilterQuery() {
        String[] fqs = attach("range[0:5]", "range[10:null]");
        assertTrue(namedFacetMap.get(SOLR_KEY).isActive());
        assertEquals(1, fqs.length);
        assertEquals("{!tag=price_td}price_td:(price_td:[0 TO 5] OR price_td:[10 TO *])", fqs[0]);
    }

    public void testHugeExponentIsIgnored() {
        String[] fqs = attach("range[1e999999999:null]");
        assertNull(fqs);
        assertFalse(namedFacetMap.get(SOLR_KEY).isActive());
    }

    public void testHugeExponentDroppedButValidRangeKept() {
        String[] fqs = attach("range[0:1E+999999999]", "range[0:5]");
        assertEquals(1, fqs.length);
        assertEquals("{!tag=price_td}price_td:(price_td:[0 TO 5])", fqs[0]);
    }

    public void testMalformedRangesDoNotThrow() {
        assertNull(attach("range[1", "range[1]", "range[1:]", "range[abc:null]"));
    }

}
