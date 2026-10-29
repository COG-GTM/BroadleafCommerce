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
package org.broadleafcommerce.openadmin.web.controller;

import org.broadleafcommerce.openadmin.dto.FilterAndSortCriteria;
import org.junit.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AdminAbstractControllerPageSizeTest {

    private static final int MAX_PAGE_SIZE = 200;

    private final AdminAbstractController controller = new AdminAbstractController() {
        @Override
        protected int getMaxPageSize() {
            return MAX_PAGE_SIZE;
        }
    };

    @Test
    public void pageSizeAboveMaximumIsClamped() {
        assertEquals(Integer.valueOf(MAX_PAGE_SIZE), controller.getPageSize(params(FilterAndSortCriteria.PAGE_SIZE_PARAMETER, "100000000")));
        assertEquals(Integer.valueOf(MAX_PAGE_SIZE), controller.getPageSize(params(FilterAndSortCriteria.PAGE_SIZE_PARAMETER, String.valueOf(Integer.MAX_VALUE))));
    }

    @Test
    public void pageSizeWithinMaximumIsKept() {
        assertEquals(Integer.valueOf(25), controller.getPageSize(params(FilterAndSortCriteria.PAGE_SIZE_PARAMETER, "25")));
        assertEquals(Integer.valueOf(MAX_PAGE_SIZE), controller.getPageSize(params(FilterAndSortCriteria.PAGE_SIZE_PARAMETER, String.valueOf(MAX_PAGE_SIZE))));
    }

    @Test
    public void nonPositivePageSizeFallsBackToDefault() {
        assertNull(controller.getPageSize(params(FilterAndSortCriteria.PAGE_SIZE_PARAMETER, "0")));
        assertNull(controller.getPageSize(params(FilterAndSortCriteria.PAGE_SIZE_PARAMETER, "-5")));
        assertNull(controller.getPageSize(params(FilterAndSortCriteria.START_INDEX_PARAMETER, "0")));
        assertNull(controller.getPageSize(null));
    }

    @Test
    public void maxResultsIsClamped() {
        assertEquals(Integer.valueOf(MAX_PAGE_SIZE), controller.getMaxResults(params(FilterAndSortCriteria.MAX_RESULTS_PARAMETER, "5000000")));
        assertEquals(Integer.valueOf(10), controller.getMaxResults(params(FilterAndSortCriteria.MAX_RESULTS_PARAMETER, "10")));
        assertNull(controller.getMaxResults(params(FilterAndSortCriteria.MAX_RESULTS_PARAMETER, "-1")));
    }

    private static MultiValueMap<String, String> params(String name, String value) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(name, value);
        return params;
    }
}
