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
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.fisco.bcos.sdk.demo.contract.Account;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.model.ConstantConfig;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.transaction.model.exception.ContractException;
import org.fisco.bcos.sdk.v3.utils.ThreadPoolService;

/**
 * 多群组并行压测工具类 支持同时对多个群组执行DMC压测
 *
 * @author flying
 */
public class PerformanceMultiGroupDMC {

    /** 打印使用说明 */
    public static void usage() {
        System.out.println(" Usage:");
        System.out.println(
                "===== PerformanceMultiGroupDMC test (Multiple Groups Parallel) ===========");
        System.out.println(
                " \t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC [groupIds] [userCount] [count] [qps].");
        System.out.println(" \t [groupIds]: 群组ID列表，用逗号分隔，例如: group0,group1,group2");
        System.out.println(" \t [userCount]: 每个群组创建的账户数量");
        System.out.println(" \t [count]: 每个群组的交易总量");
        System.out.println(" \t [qps]: 每个群组的QPS");
        System.out.println();
        System.out.println(" Example:");
        System.out.println(
                " \t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC group0,group1,group2 8 10000 100");
    }

    /** 程序入口 */
    public static void main(String[] args)
            throws ContractException, IOException, InterruptedException {
        try {
            String configFileName = ConstantConfig.CONFIG_FILE_NAME;
            URL configUrl =
                    PerformanceMultiGroupDMC.class.getClassLoader().getResource(configFileName);
            if (configUrl == null) {
                System.out.println("The configFile " + configFileName + " doesn't exist!");
                return;
            }

            if (args.length < 4) {
                usage();
                return;
            }

            // 解析群组ID列表
            String groupIdsStr = args[0];
            String[] groupIds = groupIdsStr.split(",");
            int userCount = Integer.valueOf(args[1]).intValue();
            Integer count = Integer.valueOf(args[2]).intValue();
            Integer qps = Integer.valueOf(args[3]).intValue();

            if (groupIds.length == 0) {
                System.out.println("Error: No group IDs provided!");
                usage();
                return;
            }

            System.out.println("====== Multi-Group DMC Performance Test ======");
            System.out.println("Groups: " + groupIdsStr);
            System.out.println("User count per group: " + userCount);
            System.out.println("Transactions per group: " + count);
            System.out.println("QPS per group: " + qps);
            System.out.println("Total groups: " + groupIds.length);
            System.out.println("Total transactions: " + (count * groupIds.length));
            System.out.println("================================================");

            String configFile = configUrl.getPath();
            BcosSDK sdk = BcosSDK.build(configFile);

            // 创建线程池服务
            ThreadPoolService threadPoolService =
                    new ThreadPoolService(
                            "MultiGroupDMCClient", Runtime.getRuntime().availableProcessors() * 2);

            // 启动多群组压测
            startMultiGroupTest(sdk, groupIds, userCount, count, qps, threadPoolService);

            threadPoolService.getThreadPool().awaitTermination(0, TimeUnit.SECONDS);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** 启动多群组压测 为每个群组创建独立的压测任务并并行执行 */
    public static void startMultiGroupTest(
            BcosSDK sdk,
            String[] groupIds,
            int userCount,
            int count,
            Integer qps,
            ThreadPoolService threadPoolService)
            throws InterruptedException, ContractException, IOException {

        // 创建一个执行器服务，用于并行执行各群组的压测
        ExecutorService executor = Executors.newFixedThreadPool(groupIds.length);
        CountDownLatch allGroupsLatch = new CountDownLatch(groupIds.length);

        // 记录总体开始时间
        long globalStartTime = System.currentTimeMillis();

        // 为每个群组创建独立的压测任务
        for (int i = 0; i < groupIds.length; i++) {
            final String groupId = groupIds[i].trim();
            final int groupIndex = i;

            executor.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                System.out.println(
                                        "\n[Group "
                                                + groupIndex
                                                + ": "
                                                + groupId
                                                + "] Starting test...");

                                // 为该群组创建客户端
                                Client client = sdk.getClient(groupId);

                                // 执行该群组的压测
                                startSingleGroupTest(
                                        groupId,
                                        groupIndex,
                                        client,
                                        userCount,
                                        count,
                                        qps,
                                        threadPoolService);

                                System.out.println(
                                        "\n[Group "
                                                + groupIndex
                                                + ": "
                                                + groupId
                                                + "] Test completed!");
                            } catch (Exception e) {
                                System.err.println(
                                        "[Group "
                                                + groupIndex
                                                + ": "
                                                + groupId
                                                + "] Test failed: "
                                                + e.getMessage());
                                e.printStackTrace();
                            } finally {
                                allGroupsLatch.countDown();
                            }
                        }
                    });
        }

        // 等待所有群组的压测完成
        System.out.println("\nWaiting for all groups to complete...");
        allGroupsLatch.await();
        executor.shutdown();

        long globalEndTime = System.currentTimeMillis();
        long totalTime = globalEndTime - globalStartTime;

        // 打印总体统计信息
        System.out.println("\n");
        System.out.println("====================================================================");
        System.out.println("==================== Multi-Group Test Summary =====================");
        System.out.println("====================================================================");
        System.out.println("Total groups tested: " + groupIds.length);
        System.out.println("Total transactions: " + (count * groupIds.length));
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println(
                "Overall TPS: " + (count * groupIds.length) / ((double) totalTime / 1000));
        System.out.println("====================================================================");
    }

    /** 执行单个群组的压测 每个群组有独立的账户、交易和统计信息 */
    public static void startSingleGroupTest(
            String groupId,
            int groupIndex,
            Client client,
            int userCount,
            int count,
            Integer qps,
            ThreadPoolService threadPoolService)
            throws IOException, InterruptedException, ContractException {

        System.out.println(
                "[Group "
                        + groupIndex
                        + ": "
                        + groupId
                        + "] User count: "
                        + userCount
                        + ", count: "
                        + count
                        + ", qps: "
                        + qps);

        RateLimiter limiter = RateLimiter.create(qps.intValue());

        Account[] accounts = new Account[userCount];
        AtomicLong[] summary = new AtomicLong[userCount];

        final Random random = new Random();
        random.setSeed(System.currentTimeMillis() + groupIndex);

        // 创建账户
        System.out.println("[Group " + groupIndex + ": " + groupId + "] Creating accounts...");
        IntStream.range(0, userCount)
                .parallel()
                .forEach(
                        i -> {
                            Account account;
                            try {
                                long initBalance = Math.abs(random.nextLong());

                                limiter.acquire();
                                account =
                                        Account.deploy(
                                                client, client.getCryptoSuite().getCryptoKeyPair());
                                account.addBalance(BigInteger.valueOf(initBalance));

                                accounts[i] = account;
                                summary[i] = new AtomicLong(initBalance);
                            } catch (ContractException e) {
                                e.printStackTrace();
                            }
                        });
        System.out.println("[Group " + groupIndex + ": " + groupId + "] Accounts created!");

        // 发送交易
        System.out.println("[Group " + groupIndex + ": " + groupId + "] Sending transactions...");
        ProgressBar sendedBar =
                new ProgressBarBuilder()
                        .setTaskName("[G" + groupIndex + "]Send   ")
                        .setInitialMax(count)
                        .setStyle(ProgressBarStyle.UNICODE_BLOCK)
                        .build();
        ProgressBar receivedBar =
                new ProgressBarBuilder()
                        .setTaskName("[G" + groupIndex + "]Receive")
                        .setInitialMax(count)
                        .setStyle(ProgressBarStyle.UNICODE_BLOCK)
                        .build();

        CountDownLatch transactionLatch = new CountDownLatch(count);
        AtomicLong totalCost = new AtomicLong(0);
        Collector collector = new Collector();
        collector.setTotal(count);

        IntStream.range(0, count)
                .parallel()
                .forEach(
                        i -> {
                            limiter.acquire();

                            final int index = i % accounts.length;
                            Account account = accounts[index];
                            long now = System.currentTimeMillis();

                            final long value = Math.abs(random.nextLong() % 1000);

                            account.addBalance(
                                    BigInteger.valueOf(value),
                                    new TransactionCallback() {
                                        @Override
                                        public void onResponse(TransactionReceipt receipt) {
                                            AtomicLong count = summary[index];
                                            count.addAndGet(value);

                                            long cost = System.currentTimeMillis() - now;
                                            collector.onMessage(receipt, cost);

                                            receivedBar.step();
                                            transactionLatch.countDown();
                                            totalCost.addAndGet(System.currentTimeMillis() - now);
                                        }
                                    });
                            sendedBar.step();
                        });
        transactionLatch.await();

        sendedBar.close();
        receivedBar.close();

        // 打印该群组的统计报告
        System.out.println("\n[Group " + groupIndex + ": " + groupId + "] Performance Report:");
        collector.report();

        System.out.println("[Group " + groupIndex + ": " + groupId + "] Transactions completed!");

        // 检查结果
        System.out.println("[Group " + groupIndex + ": " + groupId + "] Checking results...");
        IntStream.range(0, summary.length)
                .parallel()
                .forEach(
                        i -> {
                            limiter.acquire();
                            final long expectBalance = summary[i].longValue();
                            try {
                                limiter.acquire();
                                BigInteger balance = accounts[i].balance();
                                if (balance.longValue() != expectBalance) {
                                    System.out.println(
                                            "[Group "
                                                    + groupIndex
                                                    + ": "
                                                    + groupId
                                                    + "] Check failed! Account["
                                                    + i
                                                    + "] balance: "
                                                    + balance
                                                    + " not equal to expected: "
                                                    + expectBalance);
                                }
                            } catch (ContractException e) {
                                e.printStackTrace();
                            }
                        });

        System.out.println("[Group " + groupIndex + ": " + groupId + "] Check finished!");
    }
}
