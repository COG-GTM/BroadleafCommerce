/*-
 * #%L
 * BroadleafCommerce Common Libraries
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
package org.broadleafcommerce.common.dialect;

import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * In Hibernate 6, CLOB handling for PostgreSQL uses string binding by default.
 * This class is retained for backward compatibility but delegates to the built-in
 * {@link VarcharJdbcType} descriptor, treating CLOBs as simple strings.
 *
 * @deprecated Use {@link VarcharJdbcType#INSTANCE} directly instead.
 */
@Deprecated
public class PostgreSQLClobTypeDescriptor {

    public static final JdbcType INSTANCE = VarcharJdbcType.INSTANCE;

    private PostgreSQLClobTypeDescriptor() {
    }
}
