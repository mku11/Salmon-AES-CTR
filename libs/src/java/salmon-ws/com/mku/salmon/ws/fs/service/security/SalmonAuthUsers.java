package com.mku.salmon.ws.fs.service.security;

import java.util.HashMap;

public class SalmonAuthUsers {
    private static HashMap<String,String> users = new HashMap<>();

    public static void addUser(String user, String password) throws Exception {
        if(users.containsKey(user))
            throw new Exception("User already exists, user removeUser() first");
        users.put(user, password);
    }

    public static void removeUser(String user) throws Exception {
        if(users.containsKey(user))
            throw new Exception("User does not exist");
    }

    public static void removeAllUsers() {
        users.clear();
    }

    public static HashMap<String, String> getUsers() {
        return new HashMap<>(users);
    }
}
