package rewin.rest.ubsi.gateway.policy;

import rewin.rest.ubsi.gateway.AppConfig;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 服务接口的结果仿真
 */
public class Mock {

    public String   app;            // 应用ID，null表示不限
    public String   service;        // 服务名字
    public String   entry;          // 接口名字
    public Object   data;           // 仿真数据

    static Mock[] MockList = null;  // 仿真数据的配置表

    /** 加载mock配置项 */
    public static void loadMock() throws Exception {
        Object[] res = (Object[]) Context.request(AppConfig.SERVICE_GATEWAY, "listMock",
                AppConfig.Group, null, null, true, Util.toList("app", -1), 0, 0).call();
        List<Mock> list = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            Mock mock = new Mock();
            mock.app = Util.checkEmpty((String)map.get("app"));
            mock.service = Util.checkEmpty((String)map.get("service"));
            mock.entry = Util.checkEmpty((String)map.get("entry"));
            mock.data = map.get("data");
            if ( mock.service != null && mock.entry != null )
                list.add(mock);
        }
        MockList = list.isEmpty() ? null : list.toArray(new Mock[list.size()]);
    }

    /** 检查是否使用mock */
    public static Object[] checkMock(String app, String service, String entry) {
        Mock[] list = MockList;
        if ( list == null )
            return null;
        for ( Mock mock : list ) {
            if ( mock.app != null && !mock.app.equals(app) )
                continue;
            if ( !mock.service.equals(service) || !mock.entry.equals(entry) )
                continue;
            return new Object[] { mock.data };
        }
        return null;
    }

}
