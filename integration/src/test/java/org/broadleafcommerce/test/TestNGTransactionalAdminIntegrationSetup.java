/*-
 * #%L
 * BroadleafCommerce Integration
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
package org.broadleafcommerce.test;

import org.broadleafcommerce.common.util.TransactionUtils;
import org.broadleafcommerce.test.config.BroadleafAdminIntegrationTest;
import org.broadleafcommerce.test.config.BroadleafSiteIntegrationTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

/**
 * <p>
 * The {@literal @}Transactional version of the {@link TestNGAdminIntegrationSetup}. All {@literal @}Test methods contained
 * within any subclasses of this are run within the {@link TransactionUtils#DEFAULT_TRANSACTION_MANAGER} transaction manager.
 * 
 * <p>
 * You can get finer-grained control over which classes are {@code @Transactional} and which ones aren't by instead subclassing
 * {@link TestNGSiteIntegrationSetup} instead and annotating individual {@code @Test} methods.
 * 
 * @see SpringExtension
 * @see TransactionalTestExecutionListener
 * @see BroadleafSiteIntegrationTest
 * @author Phillip Verheyden (phillipuniverse)
 */
@BroadleafAdminIntegrationTest
@ExtendWith(SpringExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public abstract class TestNGTransactionalAdminIntegrationSetup {
    
}
