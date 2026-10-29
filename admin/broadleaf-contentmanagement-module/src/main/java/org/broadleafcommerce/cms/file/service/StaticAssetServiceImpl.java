/*-
 * #%L
 * BroadleafCommerce CMS Module
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
package org.broadleafcommerce.cms.file.service;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.broadleafcommerce.cms.field.type.StorageType;
import org.broadleafcommerce.cms.file.StaticAssetMultiTenantExtensionManager;
import org.broadleafcommerce.cms.file.dao.StaticAssetDao;
import org.broadleafcommerce.cms.file.domain.ImageStaticAsset;
import org.broadleafcommerce.cms.file.domain.ImageStaticAssetImpl;
import org.broadleafcommerce.cms.file.domain.StaticAsset;
import org.broadleafcommerce.cms.file.domain.StaticAssetImpl;
import org.broadleafcommerce.common.extension.ExtensionResultStatusType;
import org.broadleafcommerce.common.file.service.StaticAssetPathService;
import org.broadleafcommerce.common.util.StringUtil;
import org.broadleafcommerce.common.util.TransactionUtils;
import org.broadleafcommerce.openadmin.server.service.artifact.image.ImageArtifactProcessor;
import org.broadleafcommerce.openadmin.server.service.artifact.image.ImageMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import jakarta.annotation.Resource;

/**
 * Created by bpolster.
 */
@Service("blStaticAssetService")
public class StaticAssetServiceImpl implements StaticAssetService {

    private static final Log LOG = LogFactory.getLog(StaticAssetServiceImpl.class);
    private static final String UPLOAD_FILE_EXTENSION_EXCEPTION = "java.io.IOException: Invalid extension type of file.";
    private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();
    private final Random random = new Random();
    private final String FILE_NAME_CHARS = "0123456789abcdef";
    @Resource(name = "blImageArtifactProcessor")
    protected ImageArtifactProcessor imageArtifactProcessor;
    @Value("${asset.use.filesystem.storage}")
    protected boolean storeAssetsOnFileSystem = false;
    @Resource(name = "blStaticAssetDao")
    protected StaticAssetDao staticAssetDao;
    @Resource(name = "blStaticAssetStorageService")
    protected StaticAssetStorageService staticAssetStorageService;
    @Resource(name = "blStaticAssetPathService")
    protected StaticAssetPathService staticAssetPathService;
    @Resource(name = "blStaticAssetMultiTenantExtensionManager")
    protected StaticAssetMultiTenantExtensionManager staticAssetExtensionManager;
    @Value("${should.accept.non.image.asset:true}")
    protected boolean shouldAcceptNonImageAsset;
    @Value("${disabled.file.extensions}")
    protected String disabledFileExtensions;
    @Value("${allowed.file.extensions}")
    protected String allowedFileExtensions;
    @Value("${static.asset.invalid.chars.in.filename}")
    protected char[] notAllowedCharsInFileName;

    @Value("${static.asset.exception.on.invalid.char.in.filename:false}")
    protected boolean exceptionOnInvalidChar = false;

    @Value("${static.asset.invalid.chars.replacement}")
    protected String replacementString;

    @Value("${static.asset.reject.mismatched.content.type:true}")
    protected boolean rejectMismatchedContentType = true;

    private static String normalizeFileExtension(MultipartFile file) {
        int index = file.getOriginalFilename().lastIndexOf(".");
        return file.getOriginalFilename().substring(0, index + 1)
                + file.getOriginalFilename().substring(index + 1).toLowerCase();
    }

    private static String getUploadedFileName(MultipartFile file) {
        return StringUtils.isNotBlank(file.getOriginalFilename()) ? file.getOriginalFilename() : file.getName();
    }

    private static List<String> parseExtensionList(String extensionList) {
        return Arrays.asList(extensionList.toLowerCase().trim().split("\\s*,\\s*"));
    }

    /**
     * Detects the media type of the uploaded file from its bytes only (the file name is not used as a hint).
     *
     * @return the detected type, or null if detection failed or only yielded {@code application/octet-stream}
     */
    protected MediaType detectContentType(MultipartFile file) {
        try (TikaInputStream stream = TikaInputStream.get(file.getBytes())) {
            return detectContentType(stream);
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Detects the media type of a stream from its leading bytes only. The stream must support mark/reset; it is
     * reset to its original position afterwards.
     *
     * @return the detected type, or null if detection failed or only yielded {@code application/octet-stream}
     */
    protected MediaType detectContentType(InputStream stream) {
        try {
            MediaType detected = new Tika().getDetector().detect(stream, new Metadata());
            if (detected != null && !MediaType.OCTET_STREAM.equals(detected)) {
                return MIME_TYPES.getMediaTypeRegistry().normalize(detected.getBaseType());
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Resolves the MIME type to store for an asset. The type detected from the content wins; the more specific
     * name-derived type is only used when it is a specialization of the detected type (e.g. {@code text/css} for
     * {@code text/plain} content). When the content type cannot be detected, the name-derived type is used unless
     * it is a type browsers render as an active document (HTML, XML, SVG), in which case
     * {@code application/octet-stream} is stored.
     */
    protected String resolveMimeType(InputStream stream, String fileName) {
        MediaType contentType = detectContentType(stream);
        MediaType nameType = detectNameType(fileName);
        if (contentType != null) {
            if (nameType != null && MIME_TYPES.getMediaTypeRegistry().isInstanceOf(nameType, contentType)) {
                return nameType.toString();
            }
            return contentType.toString();
        }
        if (nameType == null || isActiveContentType(nameType)) {
            return MediaType.OCTET_STREAM.toString();
        }
        return nameType.toString();
    }

    /**
     * Whether a browser navigating to a resource of this type would render it as a document able to run script.
     */
    public static boolean isActiveContentType(MediaType type) {
        if (type == null) {
            return false;
        }
        String baseType = type.getBaseType().toString().toLowerCase();
        return baseType.contains("html")
                || baseType.endsWith("+xml")
                || baseType.equals("text/xml")
                || baseType.equals("application/xml")
                || baseType.equals("text/xsl");
    }

    protected MediaType detectNameType(String fileName) {
        MediaType nameType = MediaType.parse(new Tika().detect(fileName));
        return nameType == null ? null : MIME_TYPES.getMediaTypeRegistry().normalize(nameType.getBaseType());
    }

    protected List<String> getExtensions(MediaType type) {
        if (type == null) {
            return Collections.emptyList();
        }
        try {
            List<String> extensions = new ArrayList<>();
            for (String extension : MIME_TYPES.forName(type.toString()).getExtensions()) {
                extensions.add(StringUtils.removeStart(extension, ".").toLowerCase());
            }
            return extensions;
        } catch (MimeTypeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Whether {@code nameType} is a strict specialization of {@code contentType}, e.g. {@code text/css} for
     * {@code text/plain} content. In that case the extensions registered for the generic content type (such as
     * {@code txt}) do not describe the upload.
     */
    protected boolean isSpecializationOf(MediaType nameType, MediaType contentType) {
        return nameType != null && contentType != null && !nameType.equals(contentType)
                && MIME_TYPES.getMediaTypeRegistry().isInstanceOf(nameType, contentType);
    }

    /**
     * The name-based type is consistent with the content when the content type is unknown, when the file name
     * extension is registered for the content type, or when the name type is the content type or a
     * specialization of it (e.g. {@code text/css} for {@code text/plain} content).
     */
    protected boolean isNameConsistentWithContent(String nameExtension, MediaType nameType, MediaType contentType) {
        if (contentType == null) {
            return true;
        }
        if (StringUtils.isNotEmpty(nameExtension) && getExtensions(contentType).contains(nameExtension)) {
            return true;
        }
        return nameType != null && MIME_TYPES.getMediaTypeRegistry().isInstanceOf(nameType, contentType);
    }

    @Override
    public StaticAsset findStaticAssetById(Long id) {
        return staticAssetDao.readStaticAssetById(id);
    }

    @Override
    public List<StaticAsset> readAllStaticAssets() {
        return staticAssetDao.readAllStaticAssets();
    }

    @Override
    public Long findTotalStaticAssetCount() {
        return staticAssetDao.readTotalStaticAssetCount();
    }

    protected String getFileExtension(String fileName) {
        int pos = fileName.lastIndexOf(".");
        if (pos > 0) {
            return fileName.substring(pos + 1).toLowerCase();
        } else {
            LOG.warn("No extension provided for asset : " + StringUtil.sanitize(fileName));
            return null;
        }
    }

    /**
     * Generates a filename as a set of Hex digits.
     *
     * @param size
     * @return
     */
    protected String generateFileName(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            int pos = random.nextInt(FILE_NAME_CHARS.length());
            sb = sb.append(FILE_NAME_CHARS.charAt(pos));
        }
        return sb.toString();
    }

    /**
     * Will assemble the url from the passed in properties as
     * /{entityType}/{fileName}
     * /product/7001-ab12
     * <p>
     * If the properties above are not set, it will generate the fileName randomly.
     *
     * @param assetProperties
     * @param originalFilename
     * @return
     */
    protected String buildAssetURL(Map<String, String> assetProperties, String originalFilename) {
        StringBuilder path = new StringBuilder("/");

        String entityType = assetProperties.get("entityType");
        String entityId = assetProperties.get("entityId");
        String fileName = assetProperties.get("fileName");

        if (entityType != null && !"null".equals(entityType)) {
            path = path.append(entityType).append("/");
        }

        if (entityId != null && !"null".equals(entityId)) {
            path = path.append(entityId).append("/");
        }

        if (fileName != null) {
            int pos = fileName.indexOf(":");
            if (pos > 0) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Removing protocol from URL name" + StringUtil.sanitize(fileName));
                }
                fileName = fileName.substring(pos + 1);
            }
        } else {
            fileName = originalFilename;
        }

        return path.append(fileName).toString();
    }

    /**
     * Validates both the file name extension and the type detected from the file content against
     * {@code allowed.file.extensions} / {@code disabled.file.extensions}. The stored name and extension of an asset
     * come from the uploaded file name, so uploads whose name does not match their content are rejected as well
     * (see {@code static.asset.reject.mismatched.content.type}).
     */
    public void validateFileExtension(MultipartFile file) throws IOException {
        final String fileName = getUploadedFileName(file);
        final String nameExtension = StringUtils.defaultString(FilenameUtils.getExtension(fileName)).toLowerCase();
        final MediaType contentType = detectContentType(file);
        final MediaType nameType = detectNameType(fileName);
        final List<String> contentExtensions = getExtensions(contentType);
        //if we have whitelist, don't care about blacklist
        if (StringUtils.isNotBlank(allowedFileExtensions)) {
            final List<String> extensions = parseExtensionList(allowedFileExtensions);
            boolean nameAllowed = extensions.contains(nameExtension);
            boolean contentAllowed = contentType != null
                    && (!Collections.disjoint(extensions, contentExtensions)
                    || (nameAllowed && isNameConsistentWithContent(nameExtension, nameType, contentType)));
            if (!nameAllowed || !contentAllowed) {
                LOG.error("Invalid extension type of file " + StringUtil.sanitize(fileName) + " (detected content type "
                        + contentType + "). Only following is allowed:" + allowedFileExtensions);
                throw new IOException("Not allowed extension type of file.");
            }
        } else if (StringUtils.isNotBlank(disabledFileExtensions)) {
            final List<String> extensions = parseExtensionList(disabledFileExtensions);
            boolean contentDisabled = !isSpecializationOf(nameType, contentType)
                    && !Collections.disjoint(extensions, contentExtensions);
            if (extensions.contains(nameExtension) || contentDisabled) {
                LOG.error("Invalid extension type of file " + StringUtil.sanitize(fileName) + " (detected content type "
                        + contentType + "). Disabled files:" + disabledFileExtensions);
                throw new IOException("Invalid extension type of file.");
            }
        }
        if (rejectMismatchedContentType && !isNameConsistentWithContent(nameExtension, nameType, contentType)) {
            LOG.error("Invalid extension type of file " + StringUtil.sanitize(fileName) + ": name implies " + nameType
                    + " but content was detected as " + contentType);
            throw new IOException("Invalid extension type of file.");
        }
    }

    @Override
    @Transactional(TransactionUtils.DEFAULT_TRANSACTION_MANAGER)
    public StaticAsset createStaticAssetFromFile(MultipartFile file, Map<String, String> properties) {
        try {
            validateFileExtension(file);
            staticAssetStorageService.validateFileSize(file);
            String fileName = normalizeFileExtension(file);
            boolean b = validateFileName(fileName);
            if (b) {
                fileName = fileName.replaceAll(
                        "[" + String.valueOf(notAllowedCharsInFileName) + "]",
                        replacementString
                );
            }
            return createStaticAsset(file.getInputStream(), fileName, file.getSize(), properties);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean validateFileName(String fileName) {
        boolean result = StringUtils.containsAny(fileName, notAllowedCharsInFileName);
        if (exceptionOnInvalidChar && result) {
            throw new RuntimeException("File contains illegal chars. Illegal chars are:[" + String.valueOf(notAllowedCharsInFileName) + "]");
        }
        return result;
    }

    @Override
    @Transactional(TransactionUtils.DEFAULT_TRANSACTION_MANAGER)
    public StaticAsset createStaticAsset(
            InputStream inputStream,
            String fileName,
            long fileSize,
            Map<String, String> properties
    ) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        if (!inputStream.markSupported()) {
            inputStream = new BufferedInputStream(inputStream);
        }
        String mimeType = resolveMimeType(inputStream, fileName);

        String fullUrl = buildAssetURL(properties, fileName);
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append(fullUrl);
        ExtensionResultStatusType resultStatusType = staticAssetExtensionManager.getProxy().modifyDuplicateAssetURL(urlBuilder);
        fullUrl = urlBuilder.toString();
        StaticAsset newAsset = staticAssetDao.readStaticAssetByFullUrl(fullUrl);
        // If no ExtensionManager modified the URL to handle duplicates, then go ahead and run default
        // logic for handling duplicate files.
        if (resultStatusType != ExtensionResultStatusType.HANDLED) {
            int count = 0;
            while (newAsset != null) {
                count++;
                //try the new format first, then the old
                newAsset = staticAssetDao.readStaticAssetByFullUrl(getCountUrl(fullUrl, count, false));
                if (newAsset == null) {
                    newAsset = staticAssetDao.readStaticAssetByFullUrl(getCountUrl(fullUrl, count, true));
                }
            }

            if (count > 0) {
                fullUrl = getCountUrl(fullUrl, count, false);
            }
        }

        try {
            ImageMetadata metadata = imageArtifactProcessor.getImageMetadata(inputStream);
            newAsset = new ImageStaticAssetImpl();
            ((ImageStaticAsset) newAsset).setWidth(metadata.getWidth());
            ((ImageStaticAsset) newAsset).setHeight(metadata.getHeight());
        } catch (Exception e) {
            //must not be an image stream
            LOG.warn("unable to convert asset:" + fileName + " into Image");
            LOG.debug(e);

            if (getShouldAcceptNonImageAsset()) {
                newAsset = createNonImageAsset(inputStream, fileName, properties);
            } else {
                throw new RuntimeException("Selected Asset/File was not valid image.");
            }
        }
        if (storeAssetsOnFileSystem) {
            newAsset.setStorageType(StorageType.FILESYSTEM);
        } else {
            newAsset.setStorageType(StorageType.DATABASE);
        }

        newAsset.setName(fileName);
        newAsset.setMimeType(mimeType);
        newAsset.setFileExtension(getFileExtension(fileName));
        newAsset.setFileSize(fileSize);
        newAsset.setFullUrl(fullUrl);

        return staticAssetDao.addOrUpdateStaticAsset(newAsset, false);
    }

    /**
     * Hook-point for implementors to add custom business logic for handling files that are non-images
     *
     * @param inputStream
     * @param fileName
     * @param properties
     * @return
     */
    protected StaticAsset createNonImageAsset(InputStream inputStream, String fileName, Map<String, String> properties) {
        return new StaticAssetImpl();
    }

    /**
     * Gets the count URL based on the original fullUrl. If requested in legacy format this will return URLs like:
     * <p>
     * /path/to/image.jpg-1
     * /path/to/image.jpg-2
     * <p>
     * Whereas if this is in non-legacy format (<b>legacy</b> == false):
     * <p>
     * /path/to/image-1.jpg
     * /path/to/image-2.jpg
     * <p>
     * Used to deal with duplicate URLs of uploaded assets
     */
    protected String getCountUrl(String fullUrl, int count, boolean legacyFormat) {
        String countUrl = fullUrl + '-' + count;
        int dotIndex = fullUrl.lastIndexOf('.');
        if (dotIndex != -1 && !legacyFormat) {
            countUrl = fullUrl.substring(0, dotIndex) + '-' + count + '.' + fullUrl.substring(dotIndex + 1);
        }

        return countUrl;
    }

    /**
     * @deprecated use {@link #resolveMimeType(InputStream, String)}, which must be given the stream before any of
     * it has been consumed
     */
    @Deprecated
    protected void getMimeType(InputStream inputStream, String fileName, StaticAsset newAsset) {
        if (!inputStream.markSupported()) {
            inputStream = new BufferedInputStream(inputStream);
        }
        newAsset.setMimeType(resolveMimeType(inputStream, fileName));
    }

    @Override
    public StaticAsset findStaticAssetByFullUrl(String fullUrl) {
        try {
            fullUrl = URLDecoder.decode(fullUrl, "UTF-8");
            //strip out the jsessionid if it's there
            fullUrl = fullUrl.replaceAll("(?i);jsessionid.*?=.*?(?=\\?|$)", "");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unsupported encoding to decode fullUrl", e);
        }
        return staticAssetDao.readStaticAssetByFullUrl(fullUrl);
    }

    @Override
    @Transactional(TransactionUtils.DEFAULT_TRANSACTION_MANAGER)
    public StaticAsset addStaticAsset(StaticAsset staticAsset) {
        StaticAsset newAsset = staticAssetDao.addOrUpdateStaticAsset(staticAsset, true);
        return newAsset;
    }

    @Override
    @Transactional(TransactionUtils.DEFAULT_TRANSACTION_MANAGER)
    public StaticAsset updateStaticAsset(StaticAsset staticAsset) {
        return staticAssetDao.addOrUpdateStaticAsset(staticAsset, true);
    }

    @Override
    @Transactional(TransactionUtils.DEFAULT_TRANSACTION_MANAGER)
    public void deleteStaticAsset(StaticAsset staticAsset) {
        staticAssetDao.delete(staticAsset);
    }

    @Override
    public String getStaticAssetUrlPrefix() {
        return staticAssetPathService.getStaticAssetUrlPrefix();
    }

    @Override
    public String getPrefixedStaticAssetUrl(String assetUrl) {
        String staticAssetUrlPrefix = getStaticAssetUrlPrefix();
        if (staticAssetUrlPrefix != null && !staticAssetUrlPrefix.startsWith("/")) {
            staticAssetUrlPrefix = "/" + staticAssetUrlPrefix;
        }
        if (staticAssetUrlPrefix != null) {
            return staticAssetUrlPrefix + assetUrl;
        }
        return assetUrl;
    }

    @Override
    public String getStaticAssetEnvironmentUrlPrefix() {
        return staticAssetPathService.getStaticAssetEnvironmentUrlPrefix();
    }

    @Override
    public String getStaticAssetEnvironmentSecureUrlPrefix() {
        return staticAssetPathService.getStaticAssetEnvironmentSecureUrlPrefix();
    }

    @Override
    public String convertAssetPath(String assetPath, String contextPath, boolean secureRequest) {
        return staticAssetPathService.convertAssetPath(assetPath, contextPath, secureRequest);
    }

    public boolean getShouldAcceptNonImageAsset() {
        return shouldAcceptNonImageAsset;
    }

    public void setShouldAcceptNonImageAsset(boolean accept) {
        shouldAcceptNonImageAsset = accept;
    }

    public String getDisabledFileExtensions() {
        return disabledFileExtensions;
    }

    public void setDisabledFileExtensions(String disabledFileExtensions) {
        this.disabledFileExtensions = disabledFileExtensions;
    }

    public String getAllowedFileExtensions() {
        return allowedFileExtensions;
    }

    public void setAllowedFileExtensions(String allowedFileExtensions) {
        this.allowedFileExtensions = allowedFileExtensions;
    }

    public boolean isRejectMismatchedContentType() {
        return rejectMismatchedContentType;
    }

    public void setRejectMismatchedContentType(boolean rejectMismatchedContentType) {
        this.rejectMismatchedContentType = rejectMismatchedContentType;
    }

}
