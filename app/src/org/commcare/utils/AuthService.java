package org.commcare.utils;

public interface AuthService {
    void sendOTP(String phoneNumber);
    void verifyOTP(String code);
}

