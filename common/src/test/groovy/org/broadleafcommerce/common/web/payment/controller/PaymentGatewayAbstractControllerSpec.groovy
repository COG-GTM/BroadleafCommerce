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
package org.broadleafcommerce.common.web.payment.controller

import org.broadleafcommerce.common.payment.PaymentGatewayType
import org.broadleafcommerce.common.payment.PaymentType
import org.broadleafcommerce.common.payment.dto.PaymentResponseDTO
import org.broadleafcommerce.common.payment.service.PaymentGatewayCheckoutService
import org.broadleafcommerce.common.payment.service.PaymentGatewayConfiguration
import org.broadleafcommerce.common.payment.service.PaymentGatewayWebResponsePrintService
import org.broadleafcommerce.common.payment.service.PaymentGatewayWebResponseService
import org.broadleafcommerce.common.vendor.service.exception.PaymentException
import org.springframework.ui.Model
import org.springframework.web.servlet.mvc.support.RedirectAttributes

import jakarta.servlet.http.HttpServletRequest
import spock.lang.Specification

class PaymentGatewayAbstractControllerSpec extends Specification {

    PaymentGatewayWebResponseService webResponseService = Mock()
    PaymentGatewayCheckoutService checkoutService = Mock()
    PaymentGatewayConfiguration configuration = Mock()
    HttpServletRequest request = Mock()
    Model model = Mock()
    RedirectAttributes redirectAttributes = Mock()

    TestGatewayController controller = new TestGatewayController()

    def setup() {
        controller.webResponseService = webResponseService
        controller.configuration = configuration
        controller.paymentGatewayCheckoutService = checkoutService
        controller.webResponsePrintService = Mock(PaymentGatewayWebResponsePrintService)
    }

    def "a response the module never marked valid does not touch the order or run checkout"() {
        given:
        webResponseService.translateWebResponse(request) >> response()

        when:
        String view = controller.process(model, request, redirectAttributes)

        then:
        0 * checkoutService.applyPaymentToOrder(_, _)
        0 * checkoutService.initiateCheckout(_)
        0 * checkoutService.markPaymentAsInvalid(_)
        controller.handledException instanceof PaymentException
        view == "redirect:/test-gateway/error"
    }

    def "a response the module marked valid is applied and checked out"() {
        given:
        webResponseService.translateWebResponse(request) >> response().valid(true)

        when:
        String view = controller.process(model, request, redirectAttributes)

        then:
        1 * checkoutService.applyPaymentToOrder(_, configuration) >> 5L
        1 * checkoutService.initiateCheckout(10L) >> "ORDER-10"
        controller.handledException == null
        view == "redirect:/confirmation/ORDER-10"
    }

    def "an order rejected by the checkout service is not checked out"() {
        given:
        webResponseService.translateWebResponse(request) >> response().valid(true)
        checkoutService.applyPaymentToOrder(_, configuration) >> { throw new IllegalArgumentException("not yours") }

        when:
        String view = controller.process(model, request, redirectAttributes)

        then:
        0 * checkoutService.initiateCheckout(_)
        controller.handledException instanceof IllegalArgumentException
        view == "redirect:/test-gateway/error"
    }

    private static PaymentResponseDTO response() {
        return new PaymentResponseDTO(PaymentType.CREDIT_CARD, PaymentGatewayType.PASSTHROUGH).orderId("10")
    }

    static class TestGatewayController extends PaymentGatewayAbstractController {

        PaymentGatewayWebResponseService webResponseService
        PaymentGatewayConfiguration configuration
        Exception handledException

        @Override
        void handleProcessingException(Exception e, RedirectAttributes redirectAttributes) throws PaymentException {
            handledException = e
        }

        @Override
        void handleUnsuccessfulTransaction(Model model, RedirectAttributes redirectAttributes,
                                           PaymentResponseDTO responseDTO) throws PaymentException {
        }

        @Override
        String getGatewayContextKey() {
            return "test-gateway"
        }

        @Override
        PaymentGatewayWebResponseService getWebResponseService() {
            return webResponseService
        }

        @Override
        PaymentGatewayConfiguration getConfiguration() {
            return configuration
        }

        @Override
        String returnEndpoint(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes,
                              Map<String, String> pathVars) throws PaymentException {
            return process(model, request, redirectAttributes)
        }

        @Override
        String errorEndpoint(Model model, HttpServletRequest request, RedirectAttributes redirectAttributes,
                             Map<String, String> pathVars) throws PaymentException {
            return null
        }
    }
}
