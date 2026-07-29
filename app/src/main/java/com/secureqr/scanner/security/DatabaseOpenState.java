package com.secureqr.scanner.security;

public enum DatabaseOpenState {
    NORMAL,
    NOT_INITIALIZED,
    LEGACY_COMPATIBLE,
    DATABASE_KEY_ERROR,
    DATABASE_CORRUPTED,
    DATABASE_MIGRATION_ERROR,
    DATABASE_ACCESS_ERROR
}
