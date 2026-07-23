/*-
 * #%L
 * BroadleafCommerce Open Admin Platform
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
package org.broadleafcommerce.openadmin.server.security.remote;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.exception.SecurityServiceException;
import org.broadleafcommerce.common.exception.ServiceException;
import org.broadleafcommerce.common.extension.ExtensionResultStatusType;
import org.broadleafcommerce.common.presentation.client.PersistencePerspectiveItemType;
import org.broadleafcommerce.common.security.service.ExploitProtectionService;
import org.broadleafcommerce.common.util.StringUtil;
import org.broadleafcommerce.common.web.SandBoxContext;
import org.broadleafcommerce.openadmin.dto.Entity;
import org.broadleafcommerce.openadmin.dto.PersistencePackage;
import org.broadleafcommerce.openadmin.dto.Property;
import org.broadleafcommerce.openadmin.dto.SectionCrumb;
import org.broadleafcommerce.openadmin.server.security.domain.AdminPermission;
import org.broadleafcommerce.openadmin.server.security.domain.AdminRole;
import org.broadleafcommerce.openadmin.server.security.domain.AdminUser;
import org.broadleafcommerce.openadmin.server.security.extension.AdminSecurityCheckExtensionManager;
import org.broadleafcommerce.openadmin.server.security.service.RowLevelSecurityService;
import org.broadleafcommerce.openadmin.server.security.service.type.PermissionType;
import org.broadleafcommerce.openadmin.server.service.ValidationException;
import org.broadleafcommerce.openadmin.server.service.persistence.validation.GlobalValidationResult;
import org.springframework.cglib.core.CollectionUtils;
import org.springframework.cglib.core.Transformer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.Resource;

/**
 * Service for handeling security with Ajax components.  Serves two functions.
 * <ul>
 *     <li>
 *         Converts the ServerSide AdminUser to a client level admin user with
 *         appropriate roles defined.
 *     </li>
 *     <li>
 *         Provides a method to check if the current logged in user matches the
 *         client side user and verifies whether that user has access to the
 *         entity operation they are trying to perform.
 *     </li>
 * </ul>
 * 1.
 *
 * @author jfischer
 */
@Service("blAdminSecurityRemoteService")
public class AdminSecurityServiceRemote implements AdminSecurityService, SecurityVerifier {

    public static final String ANONYMOUS_USER_NAME = "anonymousUser";
    private static final Log LOG = LogFactory.getLog(AdminSecurityServiceRemote.class);

    @Resource(name = "blAdminSecurityService")
    protected org.broadleafcommerce.openadmin.server.security.service.AdminSecurityService securityService;

    @Resource(name = "blExploitProtectionService")
    protected ExploitProtectionService exploitProtectionService;

    @Resource(name = "blRowLevelSecurityService")
    protected RowLevelSecurityService rowLevelSecurityService;

    @Resource
    protected AdminSecurityCheckExtensionManager securityCheckExtensionManager;

    @Override
    public org.broadleafcommerce.openadmin.server.security.remote.AdminUser getAdminUser() throws ServiceException {
        AdminUser persistentAdminUser = getPersistentAdminUser();
        if (persistentAdminUser != null) {
            org.broadleafcommerce.openadmin.server.security.remote.AdminUser response =
                    new org.broadleafcommerce.openadmin.server.security.remote.AdminUser();
            for (AdminRole role : persistentAdminUser.getAllRoles()) {
                response.getRoles().add(role.getName());
                for (AdminPermission permission : role.getAllPermissions()) {
                    response.getPermissions().add(permission.getName());
                }
            }
            for (AdminPermission permission : persistentAdminUser.getAllPermissions()) {
                response.getPermissions().add(permission.getName());
            }
            response.setUserName(persistentAdminUser.getLogin());
            response.setCurrentSandBoxId(String.valueOf(SandBoxContext.getSandBoxContext().getSandBoxId()));
            response.setEmail(persistentAdminUser.getEmail());
            response.setName(persistentAdminUser.getName());
            response.setPhoneNumber(persistentAdminUser.getPhoneNumber());
            response.setId(persistentAdminUser.getId());
            return response;
        }

        return null;
    }

    @Override
    public AdminUser getPersistentAdminUser() {
        SecurityContext ctx = SecurityContextHolder.getContext();
        if (ctx != null) {
            Authentication auth = ctx.getAuthentication();
            if (auth != null && !auth.getName().equals(ANONYMOUS_USER_NAME)) {
                UserDetails temp = (UserDetails) auth.getPrincipal();

                return securityService.readAdminUserByUserName(temp.getUsername());
            }
        }

        return null;
    }

    @Override
    public void securityCheck(PersistencePackage persistencePackage, EntityOperationType operationType) throws ServiceException {
        // The entity actually being mutated is the server-trusted target ceiling. Authorization must be
        // anchored to it. The section crumbs are supplied on the request and are only allowed to satisfy
        // the check as parent-permission inheritance for target ceilings that are not independently secured.
        String targetCeilingName = persistencePackage.getSecurityCeilingEntityFullyQualifiedClassname();
        Set<String> inheritedCeilingNames = new LinkedHashSet<>();
        if (!ArrayUtils.isEmpty(persistencePackage.getSectionCrumbs())) {
            inheritedCeilingNames.addAll(CollectionUtils.transform(Arrays.asList(persistencePackage.getSectionCrumbs()),
                    new Transformer() {
                        @Override
                        public Object transform(Object o) {
                            return ((SectionCrumb) o).getSectionIdentifier();
                        }
                    }));
        }
        inheritedCeilingNames.remove(targetCeilingName);

        Entity entity = persistencePackage.getEntity();

        if (persistencePackage.getPersistencePerspectiveItems().containsKey(PersistencePerspectiveItemType.ADORNEDTARGETLIST)) {
            if (persistencePackage.getEntity() != null) {
                for (Property property : persistencePackage.getProperties()) {
                    if (property.getName() != null && property.getName().endsWith(".id")
                            && property.getValue() == null) {
                        entity.addGlobalValidationError("adornedTargetRequired");
                        throw new ValidationException(entity);
                    }
                }
            }
        }

        GlobalValidationResult globalValidationResult = null;
        if (operationType.equals(EntityOperationType.UPDATE)) {
            globalValidationResult = rowLevelSecurityService.validateUpdateRequest(
                    getPersistentAdminUser(), entity, persistencePackage
            );
        } else if (operationType.equals(EntityOperationType.REMOVE)) {
            globalValidationResult = rowLevelSecurityService.validateRemoveRequest(
                    getPersistentAdminUser(), entity, persistencePackage
            );
        } else if (operationType.equals(EntityOperationType.ADD)) {
            globalValidationResult = rowLevelSecurityService.validateAddRequest(
                    getPersistentAdminUser(), entity, persistencePackage
            );
        }

        if (globalValidationResult != null) {
            if (!globalValidationResult.isValid()) {
                if (StringUtils.isEmpty(globalValidationResult.getErrorMessage())) {
                    entity.addGlobalValidationError("rowLevelSecurityFailed");
                } else {
                    entity.addGlobalValidationErrors(globalValidationResult.getErrorMessages());
                }

                throw new ValidationException(entity, "Row level security check failed for " + operationType);
            }
        }

        securityCheck(targetCeilingName, inheritedCeilingNames.toArray(new String[inheritedCeilingNames.size()]), operationType);
    }

    @Override
    public void securityCheck(String ceilingEntityFullyQualifiedName, EntityOperationType operationType) throws ServiceException {
        securityCheck(new String[]{ceilingEntityFullyQualifiedName}, operationType);
    }

    protected void securityCheck(String[] ceilingNames, EntityOperationType operationType) throws ServiceException {
        if (ArrayUtils.isEmpty(ceilingNames)) {
            throw new SecurityServiceException("Security Check Failed: ceilingNames not specified");
        }
        securityCheck(ceilingNames[0], Arrays.copyOfRange(ceilingNames, 1, ceilingNames.length), operationType);
    }

    /**
     * Performs the authorization check for an admin persistence operation.
     *
     * <p>Authorization is anchored to {@code targetCeilingName} - the server-trusted ceiling of the entity that
     * is actually being mutated. The user must be qualified for the operation on that ceiling. The
     * {@code inheritedCeilingNames} (derived from request-supplied section crumbs) may only satisfy the check
     * when the target ceiling is not independently secured for this operation, which preserves legitimate
     * parent-permission inheritance for collection members that rely on their parent section while preventing
     * a caller from authorizing an operation on a sensitive, independently-secured entity by supplying an
     * unrelated crumb they happen to be qualified for.</p>
     */
    protected void securityCheck(String targetCeilingName, String[] inheritedCeilingNames, EntityOperationType operationType) throws ServiceException {
        if (StringUtils.isBlank(targetCeilingName)) {
            throw new SecurityServiceException("Security Check Failed: ceilingNames not specified");
        }
        AdminUser persistentAdminUser = getPersistentAdminUser();
        PermissionType permissionType = getPermissionType(operationType);

        List<String> allCeilingNames = new ArrayList<>();
        allCeilingNames.add(targetCeilingName);
        if (!ArrayUtils.isEmpty(inheritedCeilingNames)) {
            allCeilingNames.addAll(Arrays.asList(inheritedCeilingNames));
        }

        final ExtensionResultStatusType resultStatusType = securityCheckExtensionManager.getProxy()
                .handleAdminSecurityCheck(persistentAdminUser, permissionType, allCeilingNames);
        if (resultStatusType == ExtensionResultStatusType.HANDLED) {
            return;
        }

        //the target ceiling (the entity actually being operated on) must itself authorize the operation
        if (securityService.isUserQualifiedForOperationOnCeilingEntity(persistentAdminUser, permissionType, targetCeilingName)) {
            return;
        }

        //only fall back to breadcrumb-based parent-permission inheritance when the target ceiling is not
        //independently secured; this never lets an unrelated crumb authorize a secured entity
        if (!securityService.doesOperationExistForCeilingEntity(permissionType, targetCeilingName)
                && !ArrayUtils.isEmpty(inheritedCeilingNames)) {
            for (String inheritedCeilingName : inheritedCeilingNames) {
                if (securityService.isUserQualifiedForOperationOnCeilingEntity(persistentAdminUser, permissionType, inheritedCeilingName)) {
                    return;
                }
            }
        }

        SecurityServiceException primaryException = new SecurityServiceException("Security Check Failed for entity operation: "
                + operationType.toString() + " (" + targetCeilingName + ")");
        //check if the requested entity is not configured and warn
        if (!securityService.doesOperationExistForCeilingEntity(permissionType, targetCeilingName)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Detected security request for an unregistered ceiling entity (" + StringUtil.sanitize(targetCeilingName) + "). " +
                        "As a result, the request failed. Please make sure to configure security for any ceiling entities " +
                        "referenced via the admin. This is usually accomplished by adding records in the " +
                        "BLC_ADMIN_PERMISSION_ENTITY table. Note, depending on how the entity in question is used, you " +
                        "may need to add to BLC_ADMIN_PERMISSION, BLC_ADMIN_ROLE_PERMISSION_XREF and BLC_ADMIN_SEC_PERM_XREF.", primaryException);
            }
        }
        throw primaryException;
    }

    protected PermissionType getPermissionType(EntityOperationType operationType) {
        switch (operationType) {
            case ADD:
                return PermissionType.CREATE;
            case FETCH:
                return PermissionType.READ;
            case REMOVE:
                return PermissionType.DELETE;
            case UPDATE:
                return PermissionType.UPDATE;
            case INSPECT:
                return PermissionType.READ;
            default:
                return PermissionType.OTHER;
        }
    }

}
