package com.psdev.aka.sweep.service;

import com.psdev.aka.sweep.service.chainlisteners.SendingTransactionLock;
import com.psdev.aka.sweep.service.chainlisteners.TransactionPool;
import com.psdev.aka.sweep.service.exceptions.NodeOutOfSyncException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.psdev.aka.sweep.util.EthUtils.shortTransactionHash;
import static javax.management.timer.Timer.ONE_SECOND;

@Slf4j
@Service
public class SweepService {

    @Autowired
    Web3j web3;

    @Autowired
    @Qualifier("publicWeb3Rpc")
    Web3j publicWeb3Rpc;

    @Autowired
    AkromaApiService apiService;

    @Autowired
    TransactionPool pool;

    @Autowired
    BlockService blockService;

    @Autowired
    CredentialsService credentialsService;

    @Autowired
    SendingTransactionLock sendingTransactionLock;

    @Value("${destination.account}")
    String destintionAccount;

    @Value("${sweep.minimum}")
    String minSweepAmount;

    @Value("${akroma.masternode.deposit}")
    String depositAmount;

    @Value("${sweep.leave.remaining}")
    String remainingAmount;

    @Value("${minimum.block.height}")
    long minBlockHeight;

    @Value("${sweep.confirmations}")
    long confirmations;

    @Value("${transaction.limit.to}")
    boolean limitTransactionsTo;

    @Value("${transaction.limit.from}")
    boolean limitTransactionsFrom;

    @Autowired
    BigInteger gasPrice;

    @Autowired
    BigInteger gasLimit;

    @Scheduled(fixedDelay = ONE_SECOND*10, initialDelay=ONE_SECOND*5)
    public void doSweep() throws Exception, NodeOutOfSyncException {
        if (!blockService.hasConfimations()) {
            return;
        }

        if (limitTransactionsTo && pool.hasTransactionPendingTo(destintionAccount)) {
            log.info("Already Txn in mem-pool to " + destintionAccount);
            return;
        }

        BigInteger blockNumber = web3.ethBlockNumber().send().getBlockNumber();
        crossCheckNodeBlockHeight(blockNumber);
        BigInteger confirmedBlockHeight = blockService.getConfirmedBlockHeight();
        if (confirmedBlockHeight == null) {
            log.error("node may not be synced");
            return;
        }

        log.debug("Sweep check: nodeHeight=" + blockNumber + ", confirmed=" + confirmedBlockHeight);

        credentialsService.getCredentials().stream().forEach((Credentials credentials) ->
                sweepAccount(credentials, blockNumber, confirmedBlockHeight)
        );

    }

    private void sweepAccount(Credentials credentials, BigInteger blockNumber, BigInteger confirmedBlockHeight) {
        DefaultBlockParameter confirmedBlockNumber = DefaultBlockParameter.valueOf(confirmedBlockHeight);
        try {
            String address = credentials.getAddress();

            if (pool.hasTransactionPendingFrom(address)) {
                log.info("Already a pending Txn in mem pool from " + address);
                return;
            }

            if (limitTransactionsFrom && sendingTransactionLock.isAddressLockedByPendingConfirmationsAtHeight(
                    address, confirmedBlockHeight.add(BigInteger.ONE))) {

                BigInteger transactionBlockHeight = sendingTransactionLock.blockHeightPendingConfirmations(
                        address, confirmedBlockHeight.add(BigInteger.ONE));
                BigInteger pendingConfirmations = blockNumber.subtract(transactionBlockHeight);

                log.info("Txn on chain, pending confirmations (" + pendingConfirmations + "/" + confirmations + "), from " + address);
                return;
            }

            BigInteger balance = web3.ethGetBalance(address, confirmedBlockNumber).send().getBalance();
            BigInteger amountToSend = balance
                    .subtract(new BigInteger(depositAmount))
                    .subtract(gasLimit.multiply(gasPrice))
                    .subtract(new BigInteger(remainingAmount));

            if (overMasternodeDeposit(balance)) {

                BigInteger nonce = web3.ethGetTransactionCount(
                        address, confirmedBlockNumber).sendAsync().get().getTransactionCount();

                log.debug("Sweeping: address=" + address + " amount=" + amountToSend);

                RawTransaction rawTransaction  = RawTransaction.createEtherTransaction(
                        nonce, gasPrice, gasLimit, destintionAccount, amountToSend);

                byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
                String hexValue = Numeric.toHexString(signedMessage);

                EthSendTransaction ethSendTransaction = web3.ethSendRawTransaction(hexValue).sendAsync().get();
                log.info("Sweeping: from=" + address + " to=" + destintionAccount + " amount=" + amountToSend + " txnHash=" + shortTransactionHash(ethSendTransaction.getTransactionHash()));

                pool.newPending(ethSendTransaction.getTransactionHash(), destintionAccount, address);
                sendingTransactionLock.addTransction(address, ethSendTransaction.getTransactionHash());

            } else {
                log.debug("Address: " + address + ", Balance: " + balance);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void crossCheckNodeBlockHeight(BigInteger blockNumber) throws IOException, NodeOutOfSyncException {
        BigInteger publicWeb3RpcBlockNumber = publicWeb3Rpc.ethBlockNumber().send().getBlockNumber();

        if (outOfSync(blockNumber, Arrays.asList(publicWeb3RpcBlockNumber), confirmations*2)) {
            log.debug("Aka masternode sweep check at: block=" + blockNumber);
            log.debug("Public RPC node at: block=" + publicWeb3RpcBlockNumber);

            if (blockNumber.longValue() > publicWeb3RpcBlockNumber.longValue()) {
                log.error("out of sync.  publicWeb3Rpc behind by " +
                        (blockNumber.longValue() - publicWeb3RpcBlockNumber.longValue()));
            } else {
                log.error("out of sync.  publicWeb3Rpc behind by " +
                        (publicWeb3RpcBlockNumber.longValue() - blockNumber.longValue()));
            }
            throw new NodeOutOfSyncException();
        }
    }

    private boolean overMasternodeDeposit(BigInteger bal) {
        return bal.compareTo(new BigInteger(depositAmount).add(new BigInteger(minSweepAmount))) == 1;
    }

    private boolean outOfSync(BigInteger mainNodeHeight, List<BigInteger> referenceNodeHeights, long maxDiff) {
        if (mainNodeHeight.longValue() < minBlockHeight) {
            log.error("main node block height too low.  hieght=" + mainNodeHeight);
            return true;
        }

        BigInteger averageRefrenceHeight = BigDecimal.valueOf(referenceNodeHeights
                .stream()
                .mapToLong(a -> a.longValue())
                .filter(a -> a > minBlockHeight)
                .average().getAsDouble()).toBigInteger();

        if (mainNodeHeight.subtract(averageRefrenceHeight).longValue() > maxDiff) {
            log.error("main node diff avg ref height too big.  main=" + mainNodeHeight + " avg=" + averageRefrenceHeight);
            return true;
        }

        if (averageRefrenceHeight.subtract(mainNodeHeight).longValue() > maxDiff) {
            log.error("main node diff avg ref height too big.  main=" + mainNodeHeight + " avg=" + averageRefrenceHeight);
            return true;
        }

        return false;
    }

}
