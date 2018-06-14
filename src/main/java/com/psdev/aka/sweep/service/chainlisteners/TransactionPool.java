package com.psdev.aka.sweep.service.chainlisteners;

import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.Transaction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TransactionPool {

    Map<String, Transaction> pool = new ConcurrentHashMap();

    public void newPending(Transaction txn) {
        pool.put(txn.getHash(), txn);
    }

    public void newPending(String hash, String to, String from) {
        pool.put(hash, new Transaction(hash, null, null, null, null, from, to, null, null, null, null, null, null, null, null, null, 0));
    }

    public void sawInBlock(Transaction txn) {
        pool.remove(txn.getHash());
    }

    public int poolSize() {
        return pool.size();
    }

    public boolean hasTransactionPendingTo(String address) {
        List<String> toAddresses = pool.values().stream().map(Transaction::getTo).collect(Collectors.toList());
        return toAddresses.contains(address);
    }

    public boolean hasTransactionPendingFrom(String address) {
        List<String> toAddresses = pool.values().stream().map(Transaction::getFrom).collect(Collectors.toList());
        return toAddresses.contains(address);
    }

    public void clear() {
        pool.clear();
    }

}
