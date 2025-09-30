#!/bin/bash

# 多群组并行压测脚本
# 用于快速启动多群组DMC性能测试

# 配置参数
GROUPS="${1:-group0,group1,group2}"
USER_COUNT="${2:-8}"
TRANSACTION_COUNT="${3:-10000}"
QPS="${4:-100}"

echo "======================================================"
echo "         FISCO BCOS 多群组并行压测工具"
echo "======================================================"
echo "群组列表: $GROUPS"
echo "每群组账户数: $USER_COUNT"
echo "每群组交易数: $TRANSACTION_COUNT"
echo "每群组QPS: $QPS"
echo "======================================================"
echo ""

# 检查 dist 目录是否存在
if [ ! -d "dist" ]; then
    echo "错误: dist 目录不存在，请先执行编译:"
    echo "  bash gradlew build"
    exit 1
fi

# 检查配置文件
if [ ! -f "dist/conf/config.toml" ]; then
    echo "警告: 配置文件 dist/conf/config.toml 不存在"
    echo "请确保已正确配置 SDK"
    exit 1
fi

cd dist

echo "开始执行压测..."
echo ""

# 执行压测
java -cp 'conf/:lib/*:apps/*' \
    org.fisco.bcos.sdk.demo.perf.PerformanceMultiGroupDMC \
    "$GROUPS" \
    "$USER_COUNT" \
    "$TRANSACTION_COUNT" \
    "$QPS"

RESULT=$?

echo ""
if [ $RESULT -eq 0 ]; then
    echo "======================================================"
    echo "               压测完成！"
    echo "======================================================"
else
    echo "======================================================"
    echo "            压测失败，错误码: $RESULT"
    echo "======================================================"
fi

exit $RESULT
