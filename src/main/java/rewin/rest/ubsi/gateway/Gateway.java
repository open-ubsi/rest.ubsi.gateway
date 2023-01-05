package rewin.rest.ubsi.gateway;

import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import rewin.rest.ubsi.gateway.policy.*;
import rewin.ubsi.common.JsonCodec;
import rewin.ubsi.common.Util;
import rewin.ubsi.consumer.Context;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 服务请求接口
 */
@RestController
@Api(tags = "请求接口")
public class Gateway {

    @ApiModel("Version，服务的版本")
    public static class Version {
        @ApiModelProperty(value = "最小版本，缺省\"0.0.0\"", example = "0.0.0")
        public String   min = "0.0.0";
        @ApiModelProperty(value = "最大版本，缺省\"0.0.0\"(表示不限)", example = "0.0.0")
        public String   max = "0.0.0";
        @ApiModelProperty(value = "发行状态，-1:不限；0:非正式；1:正式版，缺省-1", example = "-1")
        public int      rel = -1;
    }
    @ApiModel("Request，服务请求")
    public static class Request {
        @ApiModelProperty("超时时间(秒)，缺省0表示使用默认设置，-1表示不需要返回结果")
        public int              timeout = 0;
        public Version          version = null;
        @ApiModelProperty("请求头的数据项(JSON数据类型的表达方式详见help.html)")
        public Map              header = null;
        @ApiModelProperty("请求参数(JSON数据类型的表达方式详见help.html)")
        public List             params = null;
    }

    @ApiModel("Result，请求结果")
    public static class Result {
        @ApiModelProperty("结果代码，0表示成功")
        public int      code = 0;
        @ApiModelProperty("结果数据，失败时返回错误消息")
        public Object   data = null;
    }
    @ApiModel("ResultEx，服务请求结果")
    public static class ResultEx extends Result {
        @ApiModelProperty("结果的附加数据")
        public Map      tail = null;
        @ApiModelProperty("处理时间，毫秒数")
        public int      time = 0;
        @ApiModelProperty("结果数据的来源，0:网关，1:微服务，2:分流的网关，3:缓冲，4:仿真")
        public int      from = 0;
    }

    /* 服务请求的异步处理 */
    static class AsyncCall implements Context.ResultNotify {
        RestRequest restRequest;
        Context     consumer;
        // 构造函数
        AsyncCall(RestRequest rest, Context context) {
            restRequest = rest;
            consumer = context;
        }
        // 请求结果的回调
        public void callback(int code, Object res) {
            over(restRequest, AppConfig.FROM_REMOTE_SERVICE, code, res, consumer.getTailer());
        }
    }

    @Autowired
    HttpServletRequest      httpRequest;        // HTTP请求对象

    /** 本次请求的数据 */
    public static class RestRequest {
        public long     timestamp = System.currentTimeMillis();
        public String   token;
        public String   remote;
        public Request  request;
        public List     params;
        public String   app;
        public String   service;
        public String   entry;
        public LogSet   logs;
        public String   cacheFile;
        public String   reqId;
        public DeferredResult<ResultEx> result = new DeferredResult<>();
    }

    @ApiOperation(
            value = "请求服务",
            response = ResultEx.class
    )
    @PostMapping("/request/{service}/{entry}")
    public DeferredResult<ResultEx> deal(
            @ApiParam(value = "服务名字", required = true)
            @PathVariable String service,
            @ApiParam(value = "方法名字", required = true)
            @PathVariable String entry,
            @ApiParam("请求数据")
            @RequestBody Request request) {
        RestRequest rest = new RestRequest();
        // 处理token
        ResultEx res = Token.fromHttp(httpRequest);
        if ( res.code != 0 ) {
            res.from = AppConfig.FROM_LOCAL;
            res.time = (int)(System.currentTimeMillis() - rest.timestamp);
            rest.result.setResult(res);
            return rest.result;
        }
        rest.app = (String)res.data;
        rest.token = httpRequest.getHeader(AppConfig.HEADER_TOKEN);

        rest.request = request;
        rest.service = service;
        rest.entry = entry;
        rest.logs = LogSet.checkLogSet(rest.app, service, entry);
        rest.remote = Application.getRemoteIP(httpRequest);
        try {
            //rest.params = request.params;
            rest.params = (List)JsonCodec.decodeType(request.params);

            if ( !Acl.checkRAcl(rest.remote, rest.app) )
                return over(rest, AppConfig.FROM_LOCAL, AppConfig.ACL_REMOTE, "access denied by address");
            if ( !Acl.checkSAcl(rest.app, service, entry) )
                return over(rest, AppConfig.FROM_LOCAL, AppConfig.ACL_SERVICE, "access denied by service");

            Object[] mock = Mock.checkMock(rest.app, service, entry);
            if ( mock != null )
                return over(rest, AppConfig.FROM_MOCK, AppConfig.OK, mock[0]);
            Object[] cache = Cache.checkCache(rest.app, service, entry, rest.params);
            if ( cache != null ) {
                if (cache.length > 1)
                    return over(rest, AppConfig.FROM_CACHE, AppConfig.OK, cache[1]);
                rest.cacheFile = (String)cache[0];
            }

            int limit = Rate.checkRate(rest.app, service, entry);
            if ( limit != 0 )
                return over(rest, AppConfig.FROM_LOCAL, limit > 0 ? AppConfig.RATE_GLOBAL : AppConfig.RATE_LOCAL,
                        "access denied by rate_limit[" + (limit > 0 ? "GLOBAL" : "LOCAL") + "]");

            String forwardUrl = Flow.checkForward(rest.app, service, entry);
            if ( forwardUrl != null )
                return forward(forwardUrl, rest);

            Router router = Router.checkRouter(rest.app, service, entry);
            String sname = service;
            if ( router != null && router.altService != null )
                sname = router.altService;
            if ( rest.params == null )
                rest.params = new ArrayList();
            rest.params.add(0, entry);
            Context consumer = Context.request(sname, rest.params.toArray());
            rest.params.remove(0);
            rest.reqId = consumer.getReqID();
            if ( request.header != null )
                consumer.setHeader((Map)JsonCodec.decodeType(request.header));
            if ( request.timeout > 0 )
                consumer.setTimeout(request.timeout);
            if ( request.version != null || router != null ) {
                int min = 0;
                int max = 0;
                int rel = -1;
                if ( request.version != null ) {
                    String str = Util.checkEmpty(request.version.min);
                    if ( str != null )
                        min = Util.getVersion(str);
                    str = Util.checkEmpty(request.version.max);
                    if ( str != null )
                        max = Util.getVersion(str);
                    rel = request.version.rel;
                }
                if ( router != null ) {
                    if ( min == 0 )
                        min = router.verMin;
                    if ( max == 0 )
                        max = router.verMax;
                    if ( rel < 0 )
                        rel = router.verRelease;
                }
                consumer.setVersion(min, max, rel);
            }
            if ( rest.logs.trace )
                consumer.setLogAccess(true);
            if ( request.timeout >= 0 )
                consumer.callAsync(new AsyncCall(rest, consumer), false);
            else {
                consumer.callAsync(null, false);
                return over(rest, AppConfig.FROM_DISCARD_SERVICE, AppConfig.OK, null);
            }
        } catch (Context.ResultException re) {
            return over(rest, AppConfig.FROM_REMOTE_SERVICE, re.getCode(), re.getMessage());
        } catch (Exception e) {
            return over(rest, AppConfig.FROM_LOCAL, AppConfig.DEAL_EXCEPTION, e.toString());
        }
        return rest.result;
    }

    /* 生成返回结果 */
    static DeferredResult<ResultEx> over(RestRequest rest, int from, int code, Object data, Map tail) {
        ResultEx res = new ResultEx();
        res.code = code;
        res.data = data;
        res.tail = tail;
        return over(rest, from, res);
    }
    /* 生成返回结果 */
    static DeferredResult<ResultEx> over(RestRequest rest, int from, int code, Object data) {
        return over(rest, from, code, data, null);
    }
    /* 生成返回结果 */
    static DeferredResult<ResultEx> over(RestRequest rest, int from, ResultEx res) {
        res.from = from;
        res.time = (int)(System.currentTimeMillis() - rest.timestamp);
        rest.result.setResult(res);

        if ( rest.cacheFile != null && res.code == AppConfig.OK &&
                (res.from == AppConfig.FROM_REMOTE_SERVICE || res.from == AppConfig.FROM_REMOTE_GATEWAY) ) {
            try {
                Cache.saveCache(rest.cacheFile, res.data);  // 生成cache数据文件
            } catch (Exception e) {
                AppRunner.log.warn("over_cache", rest.app + "@" + rest.service + "/" + rest.entry + ", " + res.code + " - " + e);
            }
        }

        try {
            LogSet.saveLog(rest, res);
        } catch (Exception e) {
            AppRunner.log.warn("over_log", rest.app + "@" + rest.service + "/" + rest.entry + ", " + res.code + " - " + e);
        }

        rest.timestamp = 0;     // 转发到镜像Gateway
        for (String url : Flow.checkMirror(rest.app, rest.service, rest.entry)) {
            try {
                forward(url, rest);
            } catch (Exception e) {
                AppRunner.log.warn("over_mirror", rest.app + "@" + url + " - " + e);
            }
        }

        return rest.result;
    }

    static WebClient webClient = WebClient.create();    // WebClient实例（单例）

    /* 转发到另外的网关 */
    static DeferredResult<ResultEx> forward(String url, RestRequest rest) {
        Mono<ClientResponse> resp = webClient.post()
                .uri(url + "/" + rest.service + "/" + rest.entry)
                .contentType(MediaType.APPLICATION_JSON)
                .header(AppConfig.HEADER_TOKEN, rest.token)
                .body(Mono.just(rest.request), Gateway.Request.class)
                .exchange()
                .doOnError(e -> {
                    if ( rest.request.timeout >= 0 && rest.timestamp != 0 )
                        over(rest, AppConfig.FROM_REMOTE_GATEWAY, AppConfig.REST_FORWARD, url + ", " + e.toString());
                    else
                        AppRunner.log.warn("forward: " + rest.app + "@" + url, e.toString());
                });

        if ( rest.request.timeout < 0 || rest.timestamp == 0 ) {
            resp.subscribe();
            if ( rest.timestamp == 0 )
                return null;        // 镜像的请求
            return over(rest, AppConfig.FROM_DISCARD_GATEWAY, AppConfig.OK, null);
        }

        resp.subscribe(clientResponse -> {
            HttpStatus status = clientResponse.statusCode();
            if ( status.is2xxSuccessful() ) {
                Mono<String> body = clientResponse.bodyToMono(String.class);
                body.subscribe(str -> {
                    try {
                        Gateway.ResultEx res = Util.json2Type(str, Gateway.ResultEx.class);
                        over(rest, AppConfig.FROM_REMOTE_GATEWAY, res);
                    } catch (Exception e) {
                        over(rest, AppConfig.FROM_REMOTE_GATEWAY, AppConfig.REST_FORWARD, url + ", " + e);
                    }
                });
            } else {
                over(rest, AppConfig.FROM_REMOTE_GATEWAY, status.value(), url + " " + status.getReasonPhrase());
            }
        });
        return rest.result;
    }
}
