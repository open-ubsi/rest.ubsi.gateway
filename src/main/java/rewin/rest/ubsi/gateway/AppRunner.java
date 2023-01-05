package rewin.rest.ubsi.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rewin.rest.ubsi.gateway.policy.*;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.consumer.Logger;
import rewin.ubsi.consumer.Register;
import rewin.ubsi.container.Bootstrap;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用初始化
 */
@Component
@EnableScheduling
public class AppRunner implements ApplicationRunner {

    final static String LOG_APPTAG = "rewin.rest.ubsi.gateway";

    public static Logger log;

    static long startTimestamp = 0; // 启动时间戳

    @Value("${server.port}")        // 监听端口号
    int port;
    @Value("${server.servlet.context-path}")        // URL路径
    String path;
    @Value("${ug.group}")           // 网关分组
    String group;
    @Value("${ug.token-expire}")    // 应用令牌的过期时间(分钟数)
    int tokenExpire;
    @Value("${ug.acl.remote}")      // 默认的远程主机访问权限
    boolean aclRemote;
    @Value("${ug.acl.service}")     // 默认的服务访问权限
    boolean aclService;
    @Value("${ug.token.check}")     // 是否验证Token的合法性(Token的值就是AppID)
    boolean tokenCheck;

    /** 在SpringBoot项目启动后开始执行 */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        AppConfig.Group = group;
        AppConfig.Port = Bootstrap.resolvePort(port);
        AppConfig.Path = path;
        AppConfig.TokenExpire = tokenExpire;
        AppConfig.AclRemote = aclRemote;
        AppConfig.AclService = aclService;
        AppConfig.TokenCheck = tokenCheck;
        AppConfig.RunPath = (new File(".")).getCanonicalPath();

        AppConfig.Host = InetAddress.getLocalHost().getHostName();
        Context.setLogApp(AppConfig.Host + "#" + AppConfig.Port + AppConfig.Path, LOG_APPTAG);
        Context.startup(AppConfig.RunPath);       // 启动ubsi consumer
        AppConfig.Host = Bootstrap.resolveHost();
        Context.setLogApp( AppConfig.Host + "#" + AppConfig.Port + AppConfig.Path, LOG_APPTAG);
        log = Context.getLogger(LOG_APPTAG, AppConfig.Group);

        if (JedisUtil.isInited()) {
            // 配置变更的消息通知
            new JedisUtil.Listener() {
                @Override
                public void onMessage(String channel, Object msg) throws Exception {
                    if ( msg == null || !(msg instanceof String) )
                        return;
                    String group = null;
                    String action = (String)msg;
                    int index = action.lastIndexOf('#');
                    if ( index >= 0 ) {
                        if ( index > 0 )
                            group = action.substring(0, index);
                        action = action.substring(index + 1);
                    }
                    if ( group != null && !AppConfig.Group.equals(group) )
                        return;
                    try {
                        if ( AppConfig.RACL.equals(action) )
                            Acl.loadRAcl();
                        else if ( AppConfig.SACL.equals(action) )
                            Acl.loadSAcl();
                        else if ( AppConfig.MOCK.equals(action) )
                            Mock.loadMock();
                        else if ( AppConfig.CACHE.equals(action) )
                            Cache.loadCache();
                        else if ( AppConfig.LOG.equals(action) )
                            LogSet.loadLogSet();
                        else if ( AppConfig.RATE.equals(action) )
                            Rate.loadRate();
                        else if ( AppConfig.FLOW.equals(action) )
                            Flow.loadFlow();
                        else if ( AppConfig.ROUTER.equals(action) )
                            Router.loadRouter();
                    } catch (Exception e) {
                        log.error("notify:" + msg, e);
                    }
                }
                @Override
                public void onEvent(String channel, Object event) throws Exception {
                }
            }.subscribe(AppConfig.PUB_CHANNEL);
        }

        // 读取配置参数
        try {
            Acl.loadRAcl();
            Acl.loadSAcl();

            Mock.loadMock();
            Cache.loadCache();
            LogSet.loadLogSet();

            Rate.loadRate();
            Flow.loadFlow();
            Router.loadRouter();
        } catch (Exception e) {
            Context.shutdown();
            throw e;
        }

        // 初始化全部完成
        startTimestamp = System.currentTimeMillis();
        log.info("startup", "rate_limit_policy:" + (Rate.isLocal()?"LOCAL":"GLOBAL"));
    }

    static long heartFailTime = 0;  // 心跳失败报告的时间戳

    /** 定时任务，每10秒执行一次（单线程），网关心跳信号 */
    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        try {
            if ( startTimestamp == 0 )
                return;     // 应用还未完成初始化
            // 获取请求统计
            Map<String, Map<String, Register.Statistics>> statistics = Context.getStatistics();
            Map req = new HashMap<>();
            for ( String srv : statistics.keySet() ) {
                Map<String, Register.Statistics> statis = statistics.get(srv);
                Map map = new HashMap();
                for ( String entry : statis.keySet() ) {
                    Register.Statistics stat = statis.get(entry);
                    map.put(entry, stat.request);
                }
                req.put(srv, map);
            }
            // 发送心跳报告
            Context context = Context.request(AppConfig.SERVICE_GATEWAY, "activeGate",
                    AppConfig.Group, AppConfig.Port, startTimestamp, req, AppConfig.Path);
            context.callAsync(null, false);
        } catch (Exception e) {
            long t = System.currentTimeMillis();
            if ( t - heartFailTime > AppConfig.HEART_FAIL_TIMEOUT ) {
                log.warn("heartbeat", e.toString());      // 报告心跳失败
                heartFailTime = t;
            }
        }
    }

}
