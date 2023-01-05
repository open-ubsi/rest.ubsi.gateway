package rewin.rest.ubsi.gateway.policy;

import rewin.ubsi.common.Util;

import java.util.Comparator;

/**
 * 基础的配置数据
 */
public class Base {
    public  String  group;
    public  String  app;
    public  String  service;
    public  String  entry;

    void check() {
        group = Util.checkEmpty(group);
        app = Util.checkEmpty(app);
        service = Util.checkEmpty(service);
        entry = Util.checkEmpty(entry);
    }

    static int compString(String a, String b) {
        if ( a == null && b == null )
            return 0;
        if ( a != null && b != null ) {
            int la = a.length();
            int lb = b.length();
            if ( la != lb )
                return lb - la;     // 更"长"的在前面
            int comp = b.compareTo(a);
            if ( comp != 0 )
                return comp;        // 更"大"的在前面，".*"排在"aAzZ019"的后面
        }
        return a != null ? -1 : 1;
    }

    static Comparator<? super Base> comparator = new Comparator<Base>() {
        @Override
        public int compare(Base a, Base b) {
            int res = compString(a.group, b.group);
            if ( res != 0 )
                return res;
            res = compString(a.app, b.app);
            if ( res != 0 )
                return res;
            res = compString(a.service, b.service);
            if ( res != 0 )
                return res;
            return compString(a.entry, b.entry);
        }
    };
}
