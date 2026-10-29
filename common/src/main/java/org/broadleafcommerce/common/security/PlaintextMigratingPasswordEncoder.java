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
package org.broadleafcommerce.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Transitional {@link PasswordEncoder} for installations whose existing credentials were stored with the former
 * {@code NoOpPasswordEncoder} default.
 * <p>
 * New values are always encoded with BCrypt. Stored values that are not BCrypt hashes are treated as legacy plaintext
 * and compared in constant time, and {@link #upgradeEncoding(String)} reports them as needing an upgrade so that an
 * {@code AuthenticationProvider} configured with a {@code UserDetailsPasswordService} rehashes them on the next
 * successful login.
 * <p>
 * Enable it temporarily with {@code password.site.encoder} / {@code password.admin.encoder}, and switch back to
 * {@link BCryptPasswordEncoder} once no plaintext values remain, since it keeps accepting plaintext rows until then.
 */
public class PlaintextMigratingPasswordEncoder implements PasswordEncoder {

    protected static final Pattern BCRYPT_PATTERN = Pattern.compile("\\A\\$2(a|y|b)?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}");

    protected final BCryptPasswordEncoder delegate;

    public PlaintextMigratingPasswordEncoder() {
        this(new BCryptPasswordEncoder());
    }

    public PlaintextMigratingPasswordEncoder(BCryptPasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        if (isBCryptHash(encodedPassword)) {
            return delegate.matches(rawPassword, encodedPassword);
        }
        return MessageDigest.isEqual(
                rawPassword.toString().getBytes(StandardCharsets.UTF_8),
                encodedPassword.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        return !isBCryptHash(encodedPassword) || delegate.upgradeEncoding(encodedPassword);
    }

    protected boolean isBCryptHash(String encodedPassword) {
        return BCRYPT_PATTERN.matcher(encodedPassword).matches();
    }

}
