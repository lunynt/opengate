package dev.lunynt.opengate.identity;

public interface ProfileLookup {
    ProfileLookupResult find(String username);

    default ProfileLookupResult findFresh(String username, String address) {
        return find(username);
    }
}
