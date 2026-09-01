package dev.lunynt.opengate.identity;

public interface ProfileLookup {
    ProfileLookupResult find(String username);
}
