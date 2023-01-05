package rewin.rest.ubsi.gateway.policy;

import org.springframework.core.io.ClassPathResource;
import redis.clients.jedis.Jedis;
import rewin.rest.ubsi.gateway.AppConfig;
import rewin.rest.ubsi.gateway.AppRunner;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.JedisUtil;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * 请求限流
 */
public class Rate extends Base {

    public int      limit = 0;          // 最大数量
    public int      seconds = 0;        // 秒数

    String getKey() {
        return (group == null ? "" : group) + "#" +
                (app == null ? "" : app) + "#" +
                service + "#" + entry;
    }

    static Rate[]   RateList = null;        // 限流的配置表
    static String   ScriptSha = null;       // Redis Lua脚本的散列值

    /* 令牌桶 */
    static class Bucket {
        int     count;
        long    time;
    }

    static Map<String, Bucket> TokenBucket = new HashMap<>();   // 本机令牌桶

    /** 加载限流配置 */
    public static void loadRate() throws Exception {
        Object[] res = (Object[]) Context.request(AppConfig.SERVICE_GATEWAY, "listRate",
                AppConfig.Group, null, true, null, 0, 0).call();
        List<Rate> list = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            Rate rate = Codec.toType(map, Rate.class);
            rate.check();
            if ( rate.limit > 0 && rate.seconds > 0 )
                list.add(rate);
        }
        if ( list.isEmpty() )
            RateList = null;
        else {
            Collections.sort(list, Base.comparator);
            RateList = list.toArray(new Rate[list.size()]);
        }

        // 加载RedisLua脚本
        if ( ScriptSha == null && JedisUtil.isInited() ) {
            ClassPathResource cpr = new ClassPathResource("templates" + File.separator + "bucket.lua");
            try (InputStream is = cpr.getInputStream();
                 Jedis jedis = JedisUtil.getJedis()) {
                byte[] buf = new byte[is.available()];
                is.read(buf);
                String script = new String(buf, "utf-8");
                ScriptSha = jedis.scriptLoad(script);
            } catch (Exception e) {
                AppRunner.log.error("load redis script", e);
            }
        }
    }

    /** 检查限流配置，返回：0:通过，-1:Local不通过，1:Global不通过 */
    public static int checkRate(String app, String service, String entry) {
        Rate[] list = RateList;
        if ( list == null )
            return 0;
        List<Rate> rules = new ArrayList<>();
        for ( Rate rate : list ) {
            if ( rate.app != null && !rate.app.equals(app) )
                continue;
            if ( !Util.matchString(service, rate.service) )
                continue;
            if ( !Util.matchString(entry, rate.entry) )
                continue;
            rules.add(rate);
        }
        if ( rules.isEmpty() )
            return 0;
        if ( isLocal() )
            return checkBucketLocal(rules);
        return checkBucketRedis(rules);
    }

    /** 检测是否采用本地令牌桶 */
    public static boolean isLocal() {
        return !(JedisUtil.isInited() && ScriptSha != null);
    }

    /* 检查本地令牌桶 */
    static synchronized int checkBucketLocal(List<Rate> rules) {
        List<Bucket> list = new ArrayList<>();
        long t = System.currentTimeMillis();
        for ( Rate rate : rules ) {
            String key = rate.getKey();
            Bucket bucket = TokenBucket.get(key);
            if ( bucket == null ) {
                bucket = new Bucket();
                bucket.count = rate.limit;
                bucket.time = t;
                TokenBucket.put(key, bucket);
            } else if ( bucket.time + (long)rate.seconds * 1000 <= t ) {
                bucket.count = rate.limit;
                bucket.time = t;
            } else if ( bucket.count <= 0 )
                return -1;
            list.add(bucket);
        }
        for ( Bucket bucket : list )
            bucket.count --;
        return 0;
    }

    /* 检查分布式令牌桶 */
    static int checkBucketRedis(List<Rate> rules) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        args.add("" + System.currentTimeMillis());
        for ( Rate rate : rules ) {
            keys.add(AppConfig.REDIS_RATEKEY + rate.getKey());
            args.add("" + rate.limit);
            args.add("" + ((long)rate.seconds * 1000));
        }
        try (Jedis jedis = JedisUtil.getJedis()) {
            int res = ((Number)jedis.evalsha(ScriptSha, keys, args)).intValue();
            return res > 0 ? 0 : 1;
        } catch (Exception e) {
            AppRunner.log.error("run redis script", e);
        }
        return checkBucketLocal(rules);
    }

}
