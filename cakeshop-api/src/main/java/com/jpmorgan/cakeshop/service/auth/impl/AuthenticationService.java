package com.jpmorgan.cakeshop.service.auth.impl;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service("authService")
public class AuthenticationService implements AuthenticationProvider {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(AuthenticationService.class);

    private final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

  @Value("${cakeshop.cred1:\"\"}")
    private String cred1;

    @Value("${cakeshop.cred2:\"\"}")
    private String cred2;

    @Override
    public Authentication authenticate(final Authentication authentication) throws AuthenticationException {
        final String userName = authentication.getName();
        final String password = authentication.getCredentials().toString();
        if (userName.equals(cred1) && ENCODER.matches(password, cred2)) {
            return new UsernamePasswordAuthenticationToken(userName, password);
        } else {
            throw new AuthenticationException("Unable to authenticate user") {
            };
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

}
