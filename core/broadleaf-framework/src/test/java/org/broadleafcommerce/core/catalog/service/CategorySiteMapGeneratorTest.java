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
package org.broadleafcommerce.core.catalog.service;

import org.broadleafcommerce.common.media.domain.Media;
import org.broadleafcommerce.common.media.domain.MediaImpl;
import org.broadleafcommerce.common.sitemap.domain.SiteMapGeneratorConfiguration;
import org.broadleafcommerce.common.sitemap.exception.SiteMapException;
import org.broadleafcommerce.common.sitemap.service.SiteMapGeneratorTest;
import org.broadleafcommerce.common.sitemap.service.type.SiteMapChangeFreqType;
import org.broadleafcommerce.common.sitemap.service.type.SiteMapGeneratorType;
import org.broadleafcommerce.common.sitemap.service.type.SiteMapPriorityType;
import org.broadleafcommerce.core.catalog.dao.CategoryDao;
import org.broadleafcommerce.core.catalog.domain.Category;
import org.broadleafcommerce.core.catalog.domain.CategoryImpl;
import org.broadleafcommerce.core.catalog.domain.CategoryMediaXref;
import org.broadleafcommerce.core.catalog.domain.CategoryMediaXrefImpl;
import org.broadleafcommerce.core.catalog.domain.CategorySiteMapGeneratorConfiguration;
import org.broadleafcommerce.core.catalog.domain.CategorySiteMapGeneratorConfigurationImpl;
import org.mockito.Mockito;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Category site map generator tests
 * 
 * @author Joshua Skorton (jskorton)
 */
public class CategorySiteMapGeneratorTest extends SiteMapGeneratorTest {

    @Test
    public void testCategorySiteMapGenerator() throws SiteMapException, IOException {

        Category c1 = new CategoryImpl();
        c1.setUrl("/");
        c1.setId(1l);

        Category c2 = new CategoryImpl();
        c2.setUrl("/hot-sauces");
        c2.setId(2l);
        CategoryMediaXref cmXref1 = new CategoryMediaXrefImpl();
        Media m1 = new MediaImpl();
        m1.setUrl("/img/hot-sauce-img1.png");
        cmXref1.setCategory(c2);
        cmXref1.setMedia(m1);
        c2.getCategoryMediaXref().put("image1", cmXref1);
        CategoryMediaXref cmXref2 = new CategoryMediaXrefImpl();
        Media m2 = new MediaImpl();
        m2.setUrl("/img/hot-sauce-img2.png");
        cmXref2.setCategory(c2);
        cmXref2.setMedia(m2);
        c2.getCategoryMediaXref().put("image2", cmXref2);

        Category c3 = new CategoryImpl();
        c3.setUrl("merchandise");
        c3.setId(3l);
        Category c4 = new CategoryImpl();
        c4.setUrl("/clearance");
        c4.setId(4l);
        Category c5 = new CategoryImpl();
        c5.setUrl("/mens");
        c5.setId(5l);
        Category c6 = new CategoryImpl();
        c6.setUrl("/womens");
        c6.setId(6l);

        List<Category> merchandiseSubcategories = new ArrayList<Category>();
        merchandiseSubcategories.add(c5);
        merchandiseSubcategories.add(c6);

        CategoryDao categoryDao = Mockito.mock(CategoryDao.class);
        Mockito.when(categoryDao.readActiveSubCategoriesByCategory(c1, 5, 0)).thenReturn(new ArrayList<Category>());
        Mockito.when(categoryDao.readCategoryById(c1.getId())).thenReturn(c1);
        
        Mockito.when(categoryDao.readActiveSubCategoriesByCategory(c2, 5, 0)).thenReturn(new ArrayList<Category>());
        Mockito.when(categoryDao.readCategoryById(c2.getId())).thenReturn(c2);
        
        Mockito.when(categoryDao.readActiveSubCategoriesByCategory(c3, 5, 0)).thenReturn(merchandiseSubcategories);
        Mockito.when(categoryDao.readCategoryById(c3.getId())).thenReturn(c3);
        
        Mockito.when(categoryDao.readActiveSubCategoriesByCategory(c4, 5, 0)).thenReturn(new ArrayList<Category>());
        Mockito.when(categoryDao.readCategoryById(c4.getId())).thenReturn(c4);
        
        Mockito.when(categoryDao.readActiveSubCategoriesByCategory(c5, 5, 0)).thenReturn(new ArrayList<Category>());
        Mockito.when(categoryDao.readCategoryById(c5.getId())).thenReturn(c5);

        Mockito.when(categoryDao.readActiveSubCategoriesByCategory(c6, 5, 0)).thenReturn(new ArrayList<Category>());
        Mockito.when(categoryDao.readCategoryById(c6.getId())).thenReturn(c6);

        CategorySiteMapGenerator csmg = new CategorySiteMapGenerator(new MockEnvironment());
        csmg.setCategoryDao(categoryDao);
        csmg.setRowLimit(5);

        List<SiteMapGeneratorConfiguration> smgcList = new ArrayList<SiteMapGeneratorConfiguration>();

        CategorySiteMapGeneratorConfiguration c1CSMGC = new CategorySiteMapGeneratorConfigurationImpl();
        c1CSMGC.setDisabled(false);
        c1CSMGC.setSiteMapGeneratorType(SiteMapGeneratorType.CATEGORY);
        c1CSMGC.setSiteMapChangeFreq(SiteMapChangeFreqType.HOURLY);
        c1CSMGC.setSiteMapPriority(SiteMapPriorityType.POINT5);
        c1CSMGC.setRootCategory(c1);
        c1CSMGC.setStartingDepth(0);
        c1CSMGC.setEndingDepth(1);
        smgcList.add(c1CSMGC);

        CategorySiteMapGeneratorConfiguration c2CSMGC = new CategorySiteMapGeneratorConfigurationImpl();
        c2CSMGC.setDisabled(false);
        c2CSMGC.setSiteMapGeneratorType(SiteMapGeneratorType.CATEGORY);
        c2CSMGC.setSiteMapChangeFreq(SiteMapChangeFreqType.HOURLY);
        c2CSMGC.setSiteMapPriority(SiteMapPriorityType.POINT5);
        c2CSMGC.setRootCategory(c2);
        c2CSMGC.setStartingDepth(0);
        c2CSMGC.setEndingDepth(1);
        smgcList.add(c2CSMGC);

        CategorySiteMapGeneratorConfiguration c3CSMGC = new CategorySiteMapGeneratorConfigurationImpl();
        c3CSMGC.setDisabled(false);
        c3CSMGC.setSiteMapGeneratorType(SiteMapGeneratorType.CATEGORY);
        c3CSMGC.setSiteMapChangeFreq(SiteMapChangeFreqType.HOURLY);
        c3CSMGC.setSiteMapPriority(SiteMapPriorityType.POINT5);
        c3CSMGC.setRootCategory(c3);
        c3CSMGC.setStartingDepth(0);
        c3CSMGC.setEndingDepth(1);
        smgcList.add(c3CSMGC);

        CategorySiteMapGeneratorConfiguration c4CSMGC = new CategorySiteMapGeneratorConfigurationImpl();
        c4CSMGC.setDisabled(false);
        c4CSMGC.setSiteMapGeneratorType(SiteMapGeneratorType.CATEGORY);
        c4CSMGC.setSiteMapChangeFreq(SiteMapChangeFreqType.HOURLY);
        c4CSMGC.setSiteMapPriority(SiteMapPriorityType.POINT5);
        c4CSMGC.setRootCategory(c4);
        c4CSMGC.setStartingDepth(0);
        c4CSMGC.setEndingDepth(1);
        smgcList.add(c4CSMGC);

        testGenerator(smgcList, csmg, 2);

        File file1 = fileService.getResource("/sitemap_index.xml");
        File file2 = fileService.getResource("/sitemap1.xml");
        File file3 = fileService.getResource("/sitemap2.xml");
        File file4 = fileService.getResource("/sitemap3.xml");

        compareFiles(file1, "src/test/resources/org/broadleafcommerce/sitemap/category/sitemap_index.xml");
        compareFiles(file2, "src/test/resources/org/broadleafcommerce/sitemap/category/sitemap1.xml");
        compareFiles(file3, "src/test/resources/org/broadleafcommerce/sitemap/category/sitemap2.xml");
        compareFiles(file4, "src/test/resources/org/broadleafcommerce/sitemap/category/sitemap3.xml");

        testGenerator(smgcList, csmg, 50000);
        File file5 = fileService.getResource("/sitemap.xml");

        compareFiles(file5, "src/test/resources/org/broadleafcommerce/sitemap/category/sitemap.xml");

    }

}
