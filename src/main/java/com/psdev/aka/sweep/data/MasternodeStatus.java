package com.psdev.aka.sweep.data;

import lombok.Data;

import java.time.Duration;
import java.util.Date;

import static org.web3j.crypto.Keys.toChecksumAddress;

@Data
public class MasternodeStatus {

    /**
     * https://akroma.io/api/masternodes/public formats address however it was first saved from web-form
     * standard needed for use as key in map
     * @param address
     */
    public void setAddress(String address) {
        this.address = toChecksumAddress(address);
    }

    String address;
    String status;
    Date lastSeenAt;
    Duration lastCheckDuration;
    long successiveConnectionCount;
}