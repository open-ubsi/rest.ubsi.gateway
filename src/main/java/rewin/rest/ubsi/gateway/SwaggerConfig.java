package rewin.rest.ubsi.gateway;

import io.swagger.annotations.ApiOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;
import java.util.List;

/**
 * Swagger的配置
 */
@Configuration
@EnableSwagger2
public class SwaggerConfig {

    @Bean
    public Docket setupSwagger() {
        ParameterBuilder ticketPar = new ParameterBuilder();
        ticketPar.name("token").description("应用令牌")
                .modelRef(new ModelRef("string")).parameterType("header")
                .required(false);
        List<Parameter> pars = new ArrayList<Parameter>();
        pars.add(ticketPar.build());
        return new Docket(DocumentationType.SWAGGER_2)
                .globalOperationParameters(pars)        // 为swagger_ui的每个请求增加header->token参数
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class))     // 方法需要有ApiOperation注解
                .paths(PathSelectors.any())     // 路径风格
                .build();
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("UBSI Service API-Gateway")
                .description("UBSI微服务API网关接口说明")
                .contact(new Contact("ubsi-home", "https://open-ubsi.github.io/", "ubsi@rewin.com.cn"))
                .version("2.3.2")
                .build();
    }
}
