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
package org.broadleafcommerce.core.search.service.solr.indexer;

import org.easymock.EasyMock;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertTrue;

public class CommandCoordinatorTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void nonCommandQueueValueDoesNotStopTheCoordinator() throws Exception {
        String group = "coordinatorTest";
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        queue.add("not a command");
        queue.add(FullReindexCommand.DEFAULT_INSTANCE);
        CountDownLatch executed = new CountDownLatch(1);

        SolrIndexUpdateCommandHandler handler = EasyMock.createNiceMock(SolrIndexUpdateCommandHandler.class);
        EasyMock.expect(handler.getCommandGroup()).andReturn(group).anyTimes();
        handler.executeCommand(EasyMock.anyObject(FullReindexCommand.class));
        EasyMock.expectLastCall().andAnswer(() -> {
            executed.countDown();
            return null;
        });
        SolrIndexQueueProvider provider = EasyMock.createNiceMock(SolrIndexQueueProvider.class);
        EasyMock.expect((BlockingQueue) provider.createOrRetrieveCommandQueue(EasyMock.anyString())).andReturn(queue);
        EasyMock.expect(provider.createOrRetrieveCommandLock(EasyMock.anyString())).andReturn(new ReentrantLock());
        EasyMock.replay(handler, provider);

        AbstractSolrIndexUpdateServiceImpl service = new AbstractSolrIndexUpdateServiceImpl(group, provider, handler) {};
        try {
            assertTrue("The coordinator must keep polling after a non-command value",
                    executed.await(10, TimeUnit.SECONDS));
        } finally {
            service.destroy();
        }
    }
}
