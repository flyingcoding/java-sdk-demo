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
 * 快速并行转账压测工具（使用默认参数） 功能：一键快速启动并行转账压测，无需任何参数 使用方法：java -cp 'conf/:lib/*:apps/*'
 * org.fisco.bcos.sdk.demo.perf.QuickParallelPerf
 */
public class QuickParallelPerf {
    private static final Logger logger = LoggerFactory.getLogger(QuickParallelPerf.class);

    // 默认配置参数
    private static final int DEFAULT_GROUP_ID = 1;
    private static final int DEFAULT_USER_COUNT = 500;
    private static final int DEFAULT_TRANSFER_COUNT = 5000;
    private static final int DEFAULT_TPS = 100;

    public static void main(String[] args) {
        // 解析参数（如果有）或使用默认值
        int groupId = args.length >= 1 ? Integer.parseInt(args[0]) : DEFAULT_GROUP_ID;
        int userCount = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_USER_COUNT;
        int transferCount = args.length >= 3 ? Integer.parseInt(args[2]) : DEFAULT_TRANSFER_COUNT;
        int tps = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_TPS;

        try {
            System.out.println(
                    "╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║          FISCO BCOS 并行转账快速压测工具                      ║");
            System.out.println(
                    "╚════════════════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("📊 压测配置:");
            System.out.println("   群组ID        : " + groupId);
            System.out.println("   用户数量      : " + userCount);
            System.out.println("   转账交易数    : " + transferCount);
            System.out.println("   目标TPS       : " + tps);
            System.out.println();

            // 初始化
            String configFileName = ConstantConfig.CONFIG_FILE_NAME;
            URL configUrl = QuickParallelPerf.class.getClassLoader().getResource(configFileName);
            if (configUrl == null) {
                System.out.println("❌ 配置文件不存在: " + configFileName);
                return;
            }

            BcosSDK sdk = BcosSDK.build(configUrl.getPath());
            Client client = sdk.getClient(groupId);
            ThreadPoolService threadPoolService =
                    new ThreadPoolService(
                            "QuickParallelPerf",
                            sdk.getConfig().getThreadPoolConfig().getMaxBlockingQueueSize());

            // 执行压测
            runQuickTest(client, userCount, transferCount, tps, threadPoolService);

            threadPoolService.stop();
            System.exit(0);

        } catch (Exception e) {
            System.out.println("❌ 压测失败: " + e.getMessage());
            logger.error("QuickParallelPerf failed: ", e);
            System.exit(1);
        }
    }

    /** 执行快速压测 */
    private static void runQuickTest(
            Client client,
            int userCount,
            int transferCount,
            int tps,
            ThreadPoolService threadPoolService)
            throws Exception {

        PerformanceCollector addUserCollector = new PerformanceCollector();
        addUserCollector.setTotal(userCount);
        addUserCollector.setLabel("添加用户");
        addUserCollector.setAutoPrint(false);

        PerformanceCollector transferCollector = new PerformanceCollector();
        transferCollector.setTotal(transferCount);
        transferCollector.setLabel("转账");
        transferCollector.setAutoPrint(false);

        // 阶段1：部署合约
        System.out.println("⏳ [1/4] 部署并行合约...");
        ParallelOk parallelOk =
                ParallelOk.deploy(client, client.getCryptoSuite().getCryptoKeyPair());
        parallelOk.enableParallel();
        System.out.println("✅ 合约部署成功: " + parallelOk.getContractAddress());
        System.out.println();

        // 阶段2：生成并添加用户
        System.out.println("⏳ [2/4] 生成 " + userCount + " 个用户并添加到合约...");
        DagUserInfo dagUserInfo = new DagUserInfo();
        addUsersQuick(parallelOk, dagUserInfo, userCount, tps, threadPoolService, addUserCollector);
        System.out.println("✅ 用户添加完成，共 " + dagUserInfo.getUserList().size() + " 个");
        System.out.println();

        // 阶段3：查询账户
        System.out.println("⏳ [3/4] 查询账户余额...");
        queryAccountsQuick(parallelOk, dagUserInfo, tps, threadPoolService);
        System.out.println("✅ 账户查询完成");
        System.out.println();

        // 阶段4：执行转账
        System.out.println("⏳ [4/4] 执行 " + transferCount + " 笔转账...");
        executeTransferQuick(
                parallelOk, dagUserInfo, transferCount, tps, threadPoolService, transferCollector);
        System.out.println("✅ 转账完成");
        System.out.println();

        // 验证
        System.out.println("⏳ 验证结果...");
        boolean success = verifyQuick(parallelOk, dagUserInfo, tps, threadPoolService);
        System.out.println();

        System.out.println("📈 性能统计汇总:");
        // addUserCollector.printSummary();
        transferCollector.printSummary();
        System.out.println();

        if (success) {
            System.out.println(
                    "╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    ✅ 压测成功完成！                          ║");
            System.out.println(
                    "╚════════════════════════════════════════════════════════════════╝");
        } else {
            System.out.println(
                    "╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                ⚠️  压测完成但验证失败                          ║");
            System.out.println(
                    "╚════════════════════════════════════════════════════════════════╝");
        }
    }

    /** 快速添加用户 */
    private static void addUsersQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int userCount,
            int tps,
            ThreadPoolService threadPoolService,
            PerformanceCollector collector)
            throws InterruptedException {

        RateLimiter limiter = RateLimiter.create(tps);
        long currentSeconds = System.currentTimeMillis() / 1000L;
        AtomicInteger progress = new AtomicInteger(0);

        for (int i = 0; i < userCount; i++) {
            final int index = i;
            limiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            () -> {
                                String user =
                                        "user_"
                                                + Long.toHexString(currentSeconds)
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
                                    int current = progress.incrementAndGet();
                                    if (current % 100 == 0) {
                                        System.out.print("\r   进度: " + current + "/" + userCount);
                                    }
                                } catch (Exception e) {
                                    TransactionReceipt receipt = new TransactionReceipt();
                                    receipt.setStatus("-1");
                                    callback.onResponse(receipt);
                                }
                            });
        }

        while (collector.getReceived().intValue() != userCount) {
            Thread.sleep(100);
        }
        System.out.print("\r   进度: " + userCount + "/" + userCount + "\n");
    }

    /** 快速查询账户 */
    private static void queryAccountsQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int tps,
            ThreadPoolService threadPoolService)
            throws InterruptedException {

        RateLimiter limiter = RateLimiter.create(tps);
        AtomicInteger queried = new AtomicInteger(0);
        int total = dagUserInfo.getUserList().size();

        for (int i = 0; i < total; i++) {
            final int index = i;
            limiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            () -> {
                                try {
                                    DagTransferUser user = dagUserInfo.getUserList().get(index);
                                    BigInteger balance = parallelOk.balanceOf(user.getUser());
                                    user.setAmount(balance);
                                    queried.incrementAndGet();
                                } catch (ContractException e) {
                                    logger.error("Query failed: {}", e.getMessage());
                                }
                            });
        }

        while (queried.get() < total) {
            Thread.sleep(50);
        }
    }

    /** 快速执行转账 */
    private static void executeTransferQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int transferCount,
            int tps,
            ThreadPoolService threadPoolService,
            PerformanceCollector collector)
            throws InterruptedException {

        RateLimiter limiter = RateLimiter.create(tps);
        AtomicInteger progress = new AtomicInteger(0);
        Random random = new Random();

        for (int i = 0; i < transferCount; i++) {
            final int index = i;
            limiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            () -> {
                                try {
                                    BigInteger amount = BigInteger.valueOf(random.nextInt(50) + 1);
                                    DagTransferUser from = dagUserInfo.getFrom(index);
                                    DagTransferUser to = dagUserInfo.getTo(index);

                                    ParallelOkCallback callback =
                                            new ParallelOkCallback(
                                                    collector,
                                                    dagUserInfo,
                                                    ParallelOkCallback.TRANS_CALLBACK);
                                    callback.setTimeout(0);
                                    callback.setFromUser(from);
                                    callback.setToUser(to);
                                    callback.setAmount(amount);
                                    callback.recordStartTime();

                                    parallelOk.transfer(
                                            from.getUser(), to.getUser(), amount, callback);

                                    int current = progress.incrementAndGet();
                                    if (current % 500 == 0) {
                                        System.out.print(
                                                "\r   进度: " + current + "/" + transferCount);
                                    }
                                } catch (Exception e) {
                                    TransactionReceipt receipt = new TransactionReceipt();
                                    receipt.setStatus("-1");
                                    collector.onMessage(receipt, 0L);
                                }
                            });
        }

        while (collector.getReceived().intValue() != transferCount) {
            Thread.sleep(500);
        }
        System.out.print("\r   进度: " + transferCount + "/" + transferCount + "\n");
    }

    /** 快速验证 */
    private static boolean verifyQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int tps,
            ThreadPoolService threadPoolService)
            throws InterruptedException {

        RateLimiter limiter = RateLimiter.create(tps);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total = dagUserInfo.getUserList().size();

        for (int i = 0; i < total; i++) {
            final int index = i;
            limiter.acquire();
            threadPoolService
                    .getThreadPool()
                    .execute(
                            () -> {
                                try {
                                    DagTransferUser user = dagUserInfo.getUserList().get(index);
                                    BigInteger remoteBalance = parallelOk.balanceOf(user.getUser());
                                    if (user.getAmount().compareTo(remoteBalance) == 0) {
                                        success.incrementAndGet();
                                    } else {
                                        failed.incrementAndGet();
                                    }
                                } catch (ContractException e) {
                                    failed.incrementAndGet();
                                }
                            });
        }

        while (success.get() + failed.get() < total) {
            Thread.sleep(50);
        }

        System.out.println("   验证结果: 成功 " + success.get() + ", 失败 " + failed.get());
        return failed.get() == 0;
    }
}
