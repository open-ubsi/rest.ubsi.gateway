package rewin.rest.ubsi.gateway.policy;

import rewin.rest.ubsi.gateway.AppConfig;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 路由设置
 */
public class Router extends Base {
    public String   altService = null;
    public int      verMin = 0;
    public int      verMax = 0;
    public int      verRelease = -1;
    public int      rate = 0;

    AtomicLong  total = new AtomicLong(0);
    AtomicLong  count = new AtomicLong(0);

    static Router[] RouterList = null;      // 路由配置表

    /** 加载router配置项 */
    public static void loadRouter() throws Exception {
        Object[] res = (Object[]) Context.request(AppConfig.SERVICE_GATEWAY, "listRouter",
                AppConfig.Group, null, true, null, 0, 0).call();
        List<Router> items = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            Router item = Codec.toType(map, Router.class);
            item.check();
            if ( item.rate > 100 )
                item.rate = 100;
            if ( item.service != null && item.rate > 0 )
                items.add(item);
        }

        if ( items.isEmpty() )
            RouterList = null;
        else {
            Collections.sort(items, Base.comparator);
            RouterList = items.toArray(new Router[items.size()]);
        }
    }

    /** 检查router配置项，返回匹配项 */
    public static Router checkRouter(String app, String service, String entry) {
        Router[] items = RouterList;
        if ( items == null )
            return null;

        for ( int i = 0; i < items.length; i ++ ) {
            Router item = items[i];
            if ( item.app != null && !item.app.equals(app) )
                continue;
            if ( !item.service.equals(service) )
                continue;
            if ( !Util.matchString(entry, item.entry) )
                continue;
            long total = item.total.incrementAndGet();
            if ( total == 1 && item.rate < 100 )
                return null;
            if ( (total - 1) * item.rate <= item.count.get() * 100 )
                return null;
            item.count.incrementAndGet();
            return item;
        }
        return null;
    }

}
