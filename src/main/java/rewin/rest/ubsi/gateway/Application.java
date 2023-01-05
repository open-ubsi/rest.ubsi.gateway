package rewin.rest.ubsi.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

import javax.servlet.http.HttpServletRequest;

@SpringBootApplication(
        exclude = {		// 取消MongoDB/Jedis的自动配置
                MongoAutoConfiguration.class,
                MongoDataAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        }
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /** 获得访问者IP地址 */
    public static String getRemoteIP(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("Proxy-Client-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getHeader("WL-Proxy-Client-IP");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip))
            ip = request.getRemoteAddr();

        if (ip == null)
            ip = "";
        else if (ip.indexOf(',') >= 0) {
            String[] xip = ip.split(",");
            ip = "";
            for (int i = 0; i < xip.length; i++) {
                String x = xip[i];
                if (x.isEmpty() || "unknown".equalsIgnoreCase(x))
                    continue;
                ip = x;
                break;
            }
        }
        return ip;
    }
}
