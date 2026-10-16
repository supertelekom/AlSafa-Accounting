package com.alsafa.native5.accounting;

import java.util.Objects;

/**
 * Explicit write boundary: only the designated tablet can post accounting events.
 * UI hiding is not an authorization boundary; all posting goes through this gateway.
 * Remote Firestore security rules must independently enforce device authorization.
 */
public final class DeviceRolePostingGateway implements PostingGateway {
    public enum Role { TABLET_AUTHORITY, PHONE_READ_ONLY }
    private final Role role;
    private final PostingGateway delegate;
    public DeviceRolePostingGateway(Role role,PostingGateway delegate) {
        this.role=Objects.requireNonNull(role,"role");
        this.delegate=Objects.requireNonNull(delegate,"delegate");
    }
    @Override public void post(PostingBatch batch) {
        if(role!=Role.TABLET_AUTHORITY)
            throw new SecurityException("Read-only phone cannot post accounting events");
        delegate.post(Objects.requireNonNull(batch,"batch"));
    }
}
