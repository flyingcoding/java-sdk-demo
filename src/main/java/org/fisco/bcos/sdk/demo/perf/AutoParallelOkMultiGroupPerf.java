package org.fisco.bcos.sdk.demo.perf;

import com.google.common.util.concurrent.RateLimiter;
import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 多群组自动化并行转账压测工具 功能：基于单群组自动化压测，扩展为对多个群组同时进行部署、加用户、查询与转账压测 使用：java -cp 'conf/:lib/*:apps/*'
 * org.fisco.bcos.sdk.demo.perf.AutoParallelOkMultiGroupPerf [groupIds] [userCount] [transferCount]
 * [tps] - groupIds: 逗号分隔的群组ID列表，例如 1,2 或 1,2,3 - userCount: 每个群组生成的用户数量 - transferCount:
 * 每个群组执行的转账交易数量 - tps: 每个群组的目标TPS
 */
public class AutoParallelOkMultiGroupPerf {
    private static final Logger logger =
            LoggerFactory.getLogger(AutoParallelOkMultiGroupPerf.class);

    /** 打印使用说明 */
    private static void usage() {
        System.out.println("===== 多群组自动化并行转账压测工具 =====");
        System.out.println(" 功能：对多个群组并发执行部署合约、添加账户、查询与转账压测");
        System.out.println(" 使用方法:");
        System.out.println(
                " \t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.AutoParallelOkMultiGroupPerf [groupIds] [userCount] [transferCount] [tps]");
        System.out.println(" 参数说明:");
        System.out.println(" \t groupIds       : 逗号分隔的群组ID列表（如：1,2 或 1,2,3）");
        System.out.println(" \t userCount      : 每群组生成的用户数量（如：1000）");
        System.out.println(" \t transferCount  : 每群组转账交易总数（如：10000）");
        System.out.println(" \t tps            : 每群组目标TPS/QPS（如：100）");
        System.out.println();
        System.out.println(" 示例:");
        System.out.println(
                " \t java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.AutoParallelOkMultiGroupPerf 1,2 1000 10000 200");
    }

    /**
     * 主入口：解析多群组参数，创建多群组上下文并并发执行压测
     *
     * @param args 命令行参数：[groupIds] [userCount] [transferCount] [tps]
     */
    public static void main(String[] args) {
        try {
            // 加载配置
            String configFileName = ConstantConfig.CONFIG_FILE_NAME;
            URL configUrl =
                    AutoParallelOkMultiGroupPerf.class.getClassLoader().getResource(configFileName);
            if (configUrl == null) {
                System.out.println("配置文件 " + configFileName + " 不存在！");
                return;
            }

            // 参数校验
            if (args.length < 4) {
                usage();
                return;
            }

            // 解析参数
            String groupIdsArg = args[0];
            List<Integer> groupIds = parseGroupIds(groupIdsArg);
            Integer userCountPerGroup = Integer.valueOf(args[1]);
            Integer transferCountPerGroup = Integer.valueOf(args[2]);
            Integer tpsPerGroup = Integer.valueOf(args[3]);

            System.out.println(
                    "====================================================================");
            System.out.println("========== 多群组自动化并行转账压测开始 ==========");
            System.out.println(
                    "====================================================================");
            System.out.println("配置信息:");
            System.out.println("  群组列表      : " + groupIds);
            System.out.println("  每组用户数    : " + userCountPerGroup);
            System.out.println("  每组交易数    : " + transferCountPerGroup);
            System.out.println("  每组目标TPS   : " + tpsPerGroup);
            System.out.println(
                    "====================================================================");

            // 初始化SDK
            BcosSDK sdk = BcosSDK.build(configUrl.getPath());

            // 构建每个群组的上下文
            List<GroupContext> contexts = new ArrayList<>();
            for (Integer gid : groupIds) {
                Client client = sdk.getClient(gid);
                ThreadPoolService threadPoolService =
                        new ThreadPoolService(
                                "AutoParallelOkMultiGroupPerf-" + gid,
                                sdk.getConfig().getThreadPoolConfig().getMaxBlockingQueueSize());
                contexts.add(
                        new GroupContext(
                                gid,
                                client,
                                threadPoolService,
                                userCountPerGroup,
                                transferCountPerGroup,
                                tpsPerGroup));
            }

            // 并发执行每个群组的压测
            ExecutorService groupExecutor = Executors.newFixedThreadPool(contexts.size());
            List<Future<GroupResult>> futures = new ArrayList<>();
            for (GroupContext ctx : contexts) {
                futures.add(groupExecutor.submit(new GroupPerfTask(ctx)));
            }

            // 汇总结果
            Map<Integer, GroupResult> resultMap = new ConcurrentHashMap<>();
            for (Future<GroupResult> f : futures) {
                GroupResult r = f.get();
                resultMap.put(r.groupId, r);
            }

            // 关闭资源
            groupExecutor.shutdown();
            for (GroupContext ctx : contexts) {
                ctx.threadPoolService.stop();
            }

            // 打印汇总
            System.out.println();
            System.out.println("======================= 多群组压测汇总 =======================");
            int successGroups = 0;
            for (Integer gid : groupIds) {
                GroupResult r = resultMap.get(gid);
                String status = (r != null && r.success) ? "成功" : "失败";
                System.out.println(
                        "  组 "
                                + gid
                                + " ："
                                + status
                                + (r != null && r.contractAddress != null
                                        ? (", 合约=" + r.contractAddress)
                                        : ""));
                if (r != null && r.success) {
                    successGroups++;
                }
            }
            System.out.println("  成功群组/总群组: " + successGroups + "/" + groupIds.size());
            System.out.println("============================================================");

            System.out.println();
            System.out.println(
                    "====================================================================");
            System.out.println("========== 多群组自动化并行转账压测完成 ==========");
            System.out.println(
                    "====================================================================");
            System.exit(0);
        } catch (Exception e) {
            System.out.println("压测失败，错误信息: " + e.getMessage());
            logger.error("AutoParallelOkMultiGroupPerf failed: ", e);
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
        String[] arr = groupIdsArg.split(",");
        List<Integer> ids = new ArrayList<>();
        Arrays.stream(arr)
                .forEach(
                        s -> {
                            String t = s.trim();
                            if (!t.isEmpty()) {
                                ids.add(Integer.valueOf(t));
                            }
                        });
        return ids;
    }

    /** 群组上下文，封装每个群组压测所需的对象 */
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

    /** 群组压测任务：依次执行部署、加用户、查询、转账与验证 */
    private static class GroupPerfTask implements Callable<GroupResult> {
        private final GroupContext ctx;

        GroupPerfTask(GroupContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public GroupResult call() {
            GroupResult result = new GroupResult();
            result.groupId = ctx.groupId;
            try {
                System.out.println();
                System.out.println("[Group " + ctx.groupId + "] 【步骤 1/4】部署并行转账合约...");
                ParallelOk parallelOk =
                        ParallelOk.deploy(
                                ctx.client, ctx.client.getCryptoSuite().getCryptoKeyPair());
                parallelOk.enableParallel();
                result.contractAddress = parallelOk.getContractAddress();
                System.out.println(
                        "[Group " + ctx.groupId + "]  ✓ 合约部署成功: " + result.contractAddress);

                // 生成并添加用户
                System.out.println(
                        "[Group " + ctx.groupId + "] 【步骤 2/4】生成并添加 " + ctx.userCount + " 个用户...");
                DagUserInfo dagUserInfo = new DagUserInfo();
                addUsersForGroup(
                        parallelOk,
                        dagUserInfo,
                        ctx.userCount,
                        ctx.tps,
                        ctx.threadPoolService,
                        ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "]  ✓ 用户添加完成");

                // 查询账户
                System.out.println("[Group " + ctx.groupId + "] 【步骤 3/4】查询账户余额...");
                queryAccountsForGroup(
                        parallelOk, dagUserInfo, ctx.tps, ctx.threadPoolService, ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "]  ✓ 账户查询完成");

                // 执行转账
                System.out.println(
                        "[Group "
                                + ctx.groupId
                                + "] 【步骤 4/4】执行转账压测，共 "
                                + ctx.transferCount
                                + " 笔，TPS="
                                + ctx.tps
                                + "...");
                executeTransferForGroup(
                        parallelOk,
                        dagUserInfo,
                        ctx.transferCount,
                        ctx.tps,
                        ctx.threadPoolService,
                        ctx.groupId);
                System.out.println("[Group " + ctx.groupId + "]  ✓ 转账交易完成");

                // 验证
                System.out.println("[Group " + ctx.groupId + "] 【验证】开始验证余额...");
                boolean verifyOk =
                        verifyForGroup(
                                parallelOk,
                                dagUserInfo,
                                ctx.tps,
                                ctx.threadPoolService,
                                ctx.groupId);
                result.success = verifyOk;
                if (verifyOk) {
                    System.out.println("[Group " + ctx.groupId + "]  ✓ 余额验证通过");
                } else {
                    System.out.println("[Group " + ctx.groupId + "]  ✗ 余额验证失败");
                }
            } catch (Exception e) {
                result.success = false;
                result.errorMessage = e.getMessage();
                logger.error("Group {} failed: ", ctx.groupId, e);
            }
            return result;
        }
    }

    /** 群组结果信息 */
    private static class GroupResult {
        int groupId;
        boolean success;
        String contractAddress;
        String errorMessage;
    }

    /**
     * 为指定群组添加用户
     *
     * @param parallelOk 合约实例
     * @param dagUserInfo 用户信息容器
     * @param userCount 用户数量
     * @param tps 每群组TPS
     * @param threadPoolService 线程池
     * @param groupId 群组ID（打印用途）
     */
    private static void addUsersForGroup(
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
                                        Long.toHexString(currentSeconds)
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
                                        System.out.println(
                                                "[Group "
                                                        + groupId
                                                        + "]  已添加用户: "
                                                        + cur
                                                        + "/"
                                                        + userCount);
                                    }
                                } catch (Exception e) {
                                    logger.warn("[Group {}] 添加用户失败: {}", groupId, e.getMessage());
                                    TransactionReceipt receipt = new TransactionReceipt();
                                    receipt.setStatus("-1");
                                    receipt.setMessage("添加用户失败: " + e.getMessage());
                                    callback.onResponse(receipt);
                                }
                            });
        }

        while (collector.getReceived().intValue() != userCount) {
            Thread.sleep(100);
        }
    }

    /**
     * 查询指定群组全部账户余额
     *
     * @param parallelOk 合约实例
     * @param dagUserInfo 用户信息容器
     * @param tps 每群组TPS
     * @param threadPoolService 线程池
     * @param groupId 群组ID（打印用途）
     */
    private static void queryAccountsForGroup(
            ParallelOk parallelOk,
            DagUserInfo dagUserInfo,
            int tps,
            ThreadPoolService threadPoolService,
            int groupId)
            throws InterruptedException {

        RateLimiter limiter = RateLimiter.create(tps);
        AtomicInteger queried = new AtomicInteger(0);
        int total = dagUserInfo.getUserList().size();
        int area = Math.max(1, total / 10);

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
                                    int cur = queried.incrementAndGet();
                                    if (cur % area == 0 || cur == total) {
                                        System.out.println(
                                                "[Group "
                                                        + groupId
                                                        + "]  已查询账户: "
                                                        + cur
                                                        + "/"
                                                        + total);
                                    }
                                } catch (ContractException e) {
                                    logger.error("[Group {}] 查询账户失败: {}", groupId, e.getMessage());
                                }
                            });
        }

        while (queried.get() < total) {
            Thread.sleep(50);
        }
    }

    /**
     * 执行指定群组的转账压测
     *
     * @param parallelOk 合约实例
     * @param dagUserInfo 用户信息容器
     * @param transferCount 转账次数
     * @param tps 每群组TPS
     * @param threadPoolService 线程池
     * @param groupId 群组ID（打印用途）
     */
    private static void executeTransferForGroup(
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
                                    BigInteger amount = BigInteger.valueOf(random.nextInt(100) + 1);
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
                                        System.out.println(
                                                "[Group "
                                                        + groupId
                                                        + "]  已发送转账: "
                                                        + cur
                                                        + "/"
                                                        + transferCount);
                                    }
                                } catch (Exception e) {
                                    logger.error("[Group {}] 转账失败: {}", groupId, e.getMessage());
                                    TransactionReceipt receipt = new TransactionReceipt();
                                    receipt.setStatus("-1");
                                    collector.onMessage(receipt, 0L);
                                }
                            });
        }

        while (collector.getReceived().intValue() != transferCount) {
            Thread.sleep(500);
        }
    }

    /**
     * 验证指定群组用户余额
     *
     * @param parallelOk 合约实例
     * @param dagUserInfo 用户信息容器
     * @param tps 每群组TPS
     * @param threadPoolService 线程池
     * @param groupId 群组ID（打印用途）
     * @return 是否全部验证通过
     */
    private static boolean verifyForGroup(
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
                                    BigInteger localBalance = user.getAmount();
                                    if (localBalance.compareTo(remoteBalance) == 0) {
                                        success.incrementAndGet();
                                    } else {
                                        failed.incrementAndGet();
                                        logger.error(
                                                "[Group {}] 余额不匹配 - 用户: {}, 本地: {}, 链上: {}",
                                                groupId,
                                                user.getUser(),
                                                localBalance,
                                                remoteBalance);
                                    }
                                } catch (ContractException e) {
                                    failed.incrementAndGet();
                                    logger.error("[Group {}] 验证失败: {}", groupId, e.getMessage());
                                } finally {
                                    latch.countDown();
                                }
                            });
        }

        latch.await(60, TimeUnit.MINUTES);
        System.out.println(
                "[Group " + groupId + "]  验证结果: 成功=" + success.get() + ", 失败=" + failed.get());
        return failed.get() == 0;
    }
}
