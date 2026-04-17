/*-
 * #%L
 * BroadleafCommerce Integration
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
package org.broadleafcommerce.core.catalog.dao;

import org.broadleafcommerce.core.catalog.ProductDataProvider;
import org.broadleafcommerce.core.catalog.domain.CrossSaleProductImpl;
import org.broadleafcommerce.core.catalog.domain.Product;
import org.broadleafcommerce.core.catalog.domain.RelatedProduct;
import org.broadleafcommerce.core.catalog.domain.UpSaleProductImpl;
import org.broadleafcommerce.core.catalog.service.CatalogService;
import org.broadleafcommerce.test.TestNGSiteIntegrationSetup;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.Resource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductDaoTest extends TestNGSiteIntegrationSetup {

    @Resource
    private ProductDao productDao;
    
    @Resource
    private CatalogService catalogService;

    private List<Product> savedProducts = new ArrayList<>();

    private static RelatedProduct getRelatedUpSaleProduct(Product prod, Product prodToRelate, List<RelatedProduct> upSales){
        RelatedProduct upSaleProduct = new UpSaleProductImpl();
        upSaleProduct.setProduct(prod);
        upSaleProduct.setPromotionMessage("brand new coffee");
        upSaleProduct.setRelatedProduct(prodToRelate);

        upSales.add(upSaleProduct);
        return upSaleProduct;
    }

    private static RelatedProduct getRelatedCrossProduct(Product prod, Product prodToRelate, List<RelatedProduct> crossSales){
        RelatedProduct crossSaleProduct = new CrossSaleProductImpl();
        crossSaleProduct.setProduct(prod);
        crossSaleProduct.setPromotionMessage("brand new coffee");
        crossSaleProduct.setRelatedProduct(prodToRelate);

        crossSales.add(crossSaleProduct);
        return crossSaleProduct;
    }

    @Order(1)
    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.catalog.ProductDataProvider#createProducts")
    @Rollback(false)
    @Transactional
    public void createProducts(Product product) {
        product = catalogService.saveProduct(product);
        assert(product.getId() != null);
        savedProducts.add(product);
    }

    @Order(2)
    @Test
    @Rollback(false)
    @Transactional
    public void createUpSaleValues(){
        Product prod1 = savedProducts.get(0);
        List<RelatedProduct> upSales = new ArrayList<>();
        getRelatedUpSaleProduct(prod1, savedProducts.get(2), upSales);
        getRelatedUpSaleProduct(prod1, savedProducts.get(3), upSales);
        getRelatedUpSaleProduct(prod1, savedProducts.get(4), upSales);
        prod1.setUpSaleProducts(upSales);
        prod1 = catalogService.saveProduct(prod1);
        assert(prod1.getId() != null);

        Product prod2 = savedProducts.get(1);
        List<RelatedProduct> upSales2 = new ArrayList<>();
        getRelatedUpSaleProduct(prod2, savedProducts.get(5), upSales2);
        getRelatedUpSaleProduct(prod2, savedProducts.get(6), upSales2);
        prod2.setUpSaleProducts(upSales2);
        prod2 = catalogService.saveProduct(prod2);
        assert(prod2.getId() != null);
    }

    @Order(3)
    @Test
    @Transactional
    public void testReadProductsWithUpSaleValues() {
        Product result = productDao.readProductById(savedProducts.get(0).getId());
        List<RelatedProduct> related = result.getUpSaleProducts();

        assert(related != null);
        assert(!related.isEmpty());
        assert(related.size() == 2 || related.size() == 3);

        for(RelatedProduct rp : related){
            assert(rp instanceof UpSaleProductImpl);
        }
    }

    @Order(4)
    @Test
    @Rollback(false)
    @Transactional
    public void createCrossSaleValues(){
        Product prod1 = savedProducts.get(0);
        List<RelatedProduct> crossSale = new ArrayList<>();
        getRelatedCrossProduct(prod1, savedProducts.get(2), crossSale);
        getRelatedCrossProduct(prod1, savedProducts.get(3), crossSale);
        getRelatedCrossProduct(prod1, savedProducts.get(4), crossSale);
        prod1.setCrossSaleProducts(crossSale);
        prod1 = catalogService.saveProduct(prod1);
        assert(prod1.getId() != null);

        Product prod2 = savedProducts.get(1);
        List<RelatedProduct> crossSale2 = new ArrayList<>();
        getRelatedCrossProduct(prod2, savedProducts.get(5), crossSale2);
        getRelatedCrossProduct(prod2, savedProducts.get(6), crossSale2);
        prod2.setCrossSaleProducts(crossSale2);
        prod2 = catalogService.saveProduct(prod2);
        assert(prod2.getId() != null);
    }

    @Order(5)
    @Test
    @Transactional
    public void testReadProductsWithCrossSaleValues() {
        Product result = productDao.readProductById(savedProducts.get(1).getId());
        List<RelatedProduct> related = result.getCrossSaleProducts();

        assert(related != null);
        assert(!related.isEmpty());
        assert(related.size() == 2 || related.size() == 3);

        for(RelatedProduct rp : related){
            assert(rp instanceof CrossSaleProductImpl);
        }
    }

    @Order(6)
    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.catalog.ProductDataProvider#provideBasicProduct")
    @Transactional
    public void testReadProductsById(Product product) {
        product = catalogService.saveProduct(product);
        Product result = productDao.readProductById(product.getId());
        assert product.equals(result);
    }

    @Order(7)
    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.catalog.ProductDataProvider#provideBasicProduct")
    @Transactional
    public void testReadProductsByName(Product product) {
        String name = product.getName();
        product = catalogService.saveProduct(product);
        List<Product> result = productDao.readProductsByName(name);
        assert result.contains(product);
    }

    @Order(8)
    @ParameterizedTest
    @MethodSource("org.broadleafcommerce.core.catalog.ProductDataProvider#provideBasicProduct")
    @Transactional
    public void testReadArchivedProductsByName(Product product) {
        String name = product.getName();
        product = catalogService.saveProduct(product);

        catalogService.removeProduct(product);

        List<Product> result = productDao.readProductsByName(name);
        assert result.isEmpty();
    }

}
