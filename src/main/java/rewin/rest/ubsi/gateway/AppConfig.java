package rewin.rest.ubsi.gateway;

/**
 * 运行参数
 */
public class AppConfig {

    public static final String SERVICE_GATEWAY = "rewin.ubsi.gateway";  // 网关配置服务的名字
    public static final String HEADER_TOKEN = "token";                  // Token的Header名字
    public static final String REDIS_RATEKEY = "_ugtb_";                // Redis令牌桶的关键字前缀

    public static final long HEART_FAIL_TIMEOUT = 600 * 1000;           // 心跳失败报告的超时时间(10分钟)
    public static final long TOKEN_TIME_FLOATING = 60 * 1000;           // 应用认证时间戳的浮动范围(60秒)
    public static final int  CACHE_TIMEOUT = 600;                       // 接口缓冲数据的超时时间(10分钟)

    public static final int OK = 0;
    public static final int AUTH_TIME = -1;
    public static final int AUTH_APP = -2;
    public static final int AUTH_STATUS = -3;
    public static final int AUTH_KEY = -4;
    public static final int TOKEN_MISS = -1000;
    public static final int TOKEN_UNKNOW = -1001;
    public static final int TOKEN_EXPIRED = -1002;
    public static final int TOKEN_APP = -1003;
    public static final int TOKEN_INVALID = -1004;
    public static final int ACL_REMOTE = -1010;
    public static final int ACL_SERVICE = -1011;
    public static final int RATE_LOCAL = -1020;
    public static final int RATE_GLOBAL = -1021;
    public static final int DEAL_EXCEPTION = -1030;
    public static final int REST_FORWARD = 1000;

    public static final int FROM_LOCAL = 0;
    public static final int FROM_REMOTE_SERVICE = 1;
    public static final int FROM_REMOTE_GATEWAY = 2;
    public static final int FROM_CACHE = 3;
    public static final int FROM_MOCK = 4;
    public static final int FROM_DISCARD_SERVICE = -1;
    public static final int FROM_DISCARD_GATEWAY = -2;

    public static final int LOG_MASK_SERVICE = 0x01;
    public static final int LOG_MASK_GATEWAY = 0x02;
    public static final int LOG_MASK_CACHE = 0x04;
    public static final int LOG_MASK_MOCK = 0x08;
    public static final int LOG_MASK_LOCAL = 0x10;

    final static String PUB_CHANNEL = "_ubsi_gateway_";     // 广播频道的前缀
    final static String RACL = "RACL";
    final static String SACL = "SACL";
    final static String FLOW = "FLOW";
    final static String RATE = "RATE";
    final static String ROUTER = "ROUTER";
    final static String LOG = "LOG";
    final static String CACHE = "CACHE";
    final static String MOCK = "MOCK";

    public static String    Host;           // 主机名
    public static int       Port;           // Web Server的监听端口
    public static String    Path;           // URL路径
    public static String    Group;          // 网关的分组
    public static int       TokenExpire;    // 应用令牌的过期时间(分钟数)
    public static boolean   AclRemote;      // 默认的远程主机访问权限
    public static boolean   AclService;     // 默认的服务访问权限
    public static boolean   TokenCheck;     // 是否验证Token的合法性(Token的值就是AppID)
    public static String    RunPath;        // 运行目录

}
