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
package org.broadleafcommerce.core.catalog.service;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CatalogURLServiceImplTest {

    protected CatalogURLServiceImpl service;

    @Before
    public void setUp() {
        service = new CatalogURLServiceImpl();
        service.appendIdToRelativeURI = true;
        service.productIdParam = "productId";
        service.categoryIdParam = "categoryId";
    }

    @Test
    public void testAppendsFragmentAndId() {
        assertEquals("/hot-sauces/sudden-death?productId=1",
                service.buildRelativeUrlWithParam("/hot-sauces", "sudden-death", "productId", "1"));
        assertEquals("/sudden-death?productId=1",
                service.buildRelativeUrlWithParam("/", "sudden-death", "productId", "1"));
    }

    @Test
    public void testKeepsEncodedQueryParameters() {
        String url = service.buildRelativeUrlWithParam("/hot-sauces?q=a+b&productId=7", "sudden-death", "productId", "1");
        assertEquals("/hot-sauces/sudden-death?q=a+b&productId=1", url);
    }

    @Test
    public void testUnparseableUrlIsNotReflected() {
        String url = service.buildRelativeUrlWithParam("/hot-sauces?a=%zz\"onmouseover=\"alert(1)", "sudden-death",
                "productId", "1");
        assertEquals("/sudden-death?productId=1", url);
        assertFalse(url.contains("\""));
    }

    @Test
    public void testFragmentIsEncoded() {
        String url = service.buildRelativeUrlWithParam("/hot-sauces", "a\"b", "productId", "1");
        assertFalse(url.contains("\""));
    }

}
