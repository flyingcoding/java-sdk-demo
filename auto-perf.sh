#!/bin/bash

# FISCO BCOS 自动化并行压测脚本（详细模式）
# 使用说明：./auto-perf.sh [groupId] [userCount] [transferCount] [tps]

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║      FISCO BCOS 自动化并行压测启动脚本（详细模式）            ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# 检查参数
if [ $# -lt 4 ]; then
    echo "使用方法："
    echo "  ./auto-perf.sh [groupId] [userCount] [transferCount] [tps]"
    echo ""
    echo "参数说明："
    echo "  groupId        : 群组ID（如：1）"
    echo "  userCount      : 生成的用户数量（建议：100-10000）"
    echo "  transferCount  : 转账交易总数（如：10000）"
    echo "  tps            : 目标TPS/QPS（如：100）"
    echo ""
    echo "示例："
    echo "  ./auto-perf.sh 1 1000 10000 100"
    echo ""
    echo "推荐配置："
    echo "  轻量测试：./auto-perf.sh 1 100 1000 50"
    echo "  中等测试：./auto-perf.sh 1 1000 10000 100"
    echo "  重度测试：./auto-perf.sh 1 5000 50000 500"
    echo ""
    exit 1
fi

# 检查配置文件
if [ ! -f "dist/conf/config.toml" ]; then
    echo "❌ 错误：配置文件 conf/config.toml 不存在"
    echo ""
    echo "请执行以下步骤："
    echo "  1. 拷贝证书：cp -r ~/fisco/nodes/127.0.0.1/sdk/* conf/"
    echo "  2. 创建配置：cp conf/config-example.toml conf/config.toml"
    echo "  3. 修改配置：编辑 conf/config.toml，设置正确的节点地址"
    echo ""
    exit 1
fi

# 检查证书文件
if [ ! -f "dist/conf/ca.crt" ] && [ ! -f "dist/conf/gm/gmca.crt" ]; then
    echo "❌ 警告：未找到证书文件"
    echo "请确保已拷贝节点证书到 conf/ 目录"
    echo ""
fi

cd dist

echo "🚀 启动压测"
echo "   - 群组ID: $1"
echo "   - 用户数量: $2"
echo "   - 转账交易数: $3"
echo "   - 目标TPS: $4"
echo ""

java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.AutoParallelOkPerf "$@"
