/*-
 * #%L
 * BroadleafCommerce Profile
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
package org.broadleafcommerce.profile.util.dao;

import org.broadleafcommerce.common.util.dao.BatchRetrieveDao;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Query;
import junit.framework.TestCase;

/**
 * 
 * @author jfischer
 *
 */
public class BatchRetrieveDaoTest extends TestCase {
    
    private static final int BATCHSIZE = 5;
    private BatchRetrieveDao dao;
    private Query queryMock;
    
    @Override
    protected void setUp() throws Exception {
        dao = new BatchRetrieveDao();
        queryMock = Mockito.mock(Query.class);
        List<String> response = new ArrayList<String>();
        response.add("test");
        Mockito.when(queryMock.getResultList()).thenReturn(response);
        Mockito.when(queryMock.setParameter(Mockito.eq("test"), Mockito.isA(List.class))).thenReturn(queryMock);
    }

    public void testFilter() throws Exception {
        dao.setInClauseBatchSize(BATCHSIZE);
        List<Integer> keys = new ArrayList<Integer>();
        for (int j = 0; j < 10; j++) {
            keys.add(j);
        }
        List<Object> response = dao.batchExecuteReadQuery(queryMock, keys, "test");
        assertTrue(response.size() == 2);
        Mockito.verify(queryMock, Mockito.times(2)).getResultList();
        Mockito.verify(queryMock, Mockito.times(2)).setParameter(Mockito.eq("test"), Mockito.isA(List.class));
    }

}
