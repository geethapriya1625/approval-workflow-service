package io.github.geethapriya1625.workflow.service;

import io.github.geethapriya1625.workflow.domain.Role;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record CurrentUser(String id, Set<Role> roles) {

    public static CurrentUser from(Authentication auth) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (name != null && name.startsWith("ROLE_")) {
                try {
                    roles.add(Role.valueOf(name.substring(5)));
                } catch (IllegalArgumentException ignored) {
                    // roles this service does not know about are irrelevant to it
                }
            }
        }
        return new CurrentUser(auth.getName(), Set.copyOf(roles));
    }
}
