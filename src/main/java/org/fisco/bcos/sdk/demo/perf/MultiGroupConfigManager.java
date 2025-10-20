package org.fisco.bcos.sdk.demo.perf;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 多群组配置管理器
 *
 * <p>功能： 1. 支持为不同群组加载不同的配置文件 2. 管理群组到SDK实例的映射 3. 提供群组客户端的获取接口
 *
 * <p>配置文件命名规则： - 默认配置文件：config.toml - 群组特定配置：config-group{groupId}.toml
 *
 * <p>使用示例： MultiGroupConfigManager manager = new MultiGroupConfigManager(); Client client1 =
 * manager.getClient(1); // 使用 config-group1.toml 或 config.toml Client client2 =
 * manager.getClient(2); // 使用 config-group2.toml 或 config.toml
 */
public class MultiGroupConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(MultiGroupConfigManager.class);

    // 群组ID到SDK实例的映射
    private final Map<Integer, BcosSDK> groupSdkMap = new HashMap<>();
    // 群组ID到Client实例的映射
    private final Map<Integer, Client> groupClientMap = new HashMap<>();
    // 配置文件路径前缀
    private static final String CONFIG_PREFIX = "config-group";
    // 配置文件后缀
    private static final String CONFIG_SUFFIX = ".toml";
    // 默认配置文件名
    private static final String DEFAULT_CONFIG = "config.toml";

    /** 构造函数 */
    public MultiGroupConfigManager() {
        // 初始化
    }

    /**
     * 获取指定群组的客户端
     *
     * @param groupId 群组ID
     * @return 群组对应的客户端
     * @throws Exception 配置加载失败
     */
    public Client getClient(Integer groupId) throws Exception {
        // 如果已经创建过客户端，直接返回
        if (groupClientMap.containsKey(groupId)) {
            return groupClientMap.get(groupId);
        }

        // 尝试加载群组特定配置或默认配置
        BcosSDK sdk = loadSdkForGroup(groupId);
        Client client = sdk.getClient(groupId);

        // 缓存SDK和客户端实例
        groupSdkMap.put(groupId, sdk);
        groupClientMap.put(groupId, client);

        logger.info("成功创建群组 {} 的客户端连接", groupId);
        return client;
    }

    /**
     * 批量初始化多个群组的客户端
     *
     * @param groupIds 群组ID列表
     * @throws Exception 配置加载失败
     */
    public void initializeGroups(List<Integer> groupIds) throws Exception {
        for (Integer groupId : groupIds) {
            getClient(groupId);
        }
        logger.info("成功初始化 {} 个群组的客户端连接", groupIds.size());
    }

    /**
     * 为指定群组加载SDK实例
     *
     * @param groupId 群组ID
     * @return SDK实例
     * @throws Exception 配置加载失败
     */
    private BcosSDK loadSdkForGroup(Integer groupId) throws Exception {
        // 首先尝试加载群组特定配置文件
        String groupConfigName = CONFIG_PREFIX + groupId + CONFIG_SUFFIX;
        URL groupConfigUrl = this.getClass().getClassLoader().getResource(groupConfigName);

        if (groupConfigUrl != null) {
            // 找到群组特定配置文件
            logger.info("群组 {} 使用特定配置文件: {}", groupId, groupConfigName);
            return BcosSDK.build(groupConfigUrl.getPath());
        }

        // 如果没有群组特定配置，使用默认配置
        URL defaultConfigUrl = this.getClass().getClassLoader().getResource(DEFAULT_CONFIG);
        if (defaultConfigUrl != null) {
            logger.info("群组 {} 使用默认配置文件: {}", groupId, DEFAULT_CONFIG);
            return BcosSDK.build(defaultConfigUrl.getPath());
        }

        // 配置文件都不存在，抛出异常
        throw new RuntimeException(
                String.format(
                        "群组 %d 的配置文件不存在！尝试查找: %s 或 %s", groupId, groupConfigName, DEFAULT_CONFIG));
    }

    /**
     * 检查群组特定配置文件是否存在
     *
     * @param groupId 群组ID
     * @return 配置文件是否存在
     */
    public boolean hasGroupConfig(Integer groupId) {
        String groupConfigName = CONFIG_PREFIX + groupId + CONFIG_SUFFIX;
        URL groupConfigUrl = this.getClass().getClassLoader().getResource(groupConfigName);
        return groupConfigUrl != null;
    }

    /**
     * 获取配置文件路径信息（用于调试和日志）
     *
     * @param groupId 群组ID
     * @return 配置文件路径描述
     */
    public String getConfigInfo(Integer groupId) {
        if (hasGroupConfig(groupId)) {
            return CONFIG_PREFIX + groupId + CONFIG_SUFFIX;
        } else {
            return DEFAULT_CONFIG + " (默认)";
        }
    }

    /** 关闭所有SDK连接 */
    public void shutdown() {
        for (Map.Entry<Integer, BcosSDK> entry : groupSdkMap.entrySet()) {
            try {
                entry.getValue().stopAll();
                logger.info("关闭群组 {} 的SDK连接", entry.getKey());
            } catch (Exception e) {
                logger.error("关闭群组 {} 的SDK连接失败", entry.getKey(), e);
            }
        }
        groupSdkMap.clear();
        groupClientMap.clear();
    }

    /**
     * 打印配置摘要信息
     *
     * @param groupIds 群组ID列表
     */
    public void printConfigSummary(List<Integer> groupIds) {
        System.out.println("========== 多群组配置信息 ==========");
        for (Integer groupId : groupIds) {
            String configInfo = getConfigInfo(groupId);
            System.out.println("  群组 " + groupId + " : " + configInfo);
        }
        System.out.println("====================================");
    }
}
