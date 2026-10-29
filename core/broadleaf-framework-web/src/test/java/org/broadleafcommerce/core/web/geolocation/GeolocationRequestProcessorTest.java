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

import org.broadleafcommerce.common.web.BroadleafRequestContext;
import org.broadleafcommerce.core.geolocation.GeolocationDTO;
import org.broadleafcommerce.core.geolocation.GeolocationService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GeolocationRequestProcessorTest {

    private GeolocationRequestProcessor processor;
    private MockEnvironment env;
    private RecordingGeolocationService geolocationService;

    @Before
    public void setUp() {
        env = new MockEnvironment();
        env.setProperty("geolocation.api.enabled", "true");
        geolocationService = new RecordingGeolocationService();
        processor = new GeolocationRequestProcessor();
        processor.env = env;
        processor.geolocationService = geolocationService;
    }

    @After
    public void tearDown() {
        BroadleafRequestContext.setBroadleafRequestContext(null);
    }

    @Test
    public void forwardedHeaderIgnoredWhenNoProxyIsTrusted() {
        assertEquals("203.0.113.7", resolve("203.0.113.7", "8.8.8.8"));
    }

    @Test
    public void forwardedHeaderIgnoredFromUntrustedRemoteAddress() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8");
        assertEquals("203.0.113.7", resolve("203.0.113.7", "8.8.8.8"));
    }

    @Test
    public void rightMostUntrustedHopUsedBehindTrustedProxy() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8, 192.168.1.10");
        assertEquals("198.51.100.4", resolve("10.1.2.3", "8.8.8.8, 198.51.100.4, 192.168.1.10"));
    }

    @Test
    public void multipleForwardedHeaderLinesAreCombinedInOrder() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8");
        assertEquals("198.51.100.4", resolve("10.1.2.3", "8.8.8.8", "198.51.100.4"));
    }

    @Test
    public void remoteAddressUsedWhenTrustedProxySendsNoHeader() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8");
        assertEquals("10.1.2.3", resolve("10.1.2.3"));
    }

    @Test
    public void chainOfOnlyTrustedHopsYieldsNoAddress() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8, 198.51.100.0/24");
        assertNull(resolve("10.1.2.3", "198.51.100.9"));
        assertNull(resolve("10.1.2.3", "198.51.100.9, 10.4.5.6"));
    }

    @Test
    public void malformedHopFromTrustedProxyYieldsNoAddress() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8");
        assertNull(resolve("10.1.2.3", "8.8.8.8, evil.example.com"));
        assertNull(resolve("10.1.2.3", "8.8.8.8, 999.1.1.1"));
    }

    @Test
    public void ipv6ProxiesAndClientsAreSupported() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "fd00::/8");
        assertEquals("2001:db8:0:0:0:0:0:1", resolve("fd12::1", "[2001:db8::1]"));
        assertEquals("2001:db8:0:0:0:0:0:2", resolve("2001:db8::2", "8.8.8.8"));
    }

    @Test
    public void nonLiteralRemoteAddressYieldsNoAddress() {
        assertNull(resolve("localhost"));
    }

    @Test
    public void invalidTrustedProxyEntriesAreIgnored() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "proxy.internal,10.0.0.0/99");
        assertEquals("10.1.2.3", resolve("10.1.2.3", "8.8.8.8"));
    }

    @Test
    public void processLooksUpSocketAddressNotSpoofedHeader() {
        MockHttpServletRequest request = request("203.0.113.7", "8.8.8.8");
        processor.process(new ServletWebRequest(request, new MockHttpServletResponse()));

        assertEquals(List.of("203.0.113.7"), geolocationService.lookups);
        assertSame(geolocationService.result,
                request.getSession().getAttribute(GeolocationRequestProcessor.GEOLOCATON_ATTRIBUTE_NAME));
        Map<?, ?> ruleMap = (Map<?, ?>) request.getAttribute("blRuleMap");
        assertSame(geolocationService.result, ruleMap.get(GeolocationRequestProcessor.GEOLOCATON_ATTRIBUTE_NAME));
    }

    @Test
    public void processSkipsLookupWhenNoTrustworthyAddress() {
        env.setProperty(GeolocationRequestProcessor.TRUSTED_PROXIES_PROPERTY, "10.0.0.0/8");
        MockHttpServletRequest request = request("10.1.2.3", "8.8.8.8, not-an-ip");
        processor.process(new ServletWebRequest(request, new MockHttpServletResponse()));

        assertTrue(geolocationService.lookups.isEmpty());
        assertNull(request.getSession().getAttribute(GeolocationRequestProcessor.GEOLOCATON_ATTRIBUTE_NAME));
    }

    private String resolve(String remoteAddr, String... forwardedFor) {
        return processor.getIPAddress(new ServletWebRequest(request(remoteAddr, forwardedFor)));
    }

    private MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (String value : forwardedFor) {
            request.addHeader(GeolocationRequestProcessor.FORWARD_HEADER, value);
        }
        return request;
    }

    private static class RecordingGeolocationService implements GeolocationService {

        private final List<String> lookups = new ArrayList<>();
        private final GeolocationDTO result = new GeolocationDTO();

        @Override
        public GeolocationDTO getLocationData(String ipAddress) {
            lookups.add(ipAddress);
            return result;
        }
    }

}
