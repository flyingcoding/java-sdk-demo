/**
 * Copyright 2014-2020 [fisco-dev]
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fisco.bcos.sdk.demo.perf;

import com.google.common.util.concurrent.RateLimiter;
import java.math.BigInteger;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.demo.contract.ParallelOk;
import org.fisco.bcos.sdk.demo.perf.callback.ParallelOkCallback;
import org.fisco.bcos.sdk.demo.perf.collector.PerformanceCollector;
import org.fisco.bcos.sdk.demo.perf.model.DagTransferUser;
import org.fisco.bcos.sdk.demo.perf.model.DagUserInfo;
import org.fisco.bcos.sdk.model.ConstantConfig;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.fisco.bcos.sdk.utils.ThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 自动化并行转账合约压测工具
 * 功能：自动生成用户信息，一键完成并行转账压测
 * 使用方法：java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.AutoParallelOkPerf [groupId] [userCount] [transferCount] [tps]
 */
public class AutoParallelOkPerf {
    private static final Logger logger = LoggerFactory.getLogger(AutoParallelOkPerf.class);
    private static AtomicInteger addUserSended = new AtomicInteger(0);
    private static AtomicInteger transferSended = new AtomicInteger(0);

    /**
     * 打印使用说明
     */
    private static void Usage() {
        System.out.println("===== 自动化并行转账压测工具 =====");
        System.out.println(" 功能：自动生成用户、部署合约、添加账户并执行转账压测");
        System.out.println(" 使用方法:");
        System.out.println(
                " \t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.AutoParallelOkPerf [groupId] [userCount] [transferCount] [tps]");
        System.out.println(" 参数说明:");
        System.out.println(" \t groupId        : 群组ID（如：1）");
        System.out.println(" \t userCount      : 生成的用户数量（如：1000，建议100-10000）");
        System.out.println(" \t transferCount  : 转账交易总数（如：10000）");
        System.out.println(" \t tps            : 目标TPS/QPS（如：100）");
        System.out.println();
        System.out.println(" 示例:");
        System.out.println(
                " \t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.AutoParallelOkPerf 1 1000 10000 100");
    }

    public static void main(String[] args) {
        try {
            // 加载配置文件
            String configFileName = ConstantConfig.CONFIG_FILE_NAME;
            URL configUrl = AutoParallelOkPerf.class.getClassLoader().getResource(configFileName);

            if (configUrl == null) {
                System.out.println("配置文件 " + configFileName + " 不存在！");
                return;
            }

            // 检查参数
            if (args.length < 4) {
                Usage();
                return;
            }

            // 解析参数
            Integer groupId = Integer.valueOf(args[0]);
            Integer userCount = Integer.valueOf(args[1]);
            Integer transferCount = Integer.valueOf(args[2]);
            Integer tps = Integer.valueOf(args[3]);

            System.out.println("====================================================================");
            System.out.println("========== 自动化并行转账压测开始 ==========");
            System.out.println("====================================================================");
            System.out.println("配置信息:");
            System.out.println("  群组ID        : " + groupId);
            System.out.println("  用户数量      : " + userCount);
            System.out.println("  转账交易总数  : " + transferCount);
            System.out.println("  目标TPS       : " + tps);
            System.out.println("====================================================================");

            // 初始化SDK和客户端
            String configFile = configUrl.getPath();
            BcosSDK sdk = BcosSDK.build(configFile);
            Client client = sdk.getClient(groupId);

            // 创建线程池
            ThreadPoolService threadPoolService =
                    new ThreadPoolService(
                            "AutoParallelOkPerf",
                            sdk.getConfig().getThreadPoolConfig().getMaxBlockingQueueSize());

            // 步骤1：部署并行合约
            System.out.println();
            System.out.println("【步骤 1/4】部署并行转账合约...");
            ParallelOk parallelOk =
                    ParallelOk.deploy(client, client.getCryptoSuite().getCryptoKeyPair());
            parallelOk.enableParallel();
            System.out.println("  ✓ 合约部署成功");
            System.out.println("  ✓ 合约地址: " + parallelOk.getContractAddress());

            // 步骤2：自动生成用户并添加到合约
            System.out.println();
            System.out.println("【步骤 2/4】自动生成用户并添加到合约...");
            System.out.println("  生成 " + userCount + " 个用户，初始余额: 1000000000");
            
            DagUserInfo dagUserInfo = new DagUserInfo();
            addUsers(parallelOk, dagUserInfo, userCount, tps, threadPoolService);
            
            System.out.println("  ✓ 用户添加完成，共 " + dagUserInfo.getUserList().size() + " 个用户");

            // 步骤3：查询账户信息
            System.out.println();
            System.out.println("【步骤 3/4】查询账户信息...");
            queryAccounts(parallelOk, dagUserInfo, tps, threadPoolService);
            System.out.println("  ✓ 账户信息查询完成");

            // 步骤4：执行转账压测
            System.out.println();
            System.out.println("【步骤 4/4】执行转账压测...");
            System.out.println("  发送 " + transferCount + " 笔转账交易，目标TPS: " + tps);
            
            executeTransferTest(
                    parallelOk, dagUserInfo, transferCount, tps, threadPoolService);

            // 验证结果
            System.out.println();
            System.out.println("【验证】验证转账结果...");
            verifyTransferData(parallelOk, dagUserInfo, tps, threadPoolService);

            // 完成
            System.out.println();
            System.out.println("====================================================================");
            System.out.println("========== 自动化并行转账压测完成 ==========");
            System.out.println("====================================================================");
            
            threadPoolService.stop();
            System.exit(0);

        } catch (Exception e) {
            System.out.println("压测失败，错误信息: " + e.getMessage());
            logger.error("AutoParallelOkPerf failed: ", e);
            System.exit(1);
        }
    }

    /**
     * 自动生成用户并添加到合约
     */
    private static void addUsers(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            Integer userCount,
            Integer tps,
            ThreadPoolService threadPoolService)
            throws InterruptedException {

        PerformanceCollector collector = new PerformanceCollector();
        collector.setTotal(userCount);
        long startTime = System.currentTimeMillis();
        collector.setStartTimestamp(startTime);

        RateLimiter limiter = RateLimiter.create(tps);
        Integer area = userCount / 10;
        long currentSeconds = System.currentTimeMillis() / 1000L;
        AtomicInteger sendFailed = new AtomicInteger(0);

        for (Integer i = 0; i < userCount; i++) {
            final Integer index = i;
            limiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // 自动生成用户名（基于时间戳+索引）
                                    String user =
                                            Long.toHexString(currentSeconds)
                                                    + "_"
                                                    + Integer.toHexString(index);
                                    BigInteger amount = new BigInteger("1000000000");
                                    
                                    DagTransferUser dtu = new DagTransferUser();
                                    dtu.setUser(user);
                                    dtu.setAmount(amount);

                                    ParallelOkCallback callback =
                                            new ParallelOkCallback(
                                                    collector,
                                                    dagUserInfo,
                                                    ParallelOkCallback.ADD_USER_CALLBACK);
                                    callback.setTimeout(0);
                                    callback.setUser(dtu);

                                    try {
                                        callback.recordStartTime();
                                        parallelOk.set(user, amount, callback);
                                        int current = addUserSended.incrementAndGet();

                                        if (current >= area && ((current % area) == 0)) {
                                            long elapsed = System.currentTimeMillis() - startTime;
                                            double sendSpeed = current / ((double) elapsed / 1000);
                                            System.out.println(
                                                    "  已发送: "
                                                            + current
                                                            + "/"
                                                            + userCount
                                                            + " 笔添加用户交易，发送速度="
                                                            + String.format("%.2f", sendSpeed)
                                                            + " TPS");
                                        }
                                    } catch (Exception e) {
                                        logger.warn("添加用户失败: {}", e.getMessage());
                                        sendFailed.incrementAndGet();
                                        TransactionReceipt receipt = new TransactionReceipt();
                                        receipt.setStatus("-1");
                                        receipt.setMessage("添加用户失败: " + e.getMessage());
                                        callback.onResponse(receipt);
                                    }
                                }
                            });
        }

        // 等待所有添加用户交易完成
        while (collector.getReceived().intValue() != userCount) {
            Thread.sleep(100);
        }
    }

    /**
     * 查询所有账户信息
     */
    private static void queryAccounts(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            Integer tps,
            ThreadPoolService threadPoolService)
            throws InterruptedException {

        RateLimiter rateLimiter = RateLimiter.create(tps);
        AtomicInteger queried = new AtomicInteger(0);
        int totalUsers = dagUserInfo.getUserList().size();

        for (int i = 0; i < totalUsers; i++) {
            final int index = i;
            rateLimiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        DagTransferUser user = dagUserInfo.getUserList().get(index);
                                        BigInteger balance = parallelOk.balanceOf(user.getUser());
                                        user.setAmount(balance);
                                        int count = queried.incrementAndGet();
                                        if (count % 100 == 0 || count == totalUsers) {
                                            System.out.println(
                                                    "  已查询: " + count + "/" + totalUsers + " 个账户");
                                        }
                                    } catch (ContractException e) {
                                        logger.error("查询账户失败: {}", e.getMessage());
                                    }
                                }
                            });
        }

        while (queried.get() < totalUsers) {
            Thread.sleep(50);
        }
    }

    /**
     * 执行转账压测
     */
    private static void executeTransferTest(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            Integer transferCount,
            Integer tps,
            ThreadPoolService threadPoolService)
            throws InterruptedException {

        PerformanceCollector collector = new PerformanceCollector();
        collector.setTotal(transferCount);
        long startTime = System.currentTimeMillis();
        collector.setStartTimestamp(startTime);

        RateLimiter limiter = RateLimiter.create(tps);
        Integer area = transferCount / 10;
        AtomicInteger sendFailed = new AtomicInteger(0);

        for (Integer i = 0; i < transferCount; i++) {
            final Integer index = i;
            limiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Random random = new Random();
                                        int r = random.nextInt(100) + 1; // 1-100之间的随机数
                                        BigInteger amount = BigInteger.valueOf(r);

                                        ParallelOkCallback callback =
                                                new ParallelOkCallback(
                                                        collector,
                                                        dagUserInfo,
                                                        ParallelOkCallback.TRANS_CALLBACK);
                                        callback.setTimeout(0);

                                        DagTransferUser from = dagUserInfo.getFrom(index);
                                        DagTransferUser to = dagUserInfo.getTo(index);

                                        callback.setFromUser(from);
                                        callback.setToUser(to);
                                        callback.setAmount(amount);
                                        callback.recordStartTime();

                                        parallelOk.transfer(
                                                from.getUser(), to.getUser(), amount, callback);

                                        int current = transferSended.incrementAndGet();
                                        if (current >= area && ((current % area) == 0)) {
                                            long elapsed = System.currentTimeMillis() - startTime;
                                            double sendSpeed = current / ((double) elapsed / 1000);
                                            System.out.println(
                                                    "  已发送: "
                                                            + current
                                                            + "/"
                                                            + transferCount
                                                            + " 笔转账交易，发送速度="
                                                            + String.format("%.2f", sendSpeed)
                                                            + " TPS");
                                        }
                                    } catch (Exception e) {
                                        logger.error("转账失败: {}", e.getMessage());
                                        TransactionReceipt receipt = new TransactionReceipt();
                                        receipt.setStatus("-1");
                                        receipt.setMessage("转账失败: " + e.getMessage());
                                        collector.onMessage(receipt, 0L);
                                        sendFailed.incrementAndGet();
                                    }
                                }
                            });
        }

        // 等待所有转账交易完成
        while (collector.getReceived().intValue() != transferCount) {
            Thread.sleep(1000);
        }
        
        System.out.println("  ✓ 转账交易全部完成");
    }

    /**
     * 验证转账结果
     */
    private static void verifyTransferData(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            Integer tps,
            ThreadPoolService threadPoolService)
            throws InterruptedException {

        RateLimiter rateLimiter = RateLimiter.create(tps);
        AtomicInteger verifySuccess = new AtomicInteger(0);
        AtomicInteger verifyFailed = new AtomicInteger(0);
        int totalUsers = dagUserInfo.getUserList().size();

        for (int i = 0; i < totalUsers; i++) {
            final int index = i;
            rateLimiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        DagTransferUser user = dagUserInfo.getUserList().get(index);
                                        BigInteger remoteBalance =
                                                parallelOk.balanceOf(user.getUser());
                                        BigInteger localBalance = user.getAmount();

                                        if (localBalance.compareTo(remoteBalance) == 0) {
                                            verifySuccess.incrementAndGet();
                                        } else {
                                            verifyFailed.incrementAndGet();
                                            logger.error(
                                                    "余额不匹配 - 用户: {}, 本地余额: {}, 链上余额: {}",
                                                    user.getUser(),
                                                    localBalance,
                                                    remoteBalance);
                                        }
                                    } catch (ContractException e) {
                                        verifyFailed.incrementAndGet();
                                        logger.error("验证失败: {}", e.getMessage());
                                    }
                                }
                            });
        }

        while (verifySuccess.get() + verifyFailed.get() < totalUsers) {
            Thread.sleep(50);
        }

        System.out.println("  验证完成:");
        System.out.println("    总用户数    : " + totalUsers);
        System.out.println("    验证成功    : " + verifySuccess.get());
        System.out.println("    验证失败    : " + verifyFailed.get());
        
        if (verifyFailed.get() == 0) {
            System.out.println("  ✓ 所有账户余额验证通过！");
        } else {
            System.out.println("  ✗ 部分账户余额验证失败，请检查日志");
        }
    }
}

