# 多群组并行压测功能 - 变更总结

## 变更日期
2025-09-30

## 变更概述
在原有 `PerformanceDMC` (多合约并行压测) 的基础上，新增了支持多群组并行压测的功能。现在可以同时对多个 FISCO BCOS 群组执行压测，每个群组有独立的统计信息。

## 新增文件

### 1. 核心代码
- **文件**: `src/main/java/org/fisco/bcos/sdk/demo/perf/PerformanceMultiGroupDMC.java`
- **说明**: 多群组并行压测工具的主类
- **功能**:
  - 支持同时对多个群组执行压测
  - 每个群组独立的账户、交易和统计
  - 并行执行，充分利用多核 CPU
  - 提供每个群组的详细报告和总体统计

### 2. 文档
- **文件**: `docs/MultiGroupPerformanceTest.md`
- **说明**: 详细的使用文档
- **内容**: 完整的功能介绍、使用方法、参数说明、输出示例、FAQ 等

- **文件**: `MULTI_GROUP_USAGE.md`
- **说明**: 快速使用指南
- **内容**: 简明的快速开始指南和常见问题

### 3. 辅助脚本
- **文件**: `multi_group_test.sh`
- **说明**: 便捷的压测启动脚本
- **功能**: 
  - 支持命令行参数配置
  - 自动检查环境
  - 友好的输出提示

## 修改文件

### 1. README.md
- **位置**: 第 24 行（功能列表部分）
- **修改**: 添加 "提供多群组并行压测Demo（新增）"

- **位置**: 第 109 行之后（压测Demo部分）
- **修改**: 添加多群组并行压测的使用说明和示例

## 功能特性

### 核心功能
1. ✅ 同时对多个群组执行 DMC 压测
2. ✅ 每个群组独立的账户和交易
3. ✅ 每个群组独立的性能统计
4. ✅ 总体性能汇总报告
5. ✅ 并行执行，资源利用率高

### 技术实现
- 使用 `ExecutorService` 并行执行各群组的压测
- 使用 `CountDownLatch` 同步等待所有群组完成
- 每个群组有独立的 `Client` 实例
- 每个群组有独立的 `Collector` 收集统计信息
- 使用进度条实时展示各群组的执行进度

### 兼容性
- ✅ 向后兼容：支持单群组压测（相当于原有功能）
- ✅ 代码独立：不影响现有的压测工具
- ✅ 编译通过：已验证 Gradle 编译成功

## 使用示例

### 基本用法
```bash
# 编译项目
bash gradlew build

# 对三个群组进行压测
cd dist
java -cp 'conf/:lib/*:apps/*' \
    org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC \
    group0,group1,group2 8 10000 100
```

### 使用脚本
```bash
# 赋予执行权限
chmod +x multi_group_test.sh

# 使用默认参数
./multi_group_test.sh

# 自定义参数
./multi_group_test.sh "group0,group1" 16 20000 200
```

## 参数说明

| 参数 | 说明 | 示例 |
|------|------|------|
| groupIds | 群组ID列表，逗号分隔 | `group0,group1,group2` |
| userCount | 每个群组的账户数量 | `8` |
| count | 每个群组的交易总量 | `10000` |
| qps | 每个群组的QPS | `100` |

## 性能优势

### 相比单群组压测
- **并行度提升**: N 个群组同时压测，理论上可达到 N 倍吞吐量
- **资源利用**: 充分利用多核 CPU 和网络带宽
- **真实场景**: 更接近多群组系统的实际使用场景

### 应用场景
1. 多群组区块链系统的综合性能测试
2. 评估节点在多群组并发场景下的性能
3. 验证系统在高负载下的稳定性
4. 对比不同群组配置的性能差异

## 代码质量

### 编译验证
```bash
$ bash gradlew compileJava --no-daemon
BUILD SUCCESSFUL in 7s
```

### 代码规范
- ✅ 遵循项目现有的代码风格
- ✅ 完整的 JavaDoc 注释
- ✅ 函数级别的功能注释（中文）
- ✅ 异常处理完善
- ✅ 资源正确释放

### 测试建议
在 Ubuntu 环境测试前需要：
1. 确保 FISCO BCOS 节点正常运行
2. 配置好多个群组（如 group0, group1, group2）
3. 正确配置 SDK 证书和网络连接
4. 根据系统资源调整参数

## 后续优化建议

### 功能增强
1. 支持配置文件方式配置各群组参数
2. 支持不同群组使用不同的 QPS
3. 添加实时监控和中断功能
4. 支持结果导出为 CSV 或 JSON 格式

### 性能优化
1. 优化线程池配置策略
2. 支持自适应 QPS 调整
3. 添加预热阶段避免冷启动影响
4. 支持分布式压测

## 相关文件清单

```
java-sdk-demo/
├── src/main/java/org/fisco/bcos/sdk/demo/perf/
│   └── PerformanceMultiGroupDMC.java          # 新增：多群组压测主类
├── docs/
│   └── MultiGroupPerformanceTest.md           # 新增：详细使用文档
├── MULTI_GROUP_USAGE.md                       # 新增：快速使用指南
├── multi_group_test.sh                        # 新增：便捷启动脚本
├── CHANGES.md                                 # 新增：本文件
└── README.md                                  # 修改：添加多群组压测说明
```

## 总结

此次变更在不影响现有功能的前提下，新增了多群组并行压测能力，使得项目能够更好地支持多群组区块链系统的性能测试需求。代码已通过编译验证，可以直接在 Ubuntu 环境中构建和测试。
