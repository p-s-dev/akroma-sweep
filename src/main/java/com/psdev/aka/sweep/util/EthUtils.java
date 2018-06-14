package com.psdev.aka.sweep.util;

public class EthUtils {
    public static synchronized String shortTransactionHash(String fullHash) {
        int strlen = fullHash.length();
        return fullHash.substring(0,6) + "." + fullHash.substring(strlen-2,strlen);
    }
}
