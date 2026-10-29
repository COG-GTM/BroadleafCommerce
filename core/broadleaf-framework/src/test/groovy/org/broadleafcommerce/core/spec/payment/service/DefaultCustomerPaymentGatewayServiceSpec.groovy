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

import org.broadleafcommerce.common.payment.PaymentGatewayType
import org.broadleafcommerce.common.payment.PaymentType
import org.broadleafcommerce.common.payment.dto.PaymentResponseDTO
import org.broadleafcommerce.common.payment.service.PaymentGatewayConfiguration
import org.broadleafcommerce.common.web.BroadleafRequestContext
import org.broadleafcommerce.common.web.BroadleafRequestCustomerResolver
import org.broadleafcommerce.core.payment.service.DefaultCustomerPaymentGatewayService
import org.broadleafcommerce.core.payment.service.PaymentResponseDTOToEntityService
import org.broadleafcommerce.profile.core.domain.Customer
import org.broadleafcommerce.profile.core.domain.CustomerImpl
import org.broadleafcommerce.profile.core.domain.CustomerPayment
import org.broadleafcommerce.profile.core.domain.CustomerPaymentImpl
import org.broadleafcommerce.profile.core.service.AddressService
import org.broadleafcommerce.profile.core.service.CustomerPaymentService
import org.broadleafcommerce.profile.core.service.CustomerService
import org.springframework.web.context.request.WebRequest

import spock.lang.Specification

class DefaultCustomerPaymentGatewayServiceSpec extends Specification {

    AddressService addressService = Mock()
    CustomerPaymentService customerPaymentService = Mock()
    CustomerService customerService = Mock()
    PaymentResponseDTOToEntityService dtoToEntityService = Mock()
    BroadleafRequestCustomerResolver requestCustomerResolver = Mock()
    PaymentGatewayConfiguration config = Mock()
    WebRequest webRequest = Mock()

    DefaultCustomerPaymentGatewayService service = new DefaultCustomerPaymentGatewayService()

    Customer victim = customer(1L)
    Customer attacker = customer(2L)

    def setup() {
        service.addressService = addressService
        service.customerPaymentService = customerPaymentService
        service.customerService = customerService
        service.dtoToEntityService = dtoToEntityService
        service.requestCustomerResolver = requestCustomerResolver
        config.getGatewayType() >> PaymentGatewayType.PASSTHROUGH
    }

    def cleanup() {
        BroadleafRequestContext.setBroadleafRequestContext(null)
    }

    def "a token response whose validity was never confirmed is not saved"() {
        when:
        service.createCustomerPaymentFromResponseDTO(response("1"), config)

        then:
        thrown(IllegalArgumentException)
        0 * customerService._
        0 * customerPaymentService._
    }

    def "a saved payment token is not attached to a different customer"() {
        given:
        requestFrom(attacker)

        when:
        service.createCustomerPaymentFromResponseDTO(response("1").valid(true), config)

        then:
        thrown(IllegalArgumentException)
        0 * customerService._
        0 * customerPaymentService._
    }

    def "a saved payment token is attached to the current customer"() {
        given:
        requestFrom(victim)
        customerService.readCustomerById(1L) >> victim
        customerPaymentService.create() >> new CustomerPaymentImpl()

        when:
        service.createCustomerPaymentFromResponseDTO(response("1").valid(true), config)

        then:
        1 * customerPaymentService.saveCustomerPayment({ it.customer == victim }) >> { args -> args[0] }
    }

    def "another customer's saved payment is neither updated nor deleted"() {
        given:
        requestFrom(attacker)
        CustomerPayment victimPayment = new CustomerPaymentImpl()
        victimPayment.customer = victim
        customerPaymentService.readCustomerPaymentByToken("tok") >> victimPayment

        when:
        service.updateCustomerPaymentFromResponseDTO(response("2").valid(true).paymentToken("tok"), config)

        then:
        thrown(IllegalArgumentException)
        0 * customerPaymentService.saveCustomerPayment(_)

        when:
        service.deleteCustomerPaymentFromResponseDTO(response("2").valid(true).paymentToken("tok"), config)

        then:
        thrown(IllegalArgumentException)
        0 * customerPaymentService.deleteCustomerPaymentByToken(_)
    }

    private void requestFrom(Customer customer) {
        BroadleafRequestContext context = new BroadleafRequestContext()
        context.webRequest = webRequest
        BroadleafRequestContext.setBroadleafRequestContext(context)
        requestCustomerResolver.getCustomer(webRequest) >> customer
    }

    private static PaymentResponseDTO response(String customerId) {
        PaymentResponseDTO responseDTO = new PaymentResponseDTO(PaymentType.CREDIT_CARD, PaymentGatewayType.PASSTHROUGH)
        responseDTO.customer().customerId(customerId).done()
        return responseDTO
    }

    private static Customer customer(Long id) {
        Customer customer = new CustomerImpl()
        customer.id = id
        return customer
    }
}
