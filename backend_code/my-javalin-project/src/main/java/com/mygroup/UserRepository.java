package com.mygroup;

public interface UserRepository {
    AuthResult register(String username, String password, String phoneNumber);
    AuthResult login(String phoneNumber, String password);
    void logout(String phoneNumber);
}
