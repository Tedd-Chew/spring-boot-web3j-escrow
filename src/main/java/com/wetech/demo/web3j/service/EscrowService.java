package com.wetech.demo.web3j.service;

import com.wetech.demo.web3j.contracts.escrow.Escrow;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowService {

    private static final String[] STATUS = {"Created", "Paid", "Completed", "Refunded", "Disputed"};

    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider gasProvider;

    private Escrow contract;

    @Getter
    private String contractAddress;

    @Getter
    private BigInteger deployBlock;

    /**
     * 部署一份 Escrow，仲裁人就是后端账户自己
     */
    public CompletableFuture<Escrow> deploy() {
        log.info("Deploying Escrow with arbiter {}", credentials.getAddress());
        return Escrow.deploy(web3j, credentials, gasProvider, credentials.getAddress())
                .sendAsync()
                .thenApply(c -> {
                    this.contract = c;
                    this.contractAddress = c.getContractAddress();
                    this.deployBlock = c.getTransactionReceipt()
                            .map(TransactionReceipt::getBlockNumber)
                            .orElse(BigInteger.ZERO);
                    log.info("Escrow deployed at {} (block {})", contractAddress, deployBlock);
                    return c;
                });
    }

    /**
     * 加载一个已部署的 Escrow，fromBlock 用来后面扫事件日志
     */
    public void load(String address, BigInteger fromBlock) {
        this.contract = Escrow.load(address, web3j, credentials, gasProvider);
        this.contractAddress = address;
        this.deployBlock = fromBlock;
        log.info("Escrow loaded at {} from block {}", address, fromBlock);
    }

    public CompletableFuture<String> getArbiter() {
        requireContract();
        return contract.arbiter().sendAsync();
    }

    /**
     * 查单个订单，直接读合约状态
     */
    public CompletableFuture<Map<String, String>> getOrder(BigInteger id) {
        requireContract();
        return contract.getOrder(id).sendAsync().thenApply(o -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("buyer", o.buyer);
            m.put("seller", o.seller);
            m.put("token", o.token);
            m.put("amount", o.amount.toString());
            m.put("deadline", o.deadline.toString());
            m.put("status", STATUS[o.status.intValue()]);
            m.put("description", o.description);
            return m;
        });
    }

    /**
     * 从 OrderCreated 事件日志重建订单列表。
     *
     * POTOS 测试网的 eth_getLogs 一次只扫 fromBlock 起的 10 个区块，
     * 超出部分静默丢弃，所以必须按 10 个区块一段分批查再拼起来。
     */
    public List<Map<String, String>> listOrders() throws Exception {
        requireContract();
        BigInteger latest = web3j.ethBlockNumber().send().getBlockNumber();
        BigInteger step = BigInteger.TEN;
        List<Log> logs = new ArrayList<>();
        for (BigInteger from = deployBlock; from.compareTo(latest) <= 0; from = from.add(step)) {
            BigInteger to = from.add(step).subtract(BigInteger.ONE).min(latest);
            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(from),
                    DefaultBlockParameter.valueOf(to),
                    contractAddress);
            filter.addSingleTopic(EventEncoder.encode(Escrow.ORDERCREATED_EVENT));
            web3j.ethGetLogs(filter).send().getLogs().forEach(r -> logs.add((Log) r.get()));
        }
        return logs.stream().map(l -> {
            Escrow.OrderCreatedEventResponse e = Escrow.getOrderCreatedEventFromLog(l);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", e.id.toString());
            m.put("buyer", e.buyer);
            m.put("seller", e.seller);
            m.put("amount", e.amount.toString());
            m.put("blockNumber", l.getBlockNumber().toString());
            m.put("transactionHash", l.getTransactionHash());
            return m;
        }).toList();
    }

    /**
     * 后端用仲裁人私钥签名，对订单发起裁决
     */
    public CompletableFuture<TransactionReceipt> resolve(BigInteger id, boolean sellerWins) {
        requireContract();
        log.info("Resolving order {} sellerWins={}", id, sellerWins);
        return contract.resolve(id, sellerWins).sendAsync();
    }

    private void requireContract() {
        if (contract == null) {
            throw new IllegalStateException(
                    "Contract not deployed or loaded, call /api/escrow/deploy or /api/escrow/load first");
        }
    }
}
