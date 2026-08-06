package com.securecardvault.utility;

import java.util.Random;

public class TokenGenerator {
    public static String generateToken() {
        Random random = new Random();
        int number = 100000000 + random.nextInt(900000000);
        return "TKN-" + number;
    }
}
