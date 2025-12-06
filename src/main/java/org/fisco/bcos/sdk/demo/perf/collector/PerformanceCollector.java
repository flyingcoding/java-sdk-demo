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
package org.fisco.bcos.sdk.demo.perf.collector;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.fisco.bcos.sdk.model.JsonRpcResponse;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceCollector {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceCollector.class);
    private final AtomicLong less50 = new AtomicLong(0);
    private final AtomicLong less100 = new AtomicLong(0);
    private final AtomicLong less200 = new AtomicLong(0);
    private final AtomicLong less400 = new AtomicLong(0);
    private final AtomicLong less1000 = new AtomicLong(0);
    private final AtomicLong less2000 = new AtomicLong(0);
    private final AtomicLong timeout2000 = new AtomicLong(0);
    private final AtomicLong totalCost = new AtomicLong(0);
    private final AtomicInteger received = new AtomicInteger(0);
    private final AtomicInteger error = new AtomicInteger(0);
    // 最小/最大单次耗时与结束时间
    private final AtomicLong minCost = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxCost = new AtomicLong(0);
    /** -- SETTER -- 设置总请求数 -- GETTER -- 获取配置的总请求数 */
    private Integer total = 0;
    /** -- GETTER -- 获取统计起始时间戳 -- SETTER -- 设置统计起始时间戳 */
    private Long startTimestamp = System.currentTimeMillis();
    /** -- SETTER -- 设置输出标签 */
    // 可选的标签（例如 Group 1），用于多群组时标识输出归属
    private String label = null;
    /** -- SETTER -- 设置是否自动打印完成日志 */
    // 是否在达到 total 后自动打印统计（默认 true 保持旧行为）
    private boolean autoPrint = true;
    /** -- SETTER -- 设置是否打印进度 */
    // 是否打印进度（默认 true 保持旧行为）
    private boolean enableProgress = true;
    /** -- GETTER -- 获取结束时间戳（可能为 null，若统计尚未结束） */
    private Long endTimestamp = null;

    /** 获取已接收的回包数量 */
    public Integer getReceived() {
        return received.get();
    }

    /** 显式提供 getter/setter 以兼容旧调用 */
    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setAutoPrint(boolean autoPrint) {
        this.autoPrint = autoPrint;
    }

    public void setEnableProgress(boolean enableProgress) {
        this.enableProgress = enableProgress;
    }

    public Long getEndTimestamp() {
        return endTimestamp;
    }

    /** 处理 JSON-RPC 响应并统计耗时与错误 */
    public void onRpcMessage(JsonRpcResponse response, Long cost) {
        try {
            boolean errorMessage = false;
            if (response.getError() != null && response.getError().getCode() != 0) {
                logger.warn("receive error jsonRpcResponse: {}", response);
                errorMessage = true;
            }
            stat(errorMessage, cost);
        } catch (Exception e) {
            logger.error("onRpcMessage exception: {}", e.getMessage());
        }
    }

    /** 统计核心：更新错误数、分布区间、总耗时、min/max，并在完成后根据配置打印 */
    public void stat(boolean errorMessage, Long cost) {
        if (errorMessage) {
            error.addAndGet(1);
        }

        if (cost != null) {
            // 更新分布
            if (cost < 50) {
                less50.incrementAndGet();
            } else if (cost < 100) {
                less100.incrementAndGet();
            } else if (cost < 200) {
                less200.incrementAndGet();
            } else if (cost < 400) {
                less400.incrementAndGet();
            } else if (cost < 1000) {
                less1000.incrementAndGet();
            } else if (cost < 2000) {
                less2000.incrementAndGet();
            } else {
                timeout2000.incrementAndGet();
            }

            totalCost.addAndGet(cost);

            // 更新 min/max
            updateMin(cost);
            updateMax(cost);
        }

        // 进度打印（每 10% 一次），默认保持旧行为；total<10 时按步长 1 打印
        int step = Math.max(1, Math.max(1, total / 10));
        int currentReceivedPlusOne = received.get() + 1;
        if (enableProgress && (currentReceivedPlusOne % step == 0)) {
            String prefix = (label != null ? ("[" + label + "] ") : "");
            System.out.println(
                    prefix
                            + "                                                       |received:"
                            + (currentReceivedPlusOne) * 100 / Math.max(1, total)
                            + "%");
        }

        if (received.incrementAndGet() >= total) {
            // 完成时记录结束时间
            if (endTimestamp == null) {
                endTimestamp = System.currentTimeMillis();
            }

            if (autoPrint) {
                printSummary();
            }
        }
    }

    private void updateMin(long cost) {
        long prev;
        do {
            prev = minCost.get();
            if (cost >= prev) {
                return;
            }
        } while (!minCost.compareAndSet(prev, cost));
    }

    private void updateMax(long cost) {
        long prev;
        do {
            prev = maxCost.get();
            if (cost <= prev) {
                return;
            }
        } while (!maxCost.compareAndSet(prev, cost));
    }

    /** 打印统计摘要；若配置了 label，则在 total 前带上该标识 */
    public void printSummary() {
        Long totalTime = getDurationMillis();
        String prefix = (label != null ? ("[" + label + "] ") : "");

        System.out.println(prefix + "性能统计");
        System.out.println("===================================================================");
        System.out.println("总交易数        : " + total);
        System.out.println("总耗时          : " + totalTime + "ms");
        System.out.println(
                "TPS(含错误)     : "
                        + String.format(
                                "%.2f", total / ((double) Math.max(1, totalTime) / 1000)));
        System.out.println(
                "TPS(不含错误)   : "
                        + String.format(
                                "%.2f",
                                (double) (total - error.get())
                                        / ((double) Math.max(1, totalTime) / 1000)));
        System.out.println("平均耗时        : " + avgTimeCostMs() + "ms");
        System.out.println("最大耗时        : " + getMaxCost() + "ms");
        System.out.println(
                "错误率          : "
                        + String.format(
                                "%.2f", (error.get() / (double) Math.max(1, received.get())) * 100)
                        + "%");

        System.out.println("耗时分布:");
        System.out.println(
                "0    < t < 50ms     : "
                        + less50
                        + " ("
                        + String.format("%.2f", (double) less50.get() / Math.max(1, total) * 100)
                        + "%)");
        System.out.println(
                "50   < t < 100ms    : "
                        + less100
                        + " ("
                        + String.format("%.2f", (double) less100.get() / Math.max(1, total) * 100)
                        + "%)");
        System.out.println(
                "100  < t < 200ms    : "
                        + less200
                        + " ("
                        + String.format("%.2f", (double) less200.get() / Math.max(1, total) * 100)
                        + "%)");
        System.out.println(
                "200  < t < 400ms    : "
                        + less400
                        + " ("
                        + String.format("%.2f", (double) less400.get() / Math.max(1, total) * 100)
                        + "%)");
        System.out.println(
                "400  < t < 1000ms   : "
                        + less1000
                        + " ("
                        + String.format("%.2f", (double) less1000.get() / Math.max(1, total) * 100)
                        + "%)");
        System.out.println(
                "1000 < t < 2000ms   : "
                        + less2000
                        + " ("
                        + String.format("%.2f", (double) less2000.get() / Math.max(1, total) * 100)
                        + "%)");
        System.out.println(
                "2000 < t            : "
                        + timeout2000
                        + " ("
                        + String.format(
                                "%.2f", (double) timeout2000.get() / Math.max(1, total) * 100)
                        + "%)");
    }

    /** 获取总耗时（毫秒）；若尚未结束，则返回当前与start的差 */
    public Long getDurationMillis() {
        Long end = endTimestamp;
        if (end == null) {
            end = System.currentTimeMillis();
        }
        return end - startTimestamp;
    }

    /** 计算平均单次耗时（ms） */
    public long avgTimeCostMs() {
        return (total <= 0 ? 0L : totalCost.get() / total);
    }

    /** 处理交易回执并统计耗时与错误 */
    public void onMessage(TransactionReceipt receipt, Long cost) {
        try {
            boolean errorMessage = false;
            if (!receipt.isStatusOK()) {
                logger.error(
                        "error receipt, status: {}, output: {}, message: {}",
                        receipt.getStatus(),
                        receipt.getOutput(),
                        receipt.getMessage());
                errorMessage = true;
            }
            stat(errorMessage, cost);
        } catch (Exception e) {
            logger.error("error:", e);
        }
    }

    /** 获取总错误数 */
    public int getError() {
        return error.get();
    }

    /** 获取累计耗时总和（ms） */
    public long getTotalCost() {
        return totalCost.get();
    }

    /** 获取单次最小耗时（ms） */
    public long getMinCost() {
        long v = minCost.get();
        return (v == Long.MAX_VALUE ? 0L : v);
    }

    /** 获取单次最大耗时（ms） */
    public long getMaxCost() {
        return maxCost.get();
    }

    /** 计算包含错误的 TPS */
    public double tpsIncludeErrors() {
        Long totalTime = getDurationMillis();
        return total / ((double) Math.max(1, totalTime) / 1000);
    }

    /** 计算排除错误的 TPS */
    public double tpsExcludeErrors() {
        Long totalTime = getDurationMillis();
        return (double) (total - error.get()) / ((double) Math.max(1, totalTime) / 1000);
    }
}
