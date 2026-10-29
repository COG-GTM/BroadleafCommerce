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
import java.util.regex.Pattern;

/**
 * Parses request-supplied facet range values of the form {@code range[min:max]}, where {@code max} may be
 * {@code null}. Bounds must be plain decimals of bounded length so that untrusted input (e.g. {@code 1e999999999})
 * cannot trigger huge allocations when the values are later rendered with {@link BigDecimal#toPlainString()}.
 */
public final class SearchFacetRangeParser {

    public static final String RANGE_PREFIX = "range[";
    public static final String OPEN_ENDED = "null";

    private static final Pattern BOUND = Pattern.compile("^-?\\d{1,19}(\\.\\d{1,10})?$");

    private SearchFacetRangeParser() {
    }

    public static boolean isRange(String value) {
        return value != null && value.contains(RANGE_PREFIX);
    }

    /**
     * @return {@code [min, max]} where {@code max} is null for an open-ended range, or null if the value is not a
     * well-formed range with plain, bounded decimal bounds
     */
    public static BigDecimal[] parse(String value) {
        if (!isRange(value)) {
            return null;
        }
        int open = value.indexOf(RANGE_PREFIX) + RANGE_PREFIX.length();
        int close = value.indexOf(']', open);
        if (close < 0) {
            return null;
        }
        String[] bounds = value.substring(open, close).split(":", -1);
        if (bounds.length != 2) {
            return null;
        }
        BigDecimal min = parseBound(bounds[0]);
        if (min == null) {
            return null;
        }
        BigDecimal max = null;
        if (!OPEN_ENDED.equals(bounds[1])) {
            max = parseBound(bounds[1]);
            if (max == null) {
                return null;
            }
        }
        return new BigDecimal[] { min, max };
    }

    public static BigDecimal parseBound(String bound) {
        if (bound == null || !BOUND.matcher(bound).matches()) {
            return null;
        }
        return new BigDecimal(bound);
    }

}
