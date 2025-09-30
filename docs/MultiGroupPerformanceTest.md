# 多群组并行压测工具使用指南

## 功能简介

`PerformanceMultiGroupDMC` 是一个支持同时对多个 FISCO BCOS 群组进行并行压测的工具。它基于 `PerformanceDMC` (多合约并行压测) 扩展而来，能够：

- 同时对多个群组执行压测
- 每个群组有独立的账户、交易和统计信息
- 并行执行，充分利用系统资源
- 提供每个群组的详细性能报告以及总体统计

## 使用方法

### 命令格式

```bash
java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC [groupIds] [userCount] [count] [qps]
```

### 参数说明

| 参数 | 说明 | 示例 |
|------|------|------|
| groupIds | 群组ID列表，用逗号分隔 | `group0,group1,group2` |
| userCount | 每个群组创建的账户数量 | `8` (建议 4-32) |
| count | 每个群组的交易总量 | `10000` |
| qps | 每个群组的QPS（每秒查询数） | `100` |

### 使用示例

#### 示例 1：对三个群组进行压测

```bash
cd dist
java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC group0,group1,group2 8 10000 100
```

这将：
- 同时对 `group0`、`group1`、`group2` 三个群组进行压测
- 每个群组创建 8 个账户
- 每个群组执行 10000 笔交易
- 每个群组的 QPS 为 100
- 总计执行 30000 笔交易

#### 示例 2：对两个群组进行高并发压测

```bash
java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC group0,group1 16 50000 500
```

这将：
- 对 `group0` 和 `group1` 进行压测
- 每个群组创建 16 个账户
- 每个群组执行 50000 笔交易
- 每个群组的 QPS 为 500
- 总计执行 100000 笔交易

#### 示例 3：单群组压测（兼容原有功能）

```bash
java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC group0 8 10000 100
```

这将只对 `group0` 进行压测，与原有的 `PerformanceDMC` 功能相同。

## 输出说明

### 启动信息

```
====== Multi-Group DMC Performance Test ======
Groups: group0,group1,group2
User count per group: 8
Transactions per group: 10000
QPS per group: 100
Total groups: 3
Total transactions: 30000
================================================
```

### 每个群组的执行过程

```
[Group 0: group0] Starting test...
[Group 0: group0] Creating accounts...
[Group 0: group0] Accounts created!
[Group 0: group0] Sending transactions...
[G0]Send    │████████████████████│ 10000/10000 (100%)
[G0]Receive │████████████████████│ 10000/10000 (100%)
```

### 每个群组的性能报告

```
[Group 0: group0] Performance Report:
total
===================================================================
Total transactions:  10000
Total time: 105234ms
TPS(include error requests): 95.02674371302624
TPS(exclude error requests): 95.02674371302624
Avg time cost: 387ms
Errors: 0
Time group:
0    < time <  50ms   : 125   : 1.25%
50   < time <  100ms  : 1234  : 12.34%
100  < time <  200ms  : 3456  : 34.56%
200  < time <  400ms  : 2890  : 28.9%
400  < time <  1000ms : 2123  : 21.23%
1000 < time <  2000ms : 172   : 1.72%
2000 < time           : 0     : 0.0%
```

### 总体统计信息

```
====================================================================
==================== Multi-Group Test Summary =====================
====================================================================
Total groups tested: 3
Total transactions: 30000
Total time: 106789ms
Overall TPS: 280.93547839502234
====================================================================
```

## 技术实现

### 核心特性

1. **并行执行**：每个群组在独立的线程中执行压测，充分利用多核CPU
2. **独立统计**：每个群组有独立的 `Collector` 来收集性能数据
3. **资源隔离**：每个群组使用独立的 `Client` 实例和账户集
4. **进度展示**：使用进度条实时展示每个群组的发送和接收进度

### 关键流程

1. 解析群组ID列表（支持逗号分隔）
2. 创建线程池，为每个群组分配独立线程
3. 并行执行各群组的压测：
   - 创建账户
   - 发送交易
   - 统计结果
   - 验证余额
4. 汇总所有群组的结果

## 注意事项

### 前置条件

1. 确保所有指定的群组在区块链网络中已存在
2. 配置文件中的节点连接信息正确
3. SDK 证书配置正确

### 性能建议

1. **账户数量**：建议每个群组 4-32 个账户，太多会增加初始化时间，太少无法充分利用并行特性
2. **QPS 设置**：根据节点性能合理设置，避免节点过载
3. **群组数量**：建议不超过系统 CPU 核心数，避免过度竞争
4. **总交易量**：总交易量 = 单群组交易量 × 群组数量

### 常见问题

#### Q1: 某个群组压测失败，其他群组是否继续？
A: 是的，各群组独立执行，某个群组失败不影响其他群组。失败的群组会打印错误信息。

#### Q2: 如何确定最优的 QPS 和账户数？
A: 建议从较小的值开始（如 QPS=50, userCount=8），逐步增加直到达到节点性能瓶颈。

#### Q3: 总体 TPS 能否达到单群组 TPS 的 N 倍？
A: 理论上可以，但实际取决于：
- 节点的处理能力
- 网络带宽
- 系统资源（CPU、内存）
- SDK 客户端的性能

#### Q4: 可以对相同的群组执行多次吗？
A: 可以，但不建议。例如 `group0,group0,group0` 会创建三个独立的压测任务同时对 `group0` 施压，可能导致资源竞争。

## 与原有工具的对比

| 工具 | 支持群组数 | 适用场景 |
|------|-----------|---------|
| PerformanceDMC | 单群组 | 单个群组的性能测试 |
| PerformanceMultiGroupDMC | 多群组 | 多群组系统的综合性能测试 |

## 示例脚本

创建一个便捷的压测脚本 `multi_group_test.sh`：

```bash
#!/bin/bash

# 多群组压测脚本
GROUPS="group0,group1,group2"
USER_COUNT=8
TRANSACTION_COUNT=10000
QPS=100

cd dist

echo "开始多群组压测..."
echo "群组: $GROUPS"
echo "每群组账户数: $USER_COUNT"
echo "每群组交易数: $TRANSACTION_COUNT"
echo "QPS: $QPS"
echo ""

java -cp 'conf/:lib/*:apps/*' \
    org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC \
    $GROUPS \
    $USER_COUNT \
    $TRANSACTION_COUNT \
    $QPS

echo ""
echo "压测完成！"
```

使用方式：

```bash
chmod +x multi_group_test.sh
./multi_group_test.sh
```

## 进阶配置

### 自定义线程池大小

代码中默认使用 `CPU核心数 × 2` 作为线程池大小。如需调整，可以修改源码中的：

```java
ThreadPoolService threadPoolService =
    new ThreadPoolService("MultiGroupDMCClient", Runtime.getRuntime().availableProcessors() * 2);
```

### 调整随机数种子

每个群组使用不同的随机数种子以确保数据分布不同：

```java
random.setSeed(System.currentTimeMillis() + groupIndex);
```

## 性能优化建议

1. **增加文件描述符限制**
   ```bash
   ulimit -n 65535
   ```

2. **调整 JVM 参数**
   ```bash
   java -Xmx4g -Xms4g -XX:+UseG1GC -cp 'conf/:lib/*:apps/*' \
       org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC ...
   ```

3. **网络优化**
   - 使用千兆或万兆网络
   - 确保客户端与节点之间网络延迟低

4. **系统资源监控**
   - 使用 `top` 或 `htop` 监控 CPU 使用率
   - 使用 `iotop` 监控磁盘 I/O
   - 使用 `nethogs` 监控网络流量
