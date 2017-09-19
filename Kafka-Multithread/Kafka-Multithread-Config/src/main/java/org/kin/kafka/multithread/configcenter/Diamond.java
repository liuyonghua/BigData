package org.kin.kafka.multithread.configcenter;

import org.kin.kafka.multithread.configcenter.common.StoreCodec;
import org.kin.kafka.multithread.configcenter.common.StoreCodecs;
import org.kin.kafka.multithread.configcenter.config.Config;
import org.kin.kafka.multithread.configcenter.config.DefaultConfig;
import org.kin.kafka.multithread.configcenter.manager.ConfigStoreManager;
import org.kin.kafka.multithread.configcenter.utils.PropertiesUtils;
import org.kin.kafka.multithread.configcenter.utils.YAMLUtils;
import org.kin.kafka.multithread.domain.ConfigFetchResult;
import org.kin.kafka.multithread.domain.ConfigSetupResult;
import org.kin.kafka.multithread.protocol.app.ApplicationHost;
import org.kin.kafka.multithread.protocol.configcenter.AdminProtocol;
import org.kin.kafka.multithread.protocol.configcenter.DiamondMasterProtocol;
import org.kin.kafka.multithread.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Created by huangjianqin on 2017/9/11.
 * 配置中心实现类
 *
 * 可以利用dubbo(目前底层的RPC)实现配置中心的服务化,可以解决配置中心挂掉无法提供服务以及通过负载均衡提高配置中心处理能力
 * 考虑实际生产环境,可能部署的kafka多线程消费应用数并不多,其实配置中心的压力不大,可以考虑不使用服务化
 *
 * 可以完美切换底层RPC(自定义实现RPCFactory并注入到RPCFactories)和配置存储(自定义实现ConfigStoreManager并在配置文件中修改class)
 */
public class Diamond implements DiamondMasterProtocol, AdminProtocol{
    private static final Logger log = LoggerFactory.getLogger(Diamond.class);
    private ConfigStoreManager configStoreManager;
    private Properties config = new Properties();
    private Map<String, Map<String, Properties>> host2AppName2Config = new HashMap<>();

    public Diamond() {
        this(DefaultConfig.DEFALUT_CONFIGPATH);
    }

    public Diamond(String configPath) {
        config = YAMLUtils.loadYML2Properties(configPath);
    }

    public void init(){
        String storeManagerClass = (String) config.getOrDefault(Config.CONFIG_STOREMANAGER_CLASS, DefaultConfig.DEFAULT_CONFIG_STOREMANAGER_CLASS);
        configStoreManager = (ConfigStoreManager) ClassUtils.instance(storeManagerClass);
        configStoreManager.setup(config);
    }

    @Override
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/kafkamultithread/{host}/{appName}/{tpye}/{config}")
    public Map<String, Object> storeConfig(
            String appName,
            String host,
            String type,
            String config) {
//        ApplicationHost appHost = new ApplicationHost(appName, host);
//        ApplicationConfig appConfig = new ApplicationConfig(config, type);
//        boolean result = configStoreManager.storeConfig(appHost, appConfig);
        //先缓存,等待判断是否配置成功后才持久化
        Map<String, Properties> appName2Config = new HashMap<>();

        if(host2AppName2Config.get(host) != null){
            appName2Config = host2AppName2Config.get(host);
        }
        StoreCodec storeCodec = StoreCodecs.getCodecByName(type);
        appName2Config.put(appName, PropertiesUtils.map2Properties(storeCodec.deSerialize(config)));
        host2AppName2Config.put(host, appName2Config);

        return Collections.singletonMap("result", 1);
    }

    /**
     * 仅仅获取持久化的配置,也就是应用的真实配置
     * @param appName
     * @param host
     * @param type
     * @return
     */
    @Override
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/kafkamultithread/{host}/{appName}/{tpye}")
    public Map<String, Object> getAppConfigStr(String appName, String host, String type) {
        ApplicationHost appHost = new ApplicationHost(appName, host);

        Map<String, String> config = configStoreManager.getAppConfigMap(appHost);
        if(config != null){
            StoreCodec storeCodec = StoreCodecs.getCodecByName(type);
            String configStr = storeCodec.serialize(config);
            Map<String, Object> result = new HashMap<>();
            result.put("result", (configStr != null && !configStr.equals(""))? 1 : 0);
            result.put("type", type);
            result.put("config", configStr);
            return result;
        }

        return Collections.singletonMap("result", 0);
    }

    @Override
    public ConfigFetchResult getAppConfig(ApplicationHost appHost) {
        ConfigFetchResult result = new ConfigFetchResult(configStoreManager.getAllAppConfig(appHost), System.currentTimeMillis());
        return result;
    }

    @Override
    public void configFail(ConfigSetupResult result) {

    }

    public void close(){
        configStoreManager.clearup();
    }

    public Properties getConfig() {
        return config;
    }

}