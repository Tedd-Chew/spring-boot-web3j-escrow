package com.wetech.demo.web3j.controller;

import com.wetech.demo.web3j.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
public class EscrowController {

    private final EscrowService escrowService;

    /**
     * 后端部署 Escrow，仲裁人是后端账户
     */
    @PostMapping("/deploy")
    public CompletableFuture<ResponseEntity<Map<String, String>>> deploy() {
        return escrowService.deploy().thenApply(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("contractAddress", c.getContractAddress());
            m.put("transactionHash", c.getTransactionReceipt()
                    .map(TransactionReceipt::getTransactionHash).orElse(""));
            m.put("deployBlock", c.getTransactionReceipt()
                    .map(r -> r.getBlockNumber().toString()).orElse("0"));
            return ResponseEntity.ok(m);
        });
    }

    /**
     * 加载已部署的 Escrow，fromBlock 传部署区块
     */
    @PostMapping("/load")
    public ResponseEntity<Map<String, String>> load(@RequestParam String address,
                                                    @RequestParam String fromBlock) {
        escrowService.load(address, new BigInteger(fromBlock));
        Map<String, String> m = new LinkedHashMap<>();
        m.put("contractAddress", address);
        m.put("fromBlock", fromBlock);
        return ResponseEntity.ok(m);
    }

    /**
     * 返回合约地址和仲裁人地址
     */
    @GetMapping("/address")
    public CompletableFuture<ResponseEntity<Map<String, String>>> address() {
        return escrowService.getArbiter().thenApply(arbiter -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("contractAddress", escrowService.getContractAddress());
            m.put("arbiter", arbiter);
            return ResponseEntity.ok(m);
        });
    }

    /**
     * 查单个订单
     */
    @GetMapping("/orders/{id}")
    public CompletableFuture<ResponseEntity<Map<String, String>>> order(@PathVariable String id) {
        return escrowService.getOrder(new BigInteger(id)).thenApply(ResponseEntity::ok);
    }

    /**
     * 订单列表，从 OrderCreated 事件重建
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Map<String, String>>> orders() throws Exception {
        return ResponseEntity.ok(escrowService.listOrders());
    }

    /**
     * 仲裁。交易 revert 时把原因返回给调用方
     */
    @PostMapping("/orders/{id}/resolve")
    public CompletableFuture<ResponseEntity<Map<String, String>>> resolve(
            @PathVariable String id, @RequestParam boolean sellerWins) {
        return escrowService.resolve(new BigInteger(id), sellerWins)
                .thenApply(receipt -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("transactionHash", receipt.getTransactionHash());
                    m.put("blockNumber", receipt.getBlockNumber().toString());
                    m.put("status", receipt.getStatus());
                    return ResponseEntity.ok(m);
                })
                .exceptionally(ex -> {
                    Throwable root = ex;
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    log.error("resolve failed: {}", root.getMessage());
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("error", root.getClass().getName());
                    m.put("message", root.getMessage());
                    return ResponseEntity.internalServerError().body(m);
                });
    }
}
