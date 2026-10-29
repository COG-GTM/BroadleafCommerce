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
 * Decides which Solr request handler ({@code qt}) a storefront search may use.
 * <p>
 * SolrJ sends a request whose handler starts with {@code /} to that raw, unencoded URL path, so a caller-supplied
 * value could reach any handler on the core ({@code /update}, {@code /replication}, ...) and append URL parameters.
 * A handler is only honored when it is a plain name ({@code /[A-Za-z0-9_-]+}) listed in the comma-separated
 * {@value #ALLOWED_REQUEST_HANDLERS_PROPERTY} property, which is empty by default. Anything else resolves to
 * {@code null}, so SolrJ falls back to its default {@code /select} handler.
 */
public final class SolrRequestHandlerPolicy {

    public static final String ALLOWED_REQUEST_HANDLERS_PROPERTY = "solr.search.allowedRequestHandlers";

    private static final Pattern HANDLER_NAME = Pattern.compile("/[A-Za-z0-9_\\-]+");

    private SolrRequestHandlerPolicy() {
    }

    /**
     * @param allowedHandlers comma-separated handler names, with or without the leading {@code /}
     * @return the well-formed allowlisted handlers, each with a leading {@code /}
     */
    public static Set<String> parseAllowedHandlers(String allowedHandlers) {
        if (StringUtils.isBlank(allowedHandlers)) {
            return Collections.emptySet();
        }
        Set<String> handlers = new LinkedHashSet<>();
        for (String handler : StringUtils.split(allowedHandlers, ',')) {
            String normalized = normalize(StringUtils.trim(handler));
            if (normalized != null) {
                handlers.add(normalized);
            }
        }
        return Collections.unmodifiableSet(handlers);
    }

    /**
     * @return the normalized handler when it is well formed and allowlisted, otherwise {@code null}
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
        if (StringUtils.isEmpty(handler)) {
            return null;
        }
        String candidate = handler.startsWith("/") ? handler : "/" + handler;
        return HANDLER_NAME.matcher(candidate).matches() ? candidate : null;
    }

}
