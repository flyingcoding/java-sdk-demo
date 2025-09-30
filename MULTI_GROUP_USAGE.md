# 多群组并行压测 - 快速使用指南

## 快速开始

### 1. 编译项目

```bash
bash gradlew build
```

### 2. 配置 SDK

```bash
# 拷贝证书（假设节点证书在 ~/fisco/nodes/127.0.0.1/sdk）
cp -r ~/fisco/nodes/127.0.0.1/sdk/* conf/

# 拷贝并编辑配置文件
cp src/test/resources/config-example.toml conf/config.toml
# 编辑 conf/config.toml，修改节点地址等配置
```

### 3. 执行压测

#### 方法一：使用脚本（推荐）

```bash
# 赋予执行权限
chmod +x multi_group_test.sh

# 使用默认参数（group0,group1,group2，每群组8个账户，10000笔交易，QPS=100）
./multi_group_test.sh

# 自定义参数
./multi_group_test.sh "group0,group1" 16 20000 200
```

#### 方法二：直接运行 Java 命令

```bash
cd dist

# 对三个群组进行压测
java -cp 'conf/:lib/*:apps/*' \
    org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC \
    group0,group1,group2 8 10000 100

# 对两个群组进行高并发压测
java -cp 'conf/:lib/*:apps/*' \
    org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC \
    group0,group1 16 50000 500
```

## 参数说明

| 参数位置 | 参数名 | 说明 | 示例值 |
|---------|--------|------|--------|
| 1 | groupIds | 群组ID列表（逗号分隔） | `group0,group1,group2` |
| 2 | userCount | 每个群组的账户数 | `8`（建议4-32） |
| 3 | count | 每个群组的交易总数 | `10000` |
| 4 | qps | 每个群组的QPS | `100` |

## 输出示例

```
====== Multi-Group DMC Performance Test ======
Groups: group0,group1,group2
User count per group: 8
Transactions per group: 10000
QPS per group: 100
Total groups: 3
Total transactions: 30000
================================================

[Group 0: group0] Starting test...
[Group 1: group1] Starting test...
[Group 2: group2] Starting test...

... 各群组的执行过程 ...

====================================================================
==================== Multi-Group Test Summary =====================
====================================================================
Total groups tested: 3
Total transactions: 30000
Total time: 106789ms
Overall TPS: 280.93
====================================================================
```

## 性能调优建议

### 系统参数

```bash
# 增加文件描述符限制
ulimit -n 65535
```

### JVM 参数（大规模压测）

```bash
java -Xmx4g -Xms4g -XX:+UseG1GC \
    -cp 'conf/:lib/*:apps/*' \
    org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC \
    group0,group1,group2 8 10000 100
```

## 常见问题

**Q: 如何确定最佳的 QPS 和账户数？**  
A: 从小值开始（QPS=50, userCount=8），逐步增加直到达到节点性能瓶颈。

**Q: 能否对同一个群组执行多次压测？**  
A: 可以，但不建议在参数中重复群组ID，应该先完成一次压测后再执行下一次。

**Q: 某个群组失败了怎么办？**  
A: 各群组独立执行，某个群组失败不影响其他群组。检查失败群组的日志。

## 详细文档

完整使用文档请参考：[docs/MultiGroupPerformanceTest.md](docs/MultiGroupPerformanceTest.md)

## 与单群组工具对比

| 工具 | 命令 | 适用场景 |
|------|------|---------|
| 单群组 | `PerformanceDMC` | 单个群组性能测试 |
| 多群组 | `PerformanceMultiGroupDMC` | 多群组系统综合性能测试 |

## 注意事项

1. ✅ 确保所有指定的群组在区块链网络中存在
2. ✅ 配置文件中的节点连接信息正确
3. ✅ SDK 证书已正确配置
4. ✅ 根据系统资源合理设置群组数量和 QPS
5. ✅ 建议群组数量不超过 CPU 核心数
