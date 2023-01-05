package rewin.rest.ubsi.gateway.policy;

import rewin.rest.ubsi.gateway.AppConfig;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import java.net.InetAddress;
import java.util.*;

/**
 * ACL配置项
 */
public class Acl {

    /* 远程主机的访问权限 */
    public static class RAcl {
        public boolean      group;      // 是否指定的group
        public Set<Integer> addr;       // 远程主机地址的Hash，null表示不限
        public Set<String>  apps;       // 指定的应用，null表示不限
        public boolean      auth;       // 是否允许访问
    }

    static RAcl[] RAcls = null;         // 远程主机的访问控制表

    /** 加载RAcl配置项 */
    public static void loadRAcl() throws Exception {
        Object[] res = (Object[])Context.request(AppConfig.SERVICE_GATEWAY, "listRAcl",
                AppConfig.Group, null, null, true, null, 0, 0).call();
        List<RAcl> acls = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            RAcl acl = new RAcl();
            acl.group = map.get("group") != null;
            String remote = Util.checkEmpty((String)map.get("remote"));
            if ( remote != null ) {
                acl.addr = new HashSet<>();
                InetAddress[] addrs = InetAddress.getAllByName(remote);
                for (InetAddress addr : addrs)
                    acl.addr.add(Arrays.hashCode(addr.getAddress()));
            }
            List<String> apps = (List)map.get("apps");
            if ( apps != null ) {
                acl.apps = new HashSet<>();
                acl.apps.addAll(apps);
            }
            acl.auth = (Boolean)map.getOrDefault("auth", AppConfig.AclRemote);
            acls.add(acl);
        }

        if ( acls.isEmpty() )
            RAcls = null;
        else {
            Collections.sort(acls, (a, b) -> {
                if (a.group && !b.group)
                    return -1;
                if (!a.group && b.group)
                    return 1;
                if (a.addr != null && b.addr == null)
                    return -1;
                if (a.addr == null && b.addr != null)
                    return 1;
                if (a.apps != null && b.apps == null)
                    return -1;
                if (a.apps == null && b.apps != null)
                    return 1;
                if (a.apps == null && b.apps == null)
                    return 0;
                return b.apps.size() - a.apps.size();
            });
            RAcls = acls.toArray(new RAcl[acls.size()]);
        }
    }

    /** 检查RAcl配置项 */
    public static boolean checkRAcl(String ip, String app) {
        RAcl[] racls = RAcls;
        if ( racls == null || ip.isEmpty() )
            return AppConfig.AclRemote;

        try {
            InetAddress addr = InetAddress.getByName(ip);
            int hash = Arrays.hashCode(addr.getAddress());
            for (int i = 0; i < racls.length; i++) {
                RAcl acl = racls[i];
                if (acl.addr != null && !acl.addr.contains(hash))
                    continue;
                if (acl.apps != null && !acl.apps.contains(app))
                    continue;
                return acl.auth;
            }
        } catch (Exception e) {
        }
        return AppConfig.AclRemote;
    }

    // ============================================================

    /* 服务的访问权限 */
    public static class SAcl extends Base {
        public boolean      auth = AppConfig.AclService;       // 是否允许访问
    }

    static SAcl[] SAcls = null;         // 服务的访问控制表

    /** 加载SAcl配置项 */
    public static void loadSAcl() throws Exception {
        Object[] res = (Object[])Context.request(AppConfig.SERVICE_GATEWAY, "listSAcl",
                AppConfig.Group, null, true, null, 0, 0).call();
        List<SAcl> acls = new ArrayList<>();
        for ( Map map : (List<Map>)res[1] ) {
            SAcl acl = Codec.toType(map, SAcl.class);
            acl.check();
            acls.add(acl);
        }

        if ( acls.isEmpty() )
            SAcls = null;
        else {
            Collections.sort(acls, Base.comparator);
            SAcls = acls.toArray(new SAcl[acls.size()]);
        }
    }

    /** 检查SAcl配置项 */
    public static boolean checkSAcl(String app, String service, String entry) {
        SAcl[] sacls = SAcls;
        if ( sacls == null )
            return AppConfig.AclService;

        for ( int i = 0; i < sacls.length; i ++ ) {
            SAcl acl = sacls[i];
            if ( acl.app != null && !acl.app.equals(app) )
                continue;
            if ( !Util.matchString(service, acl.service) )
                continue;
            if ( !Util.matchString(entry, acl.entry) )
                continue;
            return acl.auth;
        }
        return AppConfig.AclService;
    }

}
