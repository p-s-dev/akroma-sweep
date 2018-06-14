package com.psdev.aka.sweep.service.chainlisteners;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Transaction;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SendingTransactionLock {
    @Autowired
    Web3j web3;

    private Map<String, String> transactionsWaitingForConfirmation = new ConcurrentHashMap();

    public void addTransction(String fromAddress, String transactionHash) {
        transactionsWaitingForConfirmation.put(fromAddress, transactionHash);
    }

    public boolean isAddressLockedByPendingConfirmationsAtHeight(String address, BigInteger confirmedBlockHeight) {
        if (!containsAddress(address)) {
            return false;
        }

        Transaction t = null;
        try {
            t = web3.ethGetTransactionByHash(transactionsWaitingForConfirmation.get(address)).send().getTransaction().get();

            // if transaction is older than the confirmation depth
            if (t.getBlockNumber().compareTo(confirmedBlockHeight) == -1) {
                log.info("transaction reached chain.  fromAccounnt=" + t.getFrom());
                transactionsWaitingForConfirmation.remove(address);
                return false;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    public BigInteger blockHeightPendingConfirmations(String address, BigInteger defaultBlockHeight) {
        if (!containsAddress(address)) {
            return defaultBlockHeight;
        }
        try {
            return web3.ethGetTransactionByHash(transactionsWaitingForConfirmation.get(address)).send().getTransaction().get().getBlockNumber();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return defaultBlockHeight;

    }

    public boolean containsAddress(String address) {
        return transactionsWaitingForConfirmation.keySet().contains(address);
    }

}
