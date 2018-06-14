package com.psdev.aka.sweep.service.chainlisteners;

import com.psdev.aka.sweep.service.BlockService;
import com.psdev.aka.sweep.service.CredentialsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.Transaction;
import rx.Observable;
import rx.Subscription;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static javax.management.timer.Timer.ONE_MINUTE;
import static javax.management.timer.Timer.ONE_SECOND;

@Slf4j
@Service
public class Web3ObservableSubscriptionService {

    @Autowired
    Web3j web3;

    @Autowired
    TransactionPool pool;

    @Autowired
    SendingTransactionLock sendingTransactionLock;

    @Autowired
    CredentialsService credentialsService;

    @Autowired
    BlockService blockService;

    @Value("${akroma.masternode.reward-disbursing.address}")
    String masternodeRewardSenderAddress;

    Map<String, Long> durationInMemPool = new HashMap();


    Subscription pendingTransactionSubscription;
    Subscription transactionsInBlocksSubscription;
    Subscription newBlocksSubscription;

    @EventListener(ApplicationReadyEvent.class)
    public void loadPendingTransactionListener() throws IOException {

        pendingTransactionSubscription = pendingTransactions(web3).subscribe(tx -> {
            log.debug("add txn:  poolsize=" + pool.poolSize());
            pool.newPending(tx);

            if (credentialsService.containsAddress(tx.getFrom())) {
                sendingTransactionLock.addTransction(tx.getFrom(), tx.getHash());
            }

            if (credentialsService.containsAddress(tx.getTo()) &&
                    masternodeRewardSenderAddress.equals(tx.getFrom())) {
                log.info("masternode payment in mem pool.  address=" + tx.getTo());
                durationInMemPool.put(tx.getTo(), new Date().getTime());
            }

        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadTransactionInBlockListener() throws IOException {
        transactionsInBlocksSubscription = blockSealedTransactions(web3).subscribe(tx -> {
            log.debug("remove txn:  poolsize=" + pool.poolSize());
            pool.sawInBlock(tx);

            if (credentialsService.containsAddress(tx.getFrom())) {
                sendingTransactionLock.addTransction(tx.getFrom(), tx.getHash());
            }

            if (credentialsService.containsAddress(tx.getTo()) &&
                    masternodeRewardSenderAddress.equals(tx.getFrom())) {
                log.info("masternode payment in block.  address=" + tx.getTo() +
                        " mempool duration: " + Duration.ofMillis(
                        new Date().getTime() - durationInMemPool.get(tx.getTo())));
                //TODO archive date payment was sent.  assumes final health check done right before payment.
            }

        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadBlockListener() {
        newBlocksSubscription = web3.blockObservable(false).subscribe(block -> {
            log.debug("block=" + block.getBlock().getNumber().toString() +
                    " txn_count=" + block.getBlock().getTransactions().size());
            blockService.handleBlock(block.getBlock());
        });
    }

    @PreDestroy
    public void cleanup() {
        pendingTransactionSubscription.unsubscribe();
        log.info("pendingTransactionSubscription.isUnsubscribed: " + pendingTransactionSubscription.isUnsubscribed());
        newBlocksSubscription.unsubscribe();
        log.info("newBlocksSubscription.isUnsubscribed: " + newBlocksSubscription.isUnsubscribed());
        transactionsInBlocksSubscription.unsubscribe();
        log.info("transactionsInBlocksSubscription.isUnsubscribed: " + transactionsInBlocksSubscription.isUnsubscribed());
    }

    @Scheduled(fixedDelay = ONE_MINUTE, initialDelay=ONE_SECOND * 20)
    public void checkSubscriptionsActive() throws IOException {
        if(pendingTransactionSubscription.isUnsubscribed()) {
            log.warn("pendingTransactionSubscription unsubscribed, reloading.");
            loadPendingTransactionListener();
        }

        if(newBlocksSubscription.isUnsubscribed()) {
            log.warn("newBlocksSubscription unsubscribed, reloading.");
            loadTransactionInBlockListener();
        }

        if(transactionsInBlocksSubscription.isUnsubscribed()) {
            log.warn("transactionsInBlocksSubscription unsubscribed, reloading.");
            loadBlockListener();
        }

    }


    private DefaultBlockParameter currentBlock() throws IOException {
        return DefaultBlockParameter.valueOf(web3.ethBlockNumber().send().getBlockNumber());
    }

    private Observable<Transaction> pendingTransactions(Web3j web3) {
        return web3.pendingTransactionObservable();
    }

    private Observable<Transaction> blockSealedTransactions(Web3j web3) throws IOException {
        return web3.catchUpToLatestAndSubscribeToNewTransactionsObservable(currentBlock());
    }

}
