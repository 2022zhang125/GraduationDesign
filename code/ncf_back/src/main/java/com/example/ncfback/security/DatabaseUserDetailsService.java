package com.example.ncfback.security;

import com.example.ncfback.entity.User;
import com.example.ncfback.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = username == null ? "" : username.trim();
        User user = userMapper.findByExternalUserNo(normalizedUsername);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + normalizedUsername);
        }
        if (!StringUtils.hasText(user.getPasswordHash())) {
            throw new UsernameNotFoundException("Password is not initialized for user: " + normalizedUsername);
        }

        boolean enabled = user.getUserStatus() != null && user.getUserStatus() == 1;
        return org.springframework.security.core.userdetails.User.withUsername(user.getExternalUserNo())
                .password(user.getPasswordHash())
                .authorities("ROLE_USER")
                .disabled(!enabled)
                .build();
    }
}
