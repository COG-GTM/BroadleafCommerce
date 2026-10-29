/*-
 * #%L
 * BroadleafCommerce Framework Web
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
package org.broadleafcommerce.core.web.geolocation;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.util.BLCRequestUtils;
import org.broadleafcommerce.common.web.AbstractBroadleafWebRequestProcessor;
import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.geolocation.GeolocationDTO;
import org.broadleafcommerce.core.geolocation.GeolocationService;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.annotation.Resource;

/**
 * Resolves the visitor's location and exposes it to rule evaluation.
 * <p>
 * The client IP is the socket address ({@code getRemoteAddr()}). The {@value #FORWARD_HEADER} header is only consulted
 * when the socket address belongs to a proxy listed in {@value #TRUSTED_PROXIES_PROPERTY}; in that case the right-most
 * hop that is not itself a trusted proxy is used. Values that are not IP literals are never sent to the geolocation API.
 */
@Component("blGeolocationRequestProcessor")
public class GeolocationRequestProcessor extends AbstractBroadleafWebRequestProcessor {

    private static final Log LOG = LogFactory.getLog(GeolocationRequestProcessor.class);

    public static final String FORWARD_HEADER = "X-FORWARDED-FOR";
    public static final String TRUSTED_PROXIES_PROPERTY = "geolocation.trustedProxies";
    public static final String GEOLOCATON_ATTRIBUTE_NAME = "_blGeolocationAttribute";
    protected static final String BLC_RULE_MAP_PARAM = "blRuleMap";

    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
    );
    private static final Pattern IPV6_LITERAL_CHARS = Pattern.compile("^[0-9A-Fa-f:.]+$");

    @Resource(name = "blGeolocationService")
    protected GeolocationService geolocationService;

    @Resource
    protected Environment env;

    @Override
    public void process(WebRequest request) {
        if (isGeolocationEnabled()) {
            if (request instanceof ServletWebRequest) {
                ServletWebRequest servletWebRequest = (ServletWebRequest) request;
                GeolocationDTO location = (GeolocationDTO) BLCRequestUtils.getSessionAttributeIfOk(
                        request, GEOLOCATON_ATTRIBUTE_NAME
                );
                if (location == null) {
                    String ipAddress = getIPAddress(servletWebRequest);
                    if (ipAddress != null) {
                        location = geolocationService.getLocationData(ipAddress);
                        BLCRequestUtils.setSessionAttributeIfOk(request, GEOLOCATON_ATTRIBUTE_NAME, location);
                    }
                }
                BroadleafRequestContext.getBroadleafRequestContext().getAdditionalProperties().put(
                        GEOLOCATON_ATTRIBUTE_NAME, location
                );

                Map<String, Object> ruleMap = getRuleMapFromRequest(request);
                ruleMap.put(GEOLOCATON_ATTRIBUTE_NAME, location);
                request.setAttribute(BLC_RULE_MAP_PARAM, ruleMap, WebRequest.SCOPE_REQUEST);
            }
        }
    }

    protected boolean isGeolocationEnabled() {
        return env.getProperty("geolocation.api.enabled", Boolean.class, false);
    }

    /**
     * @return the normalized client IP address, or null if no trustworthy IP literal could be determined
     */
    protected String getIPAddress(ServletWebRequest request) {
        InetAddress remoteAddress = parseIpLiteral(request.getRequest().getRemoteAddr());
        if (remoteAddress == null) {
            return null;
        }
        List<IpRange> trustedProxies = getTrustedProxies();
        if (!isTrustedProxy(remoteAddress, trustedProxies)) {
            return remoteAddress.getHostAddress();
        }

        List<String> hops = getForwardedHops(request);
        InetAddress clientAddress = remoteAddress;
        for (int i = hops.size() - 1; i >= 0; i--) {
            InetAddress hop = parseIpLiteral(hops.get(i));
            if (hop == null) {
                LOG.debug("Ignoring geolocation lookup: malformed " + FORWARD_HEADER + " hop from a trusted proxy");
                return null;
            }
            clientAddress = hop;
            if (!isTrustedProxy(hop, trustedProxies)) {
                break;
            }
        }
        return clientAddress.getHostAddress();
    }

    protected List<String> getForwardedHops(ServletWebRequest request) {
        List<String> hops = new ArrayList<>();
        Enumeration<String> headers = request.getRequest().getHeaders(FORWARD_HEADER);
        if (headers == null) {
            return hops;
        }
        for (String header : Collections.list(headers)) {
            for (String hop : StringUtils.split(header, ',')) {
                if (StringUtils.isNotBlank(hop)) {
                    hops.add(hop.trim());
                }
            }
        }
        return hops;
    }

    protected List<IpRange> getTrustedProxies() {
        List<IpRange> ranges = new ArrayList<>();
        String configured = env.getProperty(TRUSTED_PROXIES_PROPERTY, "");
        for (String entry : StringUtils.split(configured, ',')) {
            if (StringUtils.isBlank(entry)) {
                continue;
            }
            IpRange range = IpRange.parse(entry.trim());
            if (range == null) {
                LOG.warn("Ignoring invalid entry in " + TRUSTED_PROXIES_PROPERTY + ": '" + entry.trim() + "'");
            } else {
                ranges.add(range);
            }
        }
        return ranges;
    }

    protected boolean isTrustedProxy(InetAddress address, List<IpRange> trustedProxies) {
        for (IpRange range : trustedProxies) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses an IPv4 or IPv6 literal without ever performing a DNS lookup.
     */
    protected static InetAddress parseIpLiteral(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.length() > 2 && candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        boolean ipv4 = IPV4_LITERAL.matcher(candidate).matches();
        boolean ipv6 = candidate.indexOf(':') >= 0 && IPV6_LITERAL_CHARS.matcher(candidate).matches();
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    protected static class IpRange {

        protected final byte[] network;
        protected final int prefixLength;

        protected IpRange(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        /**
         * @param value an IP literal or CIDR block, e.g. {@code 10.0.0.1}, {@code 10.0.0.0/8} or {@code fd00::/8}
         */
        protected static IpRange parse(String value) {
            String address = value;
            String prefix = null;
            int slash = value.indexOf('/');
            if (slash >= 0) {
                address = value.substring(0, slash);
                prefix = value.substring(slash + 1);
            }
            InetAddress parsed = parseIpLiteral(address);
            if (parsed == null) {
                return null;
            }
            byte[] bytes = parsed.getAddress();
            int maxPrefix = bytes.length * 8;
            int prefixLength = maxPrefix;
            if (prefix != null) {
                if (!prefix.matches("\\d{1,3}")) {
                    return null;
                }
                prefixLength = Integer.parseInt(prefix);
                if (prefixLength > maxPrefix) {
                    return null;
                }
            }
            return new IpRange(bytes, prefixLength);
        }

        protected boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    protected Map<String, Object> getRuleMapFromRequest(WebRequest request) {
        Map<String, Object> ruleMap = (Map<String, Object>) request.getAttribute(
                BLC_RULE_MAP_PARAM, WebRequest.SCOPE_REQUEST
        );
        if (ruleMap == null) {
            ruleMap = new HashMap<>();
        }
        return ruleMap;
    }

}
