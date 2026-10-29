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
package org.broadleafcommerce.core.search.service;

import java.math.BigDecimal;

import junit.framework.TestCase;

public class SearchFacetRangeParserTest extends TestCase {

    public void testParsesClosedRange() {
        BigDecimal[] range = SearchFacetRangeParser.parse("range[0:5]");
        assertEquals(0, new BigDecimal("0").compareTo(range[0]));
        assertEquals(0, new BigDecimal("5").compareTo(range[1]));
    }

    public void testParsesOpenEndedAndDecimalRange() {
        BigDecimal[] range = SearchFacetRangeParser.parse("range[-10.50:null]");
        assertEquals("-10.50", range[0].toPlainString());
        assertNull(range[1]);
        range = SearchFacetRangeParser.parse("range[10.00000:20.00000]");
        assertEquals("20.00000", range[1].toPlainString());
    }

    public void testRejectsExponentNotation() {
        assertNull(SearchFacetRangeParser.parse("range[1e999999999:null]"));
        assertNull(SearchFacetRangeParser.parse("range[0:1E+999999999]"));
        assertNull(SearchFacetRangeParser.parse("range[1e-999999999:5]"));
    }

    public void testRejectsOverlongBounds() {
        assertNull(SearchFacetRangeParser.parse("range[12345678901234567890:null]"));
        assertNull(SearchFacetRangeParser.parse("range[0.12345678901:null]"));
    }

    public void testRejectsMalformedValues() {
        assertNull(SearchFacetRangeParser.parse("range[1"));
        assertNull(SearchFacetRangeParser.parse("range[1]"));
        assertNull(SearchFacetRangeParser.parse("range[1:]"));
        assertNull(SearchFacetRangeParser.parse("range[:5]"));
        assertNull(SearchFacetRangeParser.parse("range[null:5]"));
        assertNull(SearchFacetRangeParser.parse("range[1:2:3]"));
        assertNull(SearchFacetRangeParser.parse("range[NaN:null]"));
        assertNull(SearchFacetRangeParser.parse("range[ 1:2]"));
        assertNull(SearchFacetRangeParser.parse("blue"));
        assertNull(SearchFacetRangeParser.parse(null));
    }

}
