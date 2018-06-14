package com.psdev.aka.sweep.service;

import com.psdev.aka.sweep.service.chainlisteners.TransactionPool;
import com.psdev.aka.sweep.service.exceptions.BlockParentLinkUnfoundException;
import com.psdev.aka.sweep.service.exceptions.FoundException;
import com.psdev.aka.sweep.util.AutoDiscardingDeque;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlock;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.psdev.aka.sweep.util.EthUtils.shortTransactionHash;

@Service
@Slf4j
public class BlockService {

    @Autowired
    TransactionPool pool;

    @Autowired
    Web3j web3;

    @Value("${sweep.confirmations}")
    int confirmations;

    @Value("${minimum.block.height}")
    long minNodeBlockHeight;

    @Value("${max.same.author.blocks}")
    long maxCountSameAuthorSequentialBlocks;

    private long blocksSeen = 0l;
    private long sequentialBlocksBySameMiner = 0l;

    private BigInteger blockHeight;
    private AutoDiscardingDeque<EthBlock.Block> lastBlocks;

    /**
     * All seen blocks are wrongly counted as canonical.
     *
     * debug logs when blocks emmitted that don't chain from previous.
     *
     * When the parent block is not the previous block seen, some tracing back should be done, to find the
     * previous block which does match the parent.
     *
     * @param b
     */
    synchronized public void handleBlock(EthBlock.Block b) {

        if (!hasConfimations()) {
            log.info("pre-confirmed-block: height=" + b.getNumber() +
                    " confirmations=" + blocksSeen + "/" + confirmations +
                    " hash=" + shortTransactionHash(b.getHash()) +
                    " parent-hash=" + shortTransactionHash(b.getParentHash())
            );
        } else {
            log.info("new-block: height=" + b.getNumber() +
                    " hash=" + shortTransactionHash(b.getHash()) +
                    " parent-hash=" + shortTransactionHash(b.getParentHash())
            );
        }

        if (lastBlocks.isEmpty()) {
            lastBlocks.offerFirst(b);
            return;
        }

        EthBlock.Block previousBlock = null;
        List<EthBlock.Block> forkBlocks = new ArrayList<>();
        AtomicInteger cnt = new AtomicInteger(0);
        int blockCounter = 0;

        try {
            while (previousBlock == null) {
                if (b == null) {
                    throw new BlockParentLinkUnfoundException();
                }
                previousBlock = matchParentHash(cnt, b);
                if (previousBlock == null) {
                    blockCounter++;
                    forkBlocks.add(b);
                    if (blockCounter >= confirmations/2) {
                        throw new BlockParentLinkUnfoundException();
                    }
                    b = web3.ethGetBlockByHash(b.getParentHash(), false).send().getBlock();
                }
            }

            if (!forkBlocks.isEmpty()) {
                Collections.reverse(forkBlocks);
                forkBlocks.forEach((EthBlock.Block block) -> {
                    log.info("fork-block: height=" + block.getNumber() +
                            " hash=" + shortTransactionHash(block.getHash()) +
                            " parent=" + shortTransactionHash(block.getParentHash()));

                    lastBlocks.offerFirst(block);
                });

                log.info("Fork resolved successfully: block parent linked to chain at height=" + previousBlock.getNumber() + " fork-depth=" + forkBlocks.size());
            }


        } catch (BlockParentLinkUnfoundException bplue) {
            //TODO report about forkBlocks

            log.error("block contains parent hash with unknown link to chain.");
            log.error("havent seen block with matching parent.  throwing away block. BlockHash=" + b.getHash() +
                    " BlockHeight=" + b.getNumber());
            log.error("ParentBlockHash=" + b.getParentHash());

            forkBlocks.forEach((EthBlock.Block block) -> {
                log.error("track-back-block: height=" + block.getNumber() +
                        " hash=" + shortTransactionHash(block.getHash()) +
                        " parent=" + shortTransactionHash(block.getParentHash()));
            });

            startover();

            return;
        } catch (IOException e) {
            e.printStackTrace();
            log.error("error looking up parent block by hash");
            startover();
            return;

        }

        blockHeight = previousBlock.getNumber();
        blocksSeen++;

        lastBlocks.offerFirst(b);
    }

    /**
     * The app makes decisions to send txn at the confirmed block height
     * @return
     */
    public BigInteger getConfirmedBlockHeight() {
        if (hasMinimimChainHeightRequirements(blockHeight)) {
            return null;
        }
        return blockHeight.subtract(BigInteger.valueOf(confirmations));
    }

    public boolean hasConfimations() {
        return blocksSeen >= confirmations;
    }

    private void startover() {
        pool.clear();
        blocksSeen = 0;
        lastBlocks.clear();
    }

    private EthBlock.Block matchParentHash(AtomicInteger cnt, EthBlock.Block b) {
        EthBlock.Block previousBlock = null;
        try {
            lastBlocks.iterator().forEachRemaining((EthBlock.Block historyBlock) -> {
                cnt.incrementAndGet();
                if (StringUtils.equals(b.getParentHash(), historyBlock.getHash())) {
                    throw new FoundException(historyBlock);
                }
            });

        } catch (FoundException f) {
            previousBlock = f.getBlock();
        }

        return previousBlock;

    }

    /**
     * @param lastBlock
     * @param block
     * @return
     */
    private boolean blockInOrder(EthBlock.Block lastBlock, EthBlock.Block block) {
        if (StringUtils.equals(block.getMiner(), lastBlock.getMiner())) {
            sequentialBlocksBySameMiner++;
        } else {
            sequentialBlocksBySameMiner=0;
        }

        if (sequentialBlocksBySameMiner > maxCountSameAuthorSequentialBlocks) {
            log.warn("more than " + maxCountSameAuthorSequentialBlocks +
                    " blocks in a row mined by same author=" + block.getMiner());
            sequentialBlocksBySameMiner=0;
            return false;
        }

        return true;

    }

    private boolean hasMinimimChainHeightRequirements(BigInteger height) {
        return blockHeight == null ||
                BigInteger.valueOf(minNodeBlockHeight).compareTo(height) == 1 ||
                blocksSeen < confirmations;
    }

    @PostConstruct
    private void init() {
        lastBlocks = new AutoDiscardingDeque(confirmations);
    }


}
