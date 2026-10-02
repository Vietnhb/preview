package com.example.backend.system.account;

import com.example.backend.system.account.model.entity.Permission;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.PermissionCode;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.PermissionRepository;
import com.example.backend.system.account.service.AccountAccessService;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test fixtures for the user_permissions model. */
public final class TestPermissions {
    private TestPermissions() {}

    /** Access service backed by an in-memory permission catalog. */
    public static AccountAccessService access() {
        PermissionRepository catalog = mock(PermissionRepository.class);
        when(catalog.findByCodeIn(anyCollection())).thenAnswer(call -> {
            Collection<String> codes = call.getArgument(0);
            return codes.stream().map(Permission::new).toList();
        });
        return new AccountAccessService(catalog);
    }

    /** Replace the user's permissions with exactly these codes. */
    public static User set(User user, String... codes) {
        user.getPermissions().clear();
        for (String code : codes) user.grantPermission(new Permission(code), null);
        return user;
    }

    /** Grant what a newly created account of the user's role receives (STAFF: TEACH; REVIEWER: both). */
    public static User defaults(User user) {
        RoleName.from(user.getRole() == null ? null : user.getRole().getName()).ifPresent(role ->
                set(user, PermissionCode.defaultsFor(role).stream().map(Enum::name).toArray(String[]::new)));
        return user;
    }
}
