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
package org.broadleafcommerce.core.web.graphql.resolvers;

import org.apache.commons.collections4.CollectionUtils;
import org.broadleafcommerce.core.offer.domain.OfferCode;
import org.broadleafcommerce.core.offer.service.OfferService;
import org.broadleafcommerce.core.offer.service.exception.OfferAlreadyAddedException;
import org.broadleafcommerce.core.offer.service.exception.OfferException;
import org.broadleafcommerce.core.offer.service.exception.OfferExpiredException;
import org.broadleafcommerce.core.offer.service.exception.OfferMaxUseExceededException;
import org.broadleafcommerce.core.order.domain.NullOrderImpl;
import org.broadleafcommerce.core.order.domain.Order;
import org.broadleafcommerce.core.order.service.OrderService;
import org.broadleafcommerce.core.order.service.call.OrderItemRequestDTO;
import org.broadleafcommerce.core.order.service.exception.AddToCartException;
import org.broadleafcommerce.core.order.service.exception.RemoveFromCartException;
import org.broadleafcommerce.core.order.service.exception.UpdateCartException;
import org.broadleafcommerce.core.pricing.service.exception.PricingException;
import org.broadleafcommerce.core.web.graphql.dto.AddToCartInput;
import org.broadleafcommerce.core.web.graphql.dto.PromoCodeResult;
import org.broadleafcommerce.core.web.order.CartState;
import org.broadleafcommerce.core.web.service.UpdateCartService;
import org.broadleafcommerce.profile.web.core.CustomerState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class CartMutationResolver {

    @Autowired
    @Qualifier("blOrderService")
    protected OrderService orderService;

    @Autowired
    @Qualifier("blOfferService")
    protected OfferService offerService;

    @Autowired
    @Qualifier("blUpdateCartService")
    protected UpdateCartService updateCartService;

    @MutationMapping
    public Order addToCart(@Argument AddToCartInput input) throws AddToCartException, PricingException {
        Order cart = CartState.getCart();
        if (cart == null || cart instanceof NullOrderImpl) {
            cart = orderService.createNewCartForCustomer(CustomerState.getCustomer());
            CartState.setCart(cart);
        }

        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO();
        itemRequest.setProductId(Long.parseLong(input.getProductId()));
        if (input.getSkuId() != null) {
            itemRequest.setSkuId(Long.parseLong(input.getSkuId()));
        }
        itemRequest.setQuantity(input.getQuantity());

        updateCartService.validateAddToCartRequest(itemRequest, cart);

        cart = orderService.addItem(cart.getId(), itemRequest, false);
        cart = orderService.save(cart, true);
        CartState.setCart(cart);
        return cart;
    }

    @MutationMapping
    public Order updateCartItemQuantity(@Argument Long orderItemId, @Argument int quantity)
            throws UpdateCartException, PricingException, RemoveFromCartException {
        Order cart = requireActiveCart();
        OrderItemRequestDTO itemRequest = new OrderItemRequestDTO();
        itemRequest.setOrderItemId(orderItemId);
        itemRequest.setQuantity(quantity);
        cart = orderService.updateItemQuantity(cart.getId(), itemRequest, true);
        cart = orderService.save(cart, false);
        CartState.setCart(cart);
        return cart;
    }

    @MutationMapping
    public Order removeFromCart(@Argument Long orderItemId)
            throws PricingException, RemoveFromCartException {
        Order cart = requireActiveCart();
        cart = orderService.removeItem(cart.getId(), orderItemId, false);
        cart = orderService.save(cart, true);
        CartState.setCart(cart);
        return cart;
    }

    protected Order requireActiveCart() {
        Order cart = CartState.getCart();
        if (cart == null || cart instanceof NullOrderImpl) {
            throw new IllegalStateException("No active cart for the current customer");
        }
        return cart;
    }

    @MutationMapping
    public PromoCodeResult applyPromoCode(@Argument String code) throws PricingException {
        Order cart = CartState.getCart();
        boolean promoAdded = false;
        String errorMessage = null;

        if (cart != null && !(cart instanceof NullOrderImpl)) {
            List<OfferCode> offerCodes = offerService.lookupAllOfferCodesByCode(code);
            if (CollectionUtils.isNotEmpty(offerCodes)) {
                for (OfferCode offerCode : offerCodes) {
                    try {
                        cart = orderService.addOfferCode(cart, offerCode, false);
                        promoAdded = true;
                    } catch (OfferMaxUseExceededException e) {
                        errorMessage = "Use Limit Exceeded";
                    } catch (OfferExpiredException e) {
                        errorMessage = "Offer Has Expired";
                    } catch (OfferAlreadyAddedException e) {
                        errorMessage = "Offer Has Already Been Added";
                    } catch (OfferException e) {
                        errorMessage = "An Unknown Offer Error Has Occurred";
                    }
                }
                if (errorMessage == null) {
                    cart = orderService.save(cart, true);
                    CartState.setCart(cart);
                }
            } else {
                errorMessage = "Unknown Code";
            }
        } else {
            errorMessage = "Invalid Cart";
        }

        return new PromoCodeResult(cart, promoAdded, errorMessage);
    }

    @MutationMapping
    public Order removePromoCode(@Argument Long offerCodeId) throws PricingException {
        Order cart = requireActiveCart();
        OfferCode offerCode = offerService.findOfferCodeById(offerCodeId);
        cart = orderService.removeOfferCode(cart, offerCode, false);
        cart = orderService.save(cart, true);
        CartState.setCart(cart);
        return cart;
    }

}
