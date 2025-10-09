#!/bin/bash

# FISCO BCOS 快速并行压测脚本
# 使用说明：./quick-perf.sh [groupId] [userCount] [transferCount] [tps]

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║        FISCO BCOS 并行转账快速压测启动脚本                    ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

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

# 如果没有参数，使用快速模式
if [ $# -eq 0 ]; then
    echo "🚀 使用快速模式（默认参数）"
    echo "   - 群组ID: 1"
    echo "   - 用户数量: 500"
    echo "   - 转账交易数: 5000"
    echo "   - 目标TPS: 100"
    echo ""
    echo "提示：也可以指定参数 ./quick-perf.sh [groupId] [userCount] [transferCount] [tps]"
    echo ""
    java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.QuickParallelPerf
else
    echo "🚀 使用自定义参数"
    echo "   - 群组ID: $1"
    echo "   - 用户数量: $2"
    echo "   - 转账交易数: $3"
    echo "   - 目标TPS: $4"
    echo ""
    java -cp 'conf/:lib/*:apps/*' org.fisco.bcos.sdk.demo.perf.QuickParallelPerf "$@"
fi
