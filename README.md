# Escrow 托管交易平台后端（路线 A）

基于 Spring Boot + Web3j 的托管交易平台后端。后端账户作为仲裁人，通过 REST 接口
部署 Escrow 合约、查询订单列表、执行仲裁。

## 环境要求

- JDK 21
- 项目自带 gradlew，不需要单独安装 Gradle
- 能访问 POTOS 测试网 RPC `https://rpc-testnet.potos.hk`

## 环境变量

启动前必须导出后端账户（仲裁人）的私钥：

```bash
export WEB3J_PRIVATE_KEY=0x你的私钥
```

私钥只通过环境变量注入，仓库中不保存任何私钥。`application.properties` 里写的是
占位符 `${WEB3J_PRIVATE_KEY}`。

## 运行

```bash
bash gradlew bootRun
```

服务监听 8080。

## 接口

| 方法 | 路径 | 参数 | 返回 |
| --- | --- | --- | --- |
| POST | /api/escrow/deploy | 无 | contractAddress、transactionHash、deployBlock |
| POST | /api/escrow/load | address、fromBlock | contractAddress |
| GET | /api/escrow/address | 无 | contractAddress、arbiter |
| GET | /api/escrow/orders/{id} | 无 | buyer、seller、token、amount、deadline、status、description |
| GET | /api/escrow/orders | 无 | 数组，每项 id、buyer、seller、amount、blockNumber、transactionHash |
| POST | /api/escrow/orders/{id}/resolve | sellerWins | transactionHash、blockNumber、status |

订单列表从 OrderCreated 事件日志重建。POTOS 的 `eth_getLogs` 一次只扫 10 个区块，
所以按 10 个区块一段分批查询后拼接。

## 合约地址（POTOS 测试网）

| 项目 | 地址 |
| --- | --- |
| Escrow 合约 | 0x38Ba1A516baB7a6120B532c26B4DAB121782444E |
| 代币 SQQToken | 0x09629CF61F03E2246F39033036A0348e22F6e0db |
| 后端账户（仲裁人） | 0xc570e436A6574DB2c62cdef24286da53AA050c90 |

## 交易记录

| 操作 | 交易哈希 | 区块浏览器链接 | 发起账户 |
| --- | --- | --- | --- |
| 部署 Escrow | 0x229a59df4df140951dd42c988234467eefe140b8fe1b2e1294ba533e018887aa | https://scan-testnet.potos.hk/tx/0x229a59df4df140951dd42c988234467eefe140b8fe1b2e1294ba533e018887aa | 0xc570e436A6574DB2c62cdef24286da53AA050c90 |
| createOrder 订单 0 | 0x982793cc55c3a6f99123a2256c80eb97e99e02ec99a29a1ddf2a15c0a5fe03a8 | https://scan-testnet.potos.hk/tx/0x982793cc55c3a6f99123a2256c80eb97e99e02ec99a29a1ddf2a15c0a5fe03a8 | 0xEb660ABb5f0E257ba8Ba552a467fEd7679Fd002A |
| approve | 0x19be2a7f8fa5d280840d105a08e17d7ea6d093ec1cb23d988e9726293b2ca634 | https://scan-testnet.potos.hk/tx/0x19be2a7f8fa5d280840d105a08e17d7ea6d093ec1cb23d988e9726293b2ca634 | 0xEb660ABb5f0E257ba8Ba552a467fEd7679Fd002A |
| deposit 订单 0 | 0x0f28de2701136a795f2201295414cc5113a9d055c5214865121158e80bd5df00 | https://scan-testnet.potos.hk/tx/0x0f28de2701136a795f2201295414cc5113a9d055c5214865121158e80bd5df00 | 0xEb660ABb5f0E257ba8Ba552a467fEd7679Fd002A |
| dispute 订单 1 | 0x1655abb761794f84d608785952d429d7512bad4931117c86b1ae44698ea5d65e | https://scan-testnet.potos.hk/tx/0x1655abb761794f84d608785952d429d7512bad4931117c86b1ae44698ea5d65e | 0x9Db8Ff858c67B5c5A20842A941E928BAb5bC43ad |
| resolve 订单 1 | 0x233191f9755ada158e633b5a45f3d651396307c5b66fac7990b4b4b3284c4403 | https://scan-testnet.potos.hk/tx/0x233191f9755ada158e633b5a45f3d651396307c5b66fac7990b4b4b3284c4403 | 0xc570e436A6574DB2c62cdef24286da53AA050c90 |
| confirm 订单 0 | 0x1103815e84136e30b02aa266fc4d2008be4a0b6feacef35036d7e7ce6a010e33 | https://scan-testnet.potos.hk/tx/0x1103815e84136e30b02aa266fc4d2008be4a0b6feacef35036d7e7ce6a010e33 | 0xEb660ABb5f0E257ba8Ba552a467fEd7679Fd002A |
| resolve 失败（路线 A） | 0xacf9abb1aeca7715ece585218da01582fd67c9cefe610f2f52595a0341d81ff0 | https://scan-testnet.potos.hk/tx/0xacf9abb1aeca7715ece585218da01582fd67c9cefe610f2f52595a0341d81ff0 | 0xc570e436A6574DB2c62cdef24286da53AA050c90 |

## 关于私钥

仓库中出现的 0x 加 64 位十六进制字符串都是 POTOS 测试网的交易哈希，可以在
https://scan-testnet.potos.hk 查到对应交易。私钥只通过环境变量 `WEB3J_PRIVATE_KEY`
注入，不保存在仓库的任何文件中。

## 目录说明

- `src/main/resources/contracts/`：Escrow 与 SQQToken 的 Solidity 源码
- `src/main/java/com/wetech/demo/web3j/contracts/`：由 `generateContractWrappers` 生成的 Java 封装
- `src/main/java/com/wetech/demo/web3j/service/EscrowService.java`：合约调用与事件查询
- `src/main/java/com/wetech/demo/web3j/controller/EscrowController.java`：REST 接口
- `src/main/java/com/wetech/demo/web3j/config/Web3jConfig.java`：Web3j、Credentials、gas 配置

修改 `.sol` 后需要重新生成封装：

```bash
bash gradlew generateContractWrappers
```
