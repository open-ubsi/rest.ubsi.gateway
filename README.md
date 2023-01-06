# rest.ubsi.gateway
UBSI微服务RestAPI网关


- 如何运行

  下载最新版本的release, 解压后执行 `java -jar target/ubsi-rest-gateway-2.3.2.jar`
  
- 运行配置

  默认的UBSI Consumer配置文件：rewin.ubsi.consumer.json
  
  默认的服务运行参数配置文件：src/main/resource/application.properties
 
- 接口文档

  http://localhost:8090/gateway/swagger-ui.html
  
- 服务请求

  [POST] http://localhost:8090/gateway/request/{service-name}/{entry}
  
- 请求参数的格式

  http://localhost:8090/gateway/help.html
