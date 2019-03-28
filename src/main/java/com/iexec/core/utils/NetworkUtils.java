package com.iexec.core.utils;

import com.google.common.net.InetAddresses;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkUtils {

    private NetworkUtils() {
        throw new UnsupportedOperationException();
    }

    public static String convertHostToIp(String hostname) {
        InetAddress address = null;
        try {
            address = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return address != null ? address.getHostAddress() : "";
    }

    public static boolean isIPAddress(String host) {
        return InetAddresses.isInetAddress(host);
    }
}
