package rewin.rest.ubsi.gateway.policy;

import rewin.rest.ubsi.gateway.AppConfig;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.RWLock;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.io.*;
import java.util.*;

/**
 * 服务接口的结果缓冲
 */
public class Cache extends Base {

    public int      timeout;        // 缓冲的超时时间(秒)

    static Cache[] CacheList = null;    // 结果缓冲的配置表

    /** 加载cache配置项 */
    public static void loadCache() throws Exception {
        Object[] res = (Object[]) Context.request(AppConfig.SERVICE_GATEWAY, "listCache",
                AppConfig.Group, null, null, true, null, 0, 0).call();
        List<Cache> list = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            Cache cache = Codec.toType(map, Cache.class);
            cache.check();
            if ( cache.timeout <= 0 )
                cache.timeout = AppConfig.CACHE_TIMEOUT;
            list.add(cache);
        }
        if ( list.isEmpty() )
            CacheList = null;
        else {
            Collections.sort(list, Base.comparator);
            CacheList = list.toArray(new Cache[list.size()]);
        }
    }

    /* 检查cache配置项 */
    static int useCache(String app, String service, String entry) {
        Cache[] list = CacheList;
        if ( list == null )
            return 0;
        for ( Cache cache : list ) {
            if ( cache.app != null && !cache.app.equals(app) )
                continue;
            if ( !Util.matchString(service, cache.service) )
                continue;
            if ( !Util.matchString(entry, cache.entry) )
                continue;
            return cache.timeout;
        }
        return 0;
    }

    /* 获得cache数据文件 */
    static String getCacheFile(String service, String entry, List params) {
        String fname = Integer.toHexString(Arrays.hashCode(Codec.encodeBytes(params)));
        return "." + File.separator + "cache" + File.separator + service + File.separator + entry + File.separator + fname;
    }

    /* 检查cache数据是否可用 */
    static Object hasCache(String fname, int timeout) throws Exception {
        File file = new File(fname);
        if (file.lastModified() < System.currentTimeMillis() - (long) timeout * 1000)
            throw new Exception();
        byte[] buf;
        try (RWLock locker = RWLock.lockRead(file.getCanonicalPath());
             InputStream is = new FileInputStream(file)) {
            buf = new byte[is.available()];
            is.read(buf);
        }
        return Codec.decodeBytes(buf);
    }

    /** 检查是否使用cache，返回：[ file_name, cache_data ] */
    public static Object[] checkCache(String app, String service, String entry, List params) {
        int timeout = useCache(app, service, entry);
        if ( timeout == 0 )
            return null;                            // 不使用缓冲
        String fname = getCacheFile(service, entry, params);
        try {
            Object res = hasCache(fname, timeout);
            return new Object[] { fname, res };     // 得到有效的缓冲
        } catch (Exception e) {
        }
        return new Object[] { fname };              // 缓冲数据无效
    }

    /** 保存cache数据文件 */
    public static void saveCache(String fname, Object data) throws Exception {
        byte[] buf = Codec.encodeBytes(data);
        File file = new File(fname);
        Util.checkFilePath(file);
        try (RWLock locker = RWLock.lockWrite(file.getCanonicalPath());
             OutputStream os = new FileOutputStream(file)) {
            os.write(buf);
        }
    }
}
