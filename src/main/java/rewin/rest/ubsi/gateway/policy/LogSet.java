package rewin.rest.ubsi.gateway.policy;

import rewin.rest.ubsi.gateway.AppConfig;
import rewin.rest.ubsi.gateway.Gateway;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 日志设置
 */
public class LogSet extends Base {
    public int log = 0;            // 0:不记录，0x01:正常转发的请求，0x02:被cache/mock处理的请求，0x04:被拒绝的请求，0x08:发生异常的请求，0xF0表示按位对应的请求需要记录参数
    public boolean trace = false;

    static LogSet[] LogList = null;     // 日志配置项

    /** 加载log配置项 */
    public static void loadLogSet() throws Exception {
        Object[] res = (Object[]) Context.request(AppConfig.SERVICE_GATEWAY, "listLog",
                AppConfig.Group, null, true, null, 0, 0).call();
        List<LogSet> items = new ArrayList<>();
        for (Map map : (List<Map>) res[1]) {
            LogSet item = Codec.toType(map, LogSet.class);
            item.check();
            items.add(item);
        }

        if (items.isEmpty())
            LogList = null;
        else {
            Collections.sort(items, Base.comparator);
            LogList = items.toArray(new LogSet[items.size()]);
        }
    }

    /** 检查log配置项，返回匹配项 */
    public static LogSet checkLogSet(String app, String service, String entry) {
        LogSet res = new LogSet();
        LogSet[] items = LogList;
        if (items == null)
            return res;

        for (int i = 0; i < items.length; i++) {
            LogSet item = items[i];
            if (item.app != null && !item.app.equals(app))
                continue;
            if (!Util.matchString(service, item.service))
                continue;
            if (!Util.matchString(entry, item.entry))
                continue;
            res.log |= item.log;
            res.trace = res.trace || item.trace;
        }
        return res;
    }

    /* 日志数据 */
    public static class LogData {
        public String   group;
        public String   gate;
        public String   remote;
        public String   app;
        public String   service;
        public String   entry;
        public List     params;
        public String   reqId;
        public int      result;
        public String   error;
        public int      source;
        public long     time;
        public int      elapse;
    }

    /* 掩码定义 */
    static Map<Integer, Integer> LogMask = Util.toMap(
            AppConfig.FROM_LOCAL,           AppConfig.LOG_MASK_LOCAL,
            AppConfig.FROM_CACHE,           AppConfig.LOG_MASK_CACHE,
            AppConfig.FROM_MOCK,            AppConfig.LOG_MASK_MOCK,
            AppConfig.FROM_REMOTE_SERVICE,  AppConfig.LOG_MASK_SERVICE,
            AppConfig.FROM_DISCARD_SERVICE, AppConfig.LOG_MASK_SERVICE,
            AppConfig.FROM_REMOTE_GATEWAY,  AppConfig.LOG_MASK_GATEWAY,
            AppConfig.FROM_DISCARD_GATEWAY, AppConfig.LOG_MASK_GATEWAY
    );

    /** 输出访问日志 */
    public static void saveLog(Gateway.RestRequest rest, Gateway.ResultEx res) throws Exception {
        if ( (rest.logs.log & 0xff) == 0 )
            return;

        Integer mask = LogMask.get(res.from);
        if ( mask == null )
            return;
        if ( (rest.logs.log & mask) == 0 )
            return;

        LogData ld = new LogData();
        ld.group = AppConfig.Group;
        ld.gate = "#" + AppConfig.Port;
        ld.remote = rest.remote;
        ld.app = rest.app;
        ld.service = rest.service;
        ld.entry = rest.entry;
        ld.params = (rest.logs.log & (mask<<8)) != 0 ? rest.params : null;
        ld.reqId = rest.reqId;
        ld.result = res.code;
        ld.error = res.code == AppConfig.OK ? null : (String)res.data;
        ld.source = res.from;
        ld.time = rest.timestamp;
        ld.elapse = res.time;
        Context.request(AppConfig.SERVICE_GATEWAY, "addLogs", ld).callAsync(null, false);
    }

}
