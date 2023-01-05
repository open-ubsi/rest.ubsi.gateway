package rewin.rest.ubsi.gateway;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rewin.ubsi.container.Info;

import java.io.File;

/**
 * 运行信息接口
 */
@RestController
@RequestMapping(value = "/runtime")
@Api(tags = "运行信息接口")
public class Runtime {

    @ApiModel("运行参数")
    public static class Props {
        @ApiModelProperty("应用令牌的过期时间(分钟数)")
        public int      token_expire;
        @ApiModelProperty("是否验证Token的合法性(false时Token的值就是AppID)")
        public boolean  token_check;
        @ApiModelProperty("默认的远程主机访问权限")
        public boolean  acl_remote;
        @ApiModelProperty("默认的服务访问权限")
        public boolean  acl_service;
    }

    @ApiOperation(
            value = "运行参数",
            response = Props.class
    )
    @GetMapping("/props")
    public Props props() throws Exception {
        Props res = new Props();
        res.token_expire = AppConfig.TokenExpire;
        res.token_check = AppConfig.TokenCheck;
        res.acl_remote = AppConfig.AclRemote;
        res.acl_service = AppConfig.AclService;
        return res;
    }

    @ApiOperation(
            value = "运行信息",
            response = Info.Controller.class
    )
    @GetMapping("/info")
    public Info.Controller info() throws Exception {
        Info.Controller res = Info.getController(new Info.Controller());
        res.run_path = AppConfig.RunPath;
        return res;
    }
}
