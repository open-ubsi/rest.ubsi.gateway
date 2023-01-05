package rewin.rest.ubsi.gateway.policy;

import rewin.rest.ubsi.gateway.AppConfig;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流量转发或镜像
 */
public class Flow extends Base {
    public int          rate = 0;
    public List<String> forward;
    public List<String> mirror;

    AtomicLong  f_total = new AtomicLong(0);
    AtomicLong  f_count = new AtomicLong(0);
    AtomicLong  f_index = new AtomicLong(0);
    AtomicLong  m_total = new AtomicLong(0);
    AtomicLong  m_count = new AtomicLong(0);

    static Flow[] FlowList = null;      // 流量策略配置表

    /** 加载flow配置项 */
    public static void loadFlow() throws Exception {
        Object[] res = (Object[]) Context.request(AppConfig.SERVICE_GATEWAY, "listFlow",
                AppConfig.Group, null, true, null, 0, 0).call();
        List<Flow> flows = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            Flow flow = Codec.toType(map, Flow.class);
            flow.check();
            if ( flow.rate > 100 )
                flow.rate = 100;
            if ( flow.rate > 0 )
                flows.add(flow);
        }

        if ( flows.isEmpty() )
            FlowList = null;
        else {
            Collections.sort(flows, Base.comparator);
            FlowList = flows.toArray(new Flow[flows.size()]);
        }
    }

    /** 检查forward配置项，返回URL */
    public static String checkForward(String app, String service, String entry) {
        Flow[] flows = FlowList;
        if ( flows == null )
            return null;

        for ( int i = 0; i < flows.length; i ++ ) {
            Flow flow = flows[i];
            if ( flow.app != null && !flow.app.equals(app) )
                continue;
            if ( !Util.matchString(service, flow.service) )
                continue;
            if ( !Util.matchString(entry, flow.entry) )
                continue;
            if ( flow.forward == null || flow.forward.isEmpty() )
                continue;
            long total = flow.f_total.incrementAndGet();
            if ( total == 1 && flow.rate < 100 )
                return null;
            if ( (total - 1) * flow.rate <= flow.f_count.get() * 100 )
                return null;
            flow.f_count.incrementAndGet();
            long index = flow.f_index.getAndIncrement();
            return flow.forward.get((int)(index % flow.forward.size()));
        }
        return null;
    }

    /** 检查mirror配置项，返回URLs */
    public static Set<String> checkMirror(String app, String service, String entry) {
        Flow[] flows = FlowList;
        Set<String> res = new HashSet<>();
        if ( flows == null )
            return res;

        for ( int i = 0; i < flows.length; i ++ ) {
            Flow flow = flows[i];
            if ( flow.app != null && !flow.app.equals(app) )
                continue;
            if ( !Util.matchString(service, flow.service) )
                continue;
            if ( !Util.matchString(entry, flow.entry) )
                continue;
            if ( flow.mirror == null || flow.mirror.isEmpty() )
                continue;
            long total = flow.m_total.incrementAndGet();
            if ( total == 1 && flow.rate < 100 )
                continue;
            if ( (total - 1) * flow.rate <= flow.m_count.get() * 100 )
                continue;
            flow.m_count.incrementAndGet();
            res.addAll(flow.mirror);
        }
        return res;
    }

}
