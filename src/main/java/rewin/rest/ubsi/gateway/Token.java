package rewin.rest.ubsi.gateway;

import com.google.gson.Gson;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.web.bind.annotation.*;
import rewin.ubsi.cli.Request;
import rewin.ubsi.common.Codec;
import rewin.ubsi.common.Crypto;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * 应用认证接口
 */
@RestController
@Api(tags = "认证接口")
public class Token {

    /* 应用注册数据 */
    static class APP {
        public int      status;
        public byte[]   key;
    }
    /* 令牌中的业务数据 */
    static class Body {
        public String   app;
        public long     exp;
    }

    static ConcurrentSkipListSet<Integer> TokenSet = new ConcurrentSkipListSet();   // 已验证令牌的Hash值缓冲

    @ApiOperation(
            value = "应用认证，成功后获得token",
            response = Gateway.Result.class
    )
    @GetMapping("/token/{app_id}/{timestamp}/{app_key}")
    public Gateway.Result auth(
            @ApiParam(value = "应用ID", required = true)
            @PathVariable String app_id,
            @ApiParam(value = "时间戳(10进制的毫秒数)", required = true)
            @PathVariable String timestamp,
            @ApiParam(value = "认证密钥(16进制字符串)，{app.id}+{app.key}+{timestamp}的SM3散列值", required = true)
            @PathVariable String app_key) throws Exception {
        Gateway.Result result = new Gateway.Result();
        if ( !AppConfig.TokenCheck ) {
            result.code = AppConfig.OK;
            result.data = app_id;
            return result;
        }

        long t = System.currentTimeMillis();
        if ( Math.abs(t - Long.parseLong(timestamp)) > AppConfig.TOKEN_TIME_FLOATING ) {
            result.code = AppConfig.AUTH_TIME;
            result.data = "invalid timestamp";
            return result;
        }

        List<Map> apps = (List)Context.request(AppConfig.SERVICE_GATEWAY, "getApps", Util.toSet(app_id)).call();
        if ( apps == null || apps.isEmpty() ) {
            result.code = AppConfig.AUTH_APP;
            result.data = "invalid app";
            return result;
        }
        APP app = Codec.toType(apps.get(0), APP.class);
        if ( app.status != 0 ) {
            result.code = AppConfig.AUTH_STATUS;
            result.data = "invalid app's status";
            return result;
        }

        if ( app.key != null ) {
            byte[] bytes = Util.mergeBytes(app_id.getBytes("utf-8"), app.key, timestamp.getBytes());
            try {
                if ( !Util.compareBytes(Crypto.sm3Digest(bytes), Hex.decode(app_key)) )
                    throw new Exception();
            } catch (Exception e) {
                result.code = AppConfig.AUTH_KEY;
                result.data = "invalid key";
                return result;
            }
        }

        Body body = new Body();
        body.app = app_id;
        body.exp = t + (long)AppConfig.TokenExpire * 60 * 1000;
        Gson gson = new Gson();
        String json = gson.toJson(body);
        Base64.Encoder base64 = Base64.getUrlEncoder();
        if ( app.key != null ) {
            byte[] bson = base64.encode(json.getBytes("utf-8"));
            byte[] sign = Crypto.sm3HMAC(bson, app.key);
            json = new String(bson) + "." + base64.encodeToString(sign);
        } else
            json = base64.encodeToString(json.getBytes("utf-8"));

        // 修改App的认证时间戳
        Context.request(AppConfig.SERVICE_GATEWAY, "setApp", app, Util.toMap("authTime", t)).callAsync(null, false);
        TokenSet.add(Util.hash(json));  // 缓冲认证结果
        result.code = AppConfig.OK;
        result.data = json;
        return result;
    }

    /* 获取HTTP请求的Token */
    static Gateway.ResultEx fromHttp(HttpServletRequest req) {
        Gateway.ResultEx res = new Gateway.ResultEx();

        String stoken = Util.checkEmpty(req.getHeader(AppConfig.HEADER_TOKEN));
        if (stoken == null) {
            res.code = AppConfig.TOKEN_MISS;
            res.data = "token not found";
            return res;
        }

        if ( !AppConfig.TokenCheck ) {
            res.code = AppConfig.OK;
            res.data = stoken;
            return res;
        }

        String[] split = stoken.split("\\.");
        Body body = null;
        byte[] sign = null;
        Base64.Decoder base64 = Base64.getUrlDecoder();
        try {
            String json = new String(base64.decode(split[0]), "utf-8");
            body = Util.json2Type(json, Body.class);
            if ( split.length > 1 )
                sign = base64.decode(split[1]);
        } catch (Exception e) {
            res.code = AppConfig.TOKEN_UNKNOW;
            res.data = "unknown token";
            return res;
        }

        long t = System.currentTimeMillis();
        if ( body.exp <= t || body.exp >= t + (long)AppConfig.TokenExpire * 60 * 1000 ) {
            res.code = AppConfig.TOKEN_EXPIRED;
            res.data = "token expired";
            return res;
        }
        body.app = Util.checkEmpty(body.app);
        if ( body.app == null ) {
            res.code = AppConfig.TOKEN_APP;
            res.data = "invalid app";
            return res;
        }

        int hash = Util.hash(stoken);
        if ( TokenSet.contains(hash) ) {
            res.code = AppConfig.OK;
            res.data = body.app;
            return res;
        }

        // 重新验证Token的签名
        try {
            List<Map> apps = (List) Context.request(AppConfig.SERVICE_GATEWAY, "getApps", Util.toSet(body.app)).call();
            APP app = Codec.toType(apps.get(0), APP.class);
            if (app.status != 0)
                throw new Exception("status");
            if ( app.key != null || sign != null ) {
                if (app.key == null || sign == null)
                    throw new Exception("key");
                byte[] bson = Base64.getUrlEncoder().encode(new Gson().toJson(body).getBytes("utf-8"));
                if ( !Util.compareBytes(sign, Crypto.sm3HMAC(bson, app.key)) )
                    throw new Exception("sign");
            }
        } catch (Exception e) {
            res.code = AppConfig.TOKEN_INVALID;
            res.data = "invalid token";
            return res;
        }

        TokenSet.add(hash);
        res.code = AppConfig.OK;
        res.data = body.app;
        return res;
    }

}
