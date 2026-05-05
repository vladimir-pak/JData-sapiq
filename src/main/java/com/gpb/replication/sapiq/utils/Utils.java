package com.gpb.replication.sapiq.utils;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
    public static String getHash(String input, String algorithm) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            messageDigest.update(input.getBytes());
            byte[] hashBytes = messageDigest.digest();
            BigInteger number = new BigInteger(1, hashBytes);
            StringBuilder hexStr = new StringBuilder(number.toString(16));
            while (hexStr.length() < 32)
                hexStr.insert(0, '0');
            return hexStr.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    public static String getSources(MutablePropertySources propertySources) {
        StringBuilder stringBuilder = new StringBuilder();
        for (PropertySource propertySource : propertySources) {
            if (propertySource instanceof EnumerablePropertySource<?>) {
                if (!propertySource.getName().contains(".yml")
                        && !propertySource.getName().contains(".yaml")
                        && !propertySource.getName().contains(".properties"))
                    continue;
                EnumerablePropertySource<?> enumerablePropertySource = (EnumerablePropertySource<?>) propertySource;
                for (String key : enumerablePropertySource.getPropertyNames()) {
                    Object value = propertySource.getProperty(key);
                    if (value != null)
                        stringBuilder.append(key)
                                .append(":")
                                .append(value);
                    else
                        stringBuilder.append(key)
                                .append(":");
                    stringBuilder.append(";");
                }
            }
        }
        return stringBuilder.toString();
    }
}
