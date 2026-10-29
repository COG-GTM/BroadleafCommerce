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
package org.broadleafcommerce.core.spec.payment.service

import org.broadleafcommerce.common.money.Money
import org.broadleafcommerce.common.payment.PaymentGatewayType
import org.broadleafcommerce.common.payment.PaymentTransactionType
import org.broadleafcommerce.common.payment.PaymentType
import org.broadleafcommerce.common.payment.dto.PaymentResponseDTO
import org.broadleafcommerce.common.payment.service.PaymentGatewayCallbackAuthentication
import org.broadleafcommerce.common.payment.service.PaymentGatewayConfiguration
import org.broadleafcommerce.common.web.BroadleafRequestContext
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolver
import org.broadleafcommerce.core.checkout.service.CheckoutService
import org.broadleafcommerce.core.checkout.service.workflow.CheckoutResponse
import org.broadleafcommerce.core.order.domain.Order
import org.broadleafcommerce.core.order.domain.OrderImpl
import org.broadleafcommerce.core.order.service.OrderService
import org.broadleafcommerce.core.order.service.type.OrderStatus
import org.broadleafcommerce.core.payment.domain.OrderPaymentImpl
import org.broadleafcommerce.core.payment.domain.PaymentTransactionImpl
import org.broadleafcommerce.core.payment.service.DefaultPaymentGatewayCheckoutService
import org.broadleafcommerce.core.payment.service.OrderPaymentService
import org.broadleafcommerce.core.payment.service.PaymentResponseDTOToEntityService
import org.broadleafcommerce.profile.core.domain.Customer
import org.broadleafcommerce.profile.core.domain.CustomerImpl
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.WebRequest

import spock.lang.Specification

class DefaultPaymentGatewayCheckoutServiceSpec extends Specification {

    OrderService orderService = Mock()
    OrderPaymentService orderPaymentService = Mock()
    CheckoutService checkoutService = Mock()
    PaymentResponseDTOToEntityService dtoToEntityService = Mock()
    BroadleafRequestCustomerResolver requestCustomerResolver = Mock()
    PaymentGatewayConfiguration config = Mock()
    WebRequest webRequest = Mock()

    DefaultPaymentGatewayCheckoutService service = new DefaultPaymentGatewayCheckoutService()

    Customer victim = customer(1L)
    Customer attacker = customer(2L)
    Order victimOrder = new OrderImpl()

    def setup() {
        service.orderService = orderService
        service.orderPaymentService = orderPaymentService
        service.checkoutService = checkoutService
        service.dtoToEntityService = dtoToEntityService
        service.requestCustomerResolver = requestCustomerResolver

        victimOrder.id = 10L
        victimOrder.customer = victim
        victimOrder.status = OrderStatus.IN_PROCESS

        config.handlesMultiplePayments() >> true
        config.getGatewayType() >> PaymentGatewayType.PASSTHROUGH
    }

    def cleanup() {
        BroadleafRequestContext.setBroadleafRequestContext(null)
    }

    def "a response whose validity the gateway module never confirmed is not applied"() {
        given:
        PaymentResponseDTO responseDTO = response().orderId("10")

        when:
        service.applyPaymentToOrder(responseDTO, config)

        then:
        thrown(IllegalArgumentException)
        0 * orderService._
        0 * dtoToEntityService._
        0 * orderPaymentService._
    }

    def "a valid response naming another customer's order does not change that order"() {
        given:
        requestFrom(attacker)
        orderService.findOrderById(10L) >> victimOrder
        PaymentResponseDTO responseDTO = response().valid(true).orderId("10")
        responseDTO.shipTo().addressLine1("1 Attacker Way").done()

        when:
        service.applyPaymentToOrder(responseDTO, config)

        then:
        thrown(IllegalArgumentException)
        0 * dtoToEntityService._
        0 * orderPaymentService._
        0 * orderService.addPaymentToOrder(_, _, _)
    }

    def "a valid response for the current customer's order is applied"() {
        given:
        requestFrom(victim)
        orderService.findOrderById(10L) >> victimOrder
        orderPaymentService.create() >> new OrderPaymentImpl()
        orderPaymentService.createTransaction() >> new PaymentTransactionImpl()
        orderPaymentService.save(_) >> { args -> args[0] }
        PaymentResponseDTO responseDTO = response().valid(true).orderId("10")

        when:
        service.applyPaymentToOrder(responseDTO, config)

        then:
        1 * dtoToEntityService.populateShippingInfo(responseDTO, victimOrder)
        1 * orderService.addPaymentToOrder(victimOrder, _, null)
    }

    def "a valid response with no customer on the request (server-to-server) is applied"() {
        given:
        orderService.findOrderById(10L) >> victimOrder
        orderPaymentService.create() >> new OrderPaymentImpl()
        orderPaymentService.createTransaction() >> new PaymentTransactionImpl()
        orderPaymentService.save(_) >> { args -> args[0] }

        when:
        service.applyPaymentToOrder(response().valid(true).orderId("10"), config)

        then:
        1 * orderService.addPaymentToOrder(victimOrder, _, null)
    }

    def "a callback bound to a fresh anonymous customer is rejected unless the gateway module authenticated it"() {
        given:
        requestFrom(customer(99L))
        orderService.findOrderById(10L) >> victimOrder

        when:
        service.applyPaymentToOrder(response().valid(true).orderId("10"), config)

        then:
        thrown(IllegalArgumentException)
        0 * dtoToEntityService._
        0 * orderPaymentService._
    }

    def "an authenticated server-to-server callback is applied and checked out despite its anonymous request customer"() {
        given:
        requestFrom(customer(99L))
        markAuthenticatedGatewayCallback()
        orderService.findOrderById(10L) >> victimOrder
        orderService.findOrderById(10L, true) >> victimOrder
        orderPaymentService.create() >> new OrderPaymentImpl()
        orderPaymentService.createTransaction() >> new PaymentTransactionImpl()
        orderPaymentService.save(_) >> { args -> args[0] }
        CheckoutResponse checkoutResponse = Mock()
        checkoutResponse.getOrder() >> victimOrder
        victimOrder.orderNumber = "ORDER-10"

        when:
        service.applyPaymentToOrder(response().valid(true).orderId("10"), config)
        String orderNumber = service.initiateCheckout(10L)

        then:
        1 * orderService.addPaymentToOrder(victimOrder, _, null)
        1 * checkoutService.performCheckout(victimOrder) >> checkoutResponse
        orderNumber == "ORDER-10"
    }

    def "checkout is not run for another customer's order"() {
        given:
        requestFrom(attacker)
        orderService.findOrderById(10L, true) >> victimOrder

        when:
        service.initiateCheckout(10L)

        then:
        thrown(IllegalArgumentException)
        0 * checkoutService._
    }

    def "checkout runs for the current customer's order"() {
        given:
        requestFrom(victim)
        orderService.findOrderById(10L, true) >> victimOrder
        CheckoutResponse checkoutResponse = Mock()
        checkoutResponse.getOrder() >> victimOrder
        victimOrder.orderNumber = "ORDER-10"

        when:
        String orderNumber = service.initiateCheckout(10L)

        then:
        1 * checkoutService.performCheckout(victimOrder) >> checkoutResponse
        orderNumber == "ORDER-10"
    }

    def "the order number of another customer's order is not disclosed"() {
        given:
        requestFrom(attacker)
        orderService.findOrderById(10L, true) >> victimOrder
        victimOrder.orderNumber = "ORDER-10"

        when:
        service.lookupOrderNumberFromOrderId(response().valid(true).orderId("10"))

        then:
        thrown(IllegalArgumentException)
    }

    private void markAuthenticatedGatewayCallback() {
        webRequest.getAttribute(PaymentGatewayCallbackAuthentication.AUTHENTICATED_GATEWAY_CALLBACK_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST) >> true
    }

    private void requestFrom(Customer customer) {
        BroadleafRequestContext context = new BroadleafRequestContext()
        context.webRequest = webRequest
        BroadleafRequestContext.setBroadleafRequestContext(context)
        requestCustomerResolver.getCustomer(webRequest) >> customer
    }

    private static PaymentResponseDTO response() {
        return new PaymentResponseDTO(PaymentType.CREDIT_CARD, PaymentGatewayType.PASSTHROUGH)
                .paymentTransactionType(PaymentTransactionType.AUTHORIZE_AND_CAPTURE)
                .amount(new Money(10))
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl()
        customer.id = id
        return customer
    }
}
