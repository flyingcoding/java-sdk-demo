package org.fisco.bcos.sdk.demo.perf;

import com.google.common.util.concurrent.RateLimiter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.demo.contract.ParallelOk;
import org.fisco.bcos.sdk.demo.perf.callback.ParallelOkCallback;
import org.fisco.bcos.sdk.demo.perf.collector.PerformanceCollector;
import org.fisco.bcos.sdk.demo.perf.model.DagTransferUser;
import org.fisco.bcos.sdk.demo.perf.model.DagUserInfo;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.fisco.bcos.sdk.utils.ThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 多群组快速并行转账压测工具 功能：一行命令即可对多个群组并发进行部署、加用户、查询、转账与验证 使用：java -cp 'conf/:lib/*:apps/*'
 * org.fisco.bcos.sdk.demo.perf.QuickParallelMultiGroupPerf [groupIds] [userCount] [transferCount]
 * [tps] - groupIds: 逗号分隔的群组ID列表，例如 1,2 或 1,2,3；默认 1 - userCount: 每群组用户数，默认 500 - transferCount:
 * 每群组交易数，默认 5000 - tps: 每群组TPS，默认 100
 */
public class QuickParallelMultiGroupPerf {
    private static final Logger logger = LoggerFactory.getLogger(QuickParallelMultiGroupPerf.class);

    private static final String DEFAULT_GROUP_IDS = "1";
    private static final int DEFAULT_USER_COUNT = 500;
    private static final int DEFAULT_TRANSFER_COUNT = 5000;
    private static final int DEFAULT_TPS = 100;

    /**
     * 主入口：解析参数并并发执行多群组快速压测
     *
     * <p>支持两种配置方式： 1. 默认配置：所有群组使用 config.toml 2. 群组特定配置：群组N使用 config-groupN.toml（如果存在）
     *
     * @param args 命令行参数：[groupIds] [userCount] [transferCount] [tps]
     */
    public static void main(String[] args) {
        MultiGroupConfigManager configManager = null;
        try {
            System.out.println(
                    "╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║        FISCO BCOS 多群组并行转账快速压测工具                  ║");
            System.out.println(
                    "╚════════════════════════════════════════════════════════════════╝");
            System.out.println();

            // 解析参数（若未提供则使用默认值）
            String groupIdsArg = args.length >= 1 ? args[0] : DEFAULT_GROUP_IDS;
            int userCount = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_USER_COUNT;
            int transferCount =
                    args.length >= 3 ? Integer.parseInt(args[2]) : DEFAULT_TRANSFER_COUNT;
            int tps = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_TPS;
            List<Integer> groupIds = parseGroupIds(groupIdsArg);

            // 打印配置
            System.out.println("📊 压测配置:");
            System.out.println("   群组列表      : " + groupIds);
            System.out.println("   每组用户数    : " + userCount);
            System.out.println("   每组交易数    : " + transferCount);
            System.out.println("   每组目标TPS   : " + tps);
            System.out.println();

            // 初始化配置管理器
            configManager = new MultiGroupConfigManager();

            // 显示每个群组使用的配置文件
            configManager.printConfigSummary(groupIds);

            // 构建上下文
            List<GroupContext> contexts = new ArrayList<>();
            for (Integer gid : groupIds) {
                try {
                    Client client = configManager.getClient(gid);
                    ThreadPoolService threadPoolService =
                            new ThreadPoolService(
                                    "QuickParallelMultiGroupPerf-" + gid, 102400); // 使用默认队列大小
                    contexts.add(
                            new GroupContext(
                                    gid, client, threadPoolService, userCount, transferCount, tps));
                } catch (Exception e) {
                    System.out.println("❌ 群组 " + gid + " 初始化失败: " + e.getMessage());
                    logger.error("Failed to initialize group " + gid, e);
                }
            }

            if (contexts.isEmpty()) {
                System.out.println("❌ 没有成功初始化的群组！");
                return;
            }

            // 并发执行
            ExecutorService groupExecutor = Executors.newFixedThreadPool(contexts.size());
            List<Future<Boolean>> futures = new ArrayList<>();
            for (GroupContext ctx : contexts) {
                futures.add(groupExecutor.submit(new GroupQuickTask(ctx)));
            }

            // 等待完成
            boolean allOk = true;
            for (Future<Boolean> f : futures) {
                allOk = allOk && f.get();
            }

            groupExecutor.shutdown();
            for (GroupContext ctx : contexts) {
                ctx.threadPoolService.stop();
            }

            System.out.println();
            if (allOk) {
                System.out.println(
                        "╔════════════════════════════════════════════════════════════════╗");
                System.out.println("║                    ✅ 多群组压测成功完成！                    ║");
                System.out.println(
                        "╚════════════════════════════════════════════════════════════════╝");
            } else {
                System.out.println(
                        "╔════════════════════════════════════════════════════════════════╗");
                System.out.println("║                ⚠️  多群组压测完成但存在失败                   ║");
                System.out.println(
                        "╚════════════════════════════════════════════════════════════════╝");
            }

            // 关闭配置管理器
            if (configManager != null) {
                configManager.shutdown();
            }
            System.exit(0);
        } catch (Exception e) {
            System.out.println("❌ 压测失败: " + e.getMessage());
            logger.error("QuickParallelMultiGroupPerf failed: ", e);
            // 确保资源释放
            if (configManager != null) {
                configManager.shutdown();
            }
            System.exit(1);
        }
    }

    /**
     * 解析逗号分隔的群组ID列表
     *
     * @param groupIdsArg 形如 "1,2,3" 的参数
     * @return 群组ID列表
     */
    private static List<Integer> parseGroupIds(String groupIdsArg) {
        List<Integer> ids = new ArrayList<>();
        Arrays.stream(groupIdsArg.split(","))
                .forEach(
                        s -> {
                            String t = s.trim();
                            if (!t.isEmpty()) {
                                ids.add(Integer.valueOf(t));
                            }
                        });
        return ids;
    }

    /** 群组上下文 */
    private static class GroupContext {
        final int groupId;
        final Client client;
        final ThreadPoolService threadPoolService;
        final int userCount;
        final int transferCount;
        final int tps;

        GroupContext(
                int groupId,
                Client client,
                ThreadPoolService threadPoolService,
                int userCount,
                int transferCount,
                int tps) {
            this.groupId = groupId;
            this.client = client;
            this.threadPoolService = threadPoolService;
            this.userCount = userCount;
            this.transferCount = transferCount;
            this.tps = tps;
        }
    }

    /** 群组快速压测任务：部署→加用户→查余额→转账→验证 */
    private static class GroupQuickTask implements Callable<Boolean> {
        private final GroupContext ctx;

        GroupQuickTask(GroupContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public Boolean call() {
            try {
                // 部署
                System.out.println("[Group " + ctx.groupId + "] ⏳ [1/4] 部署并行合约...");
                ParallelOk parallelOk =
                        ParallelOk.deploy(
                                ctx.client, ctx.client.getCryptoSuite().getCryptoKeyPair());
                parallelOk.enableParallel();
                System.out.println(
                        "[Group " + ctx.groupId + "] ✅ 合约部署成功: " + parallelOk.getContractAddress());

                // 加用户
                System.out.println(
                        "[Group " + ctx.groupId + "] ⏳ [2/4] 生成 " + ctx.userCount + " 个用户并添加...");
                DagUserInfo dagUserInfo = new DagUserInfo();
                addUsersQuick(
                        parallelOk,
                        dagUserInfo,
                        ctx.userCount,
                        ctx.tps,
                        ctx.threadPoolService,
                        ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "] ✅ 用户添加完成");

                // 查余额
                System.out.println("[Group " + ctx.groupId + "] ⏳ [3/4] 查询账户余额...");
                queryAccountsQuick(
                        parallelOk, dagUserInfo, ctx.tps, ctx.threadPoolService, ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "] ✅ 账户查询完成");

                // 转账
                System.out.println(
                        "[Group " + ctx.groupId + "] ⏳ [4/4] 执行 " + ctx.transferCount + " 笔转账...");
                executeTransferQuick(
                        parallelOk,
                        dagUserInfo,
                        ctx.transferCount,
                        ctx.tps,
                        ctx.threadPoolService,
                        ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "] ✅ 转账完成");

                // 验证
                System.out.println("[Group " + ctx.groupId + "] ⏳ 验证结果...");
                boolean ok =
                        verifyQuick(
                                parallelOk,
                                dagUserInfo,
                                ctx.tps,
                                ctx.threadPoolService,
                                ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "]   验证结果: " + (ok ? "成功" : "失败"));
                return ok;
            } catch (Exception e) {
                logger.error("[Group {}] quick task failed: ", ctx.groupId, e);
                return false;
            }
        }
    }

    /** 快速添加用户 */
    private static void addUsersQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int userCount,
            int tps,
            ThreadPoolService threadPoolService,
            int groupId)
            throws InterruptedException {

        PerformanceCollector collector = new PerformanceCollector();
        collector.setTotal(userCount);
        RateLimiter limiter = RateLimiter.create(tps);
        long currentSeconds = System.currentTimeMillis() / 1000L;
        AtomicInteger progress = new AtomicInteger(0);
        int area = Math.max(1, userCount / 10);

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
                                                + "_g"
                                                + groupId
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
                                    int cur = progress.incrementAndGet();
                                    if (cur % area == 0 || cur == userCount) {
                                        System.out.print(
                                                "\r[Group "
                                                        + groupId
                                                        + "]    进度: "
                                                        + cur
                                                        + "/"
                                                        + userCount);
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
        System.out.print("\r[Group " + groupId + "]    进度: " + userCount + "/" + userCount + "\n");
    }

    /** 快速查询账户 */
    private static void queryAccountsQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int tps,
            ThreadPoolService threadPoolService,
            int groupId)
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
                                    logger.error(
                                            "[Group {}] Query failed: {}", groupId, e.getMessage());
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
            int groupId)
            throws InterruptedException {

        PerformanceCollector collector = new PerformanceCollector();
        collector.setTotal(transferCount);
        RateLimiter limiter = RateLimiter.create(tps);
        AtomicInteger progress = new AtomicInteger(0);
        Random random = new Random();
        int area = Math.max(1, transferCount / 10);

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
                                    int cur = progress.incrementAndGet();
                                    if (cur % area == 0 || cur == transferCount) {
                                        System.out.print(
                                                "\r[Group "
                                                        + groupId
                                                        + "]    进度: "
                                                        + cur
                                                        + "/"
                                                        + transferCount);
                                    }
                                } catch (Exception e) {
                                    TransactionReceipt receipt = new TransactionReceipt();
                                    receipt.setStatus("-1");
                                    collector.onMessage(receipt, 0L);
                                }
                            });
        }

        while (collector.getReceived().intValue() != transferCount) {
            Thread.sleep(300);
        }
        System.out.print(
                "\r[Group " + groupId + "]    进度: " + transferCount + "/" + transferCount + "\n");
    }

    /**
     * 快速验证
     *
     * @return 验证是否全部通过
     */
    private static boolean verifyQuick(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int tps,
            ThreadPoolService threadPoolService,
            int groupId)
            throws InterruptedException {

        RateLimiter limiter = RateLimiter.create(tps);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int total = dagUserInfo.getUserList().size();
        CountDownLatch latch = new CountDownLatch(total);

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
                                } finally {
                                    latch.countDown();
                                }
                            });
        }

        latch.await(30, TimeUnit.MINUTES);
        System.out.println(
                "[Group " + groupId + "]    验证统计: 成功 " + success.get() + ", 失败 " + failed.get());
        return failed.get() == 0;
    }
}
