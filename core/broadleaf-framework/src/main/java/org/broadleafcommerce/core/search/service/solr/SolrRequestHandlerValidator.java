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

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Restricts which Solr request handler a search may target.
 * <p>
 * SolrJ treats a request handler ({@code qt}) that starts with {@code /} as the raw HTTP path of the request,
 * so an unchecked value can select any Solr handler and append arbitrary URL parameters. Only simple handler
 * names that are explicitly allowlisted through the {@code solr.search.allowedRequestHandlers} property
 * (comma-separated, empty by default) are accepted; anything else resolves to {@code null}, which makes
 * SolrJ use its default {@code /select} handler.
 */
public final class SolrRequestHandlerValidator {

    public static final String ALLOWED_REQUEST_HANDLERS_PROPERTY = "solr.search.allowedRequestHandlers";

    private static final Pattern HANDLER_NAME = Pattern.compile("/[A-Za-z0-9_\\-]+");

    private SolrRequestHandlerValidator() {
    }

    /**
     * @param allowedHandlers comma-separated handler names, with or without a leading {@code /}
     * @return the normalized (leading {@code /}) allowlisted handlers; malformed entries are dropped
     */
    public static Set<String> parseAllowedHandlers(String allowedHandlers) {
        if (StringUtils.isBlank(allowedHandlers)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String handler : StringUtils.split(allowedHandlers, ',')) {
            String normalized = normalize(handler.trim());
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * @return the normalized handler if it is a well-formed name contained in {@code allowedHandlers}, otherwise null
     */
    public static String resolve(String requestedHandler, Set<String> allowedHandlers) {
        String normalized = normalize(requestedHandler);
        if (normalized == null || allowedHandlers == null || !allowedHandlers.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    public static String resolve(String requestedHandler, String allowedHandlers) {
        return resolve(requestedHandler, parseAllowedHandlers(allowedHandlers));
    }

    private static String normalize(String handler) {
        if (handler == null) {
            return null;
        }
        String candidate = handler;
        if (!candidate.startsWith("/")) {
            candidate = "/" + candidate;
        }
        return HANDLER_NAME.matcher(candidate).matches() ? candidate : null;
    }

}
