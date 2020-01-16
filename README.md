# midpoint-grpc
**This repository is heavily under development.**

[MidPoint](https://github.com/Evolveum/midpoint) extension that enables serving gRPC services on midPoint server.

## Features

* gRPC server on midPoint
* Sample implementation of self service API for midPoint

## Install

### Build

Install JDK 11+ and [maven3](https://maven.apache.org/download.cgi) then build:

```
mvn install
```

After successful the build, you can find `midpoint-grpc-server-*.jar` in `./server/target` directory.
Also, you can see `midpoint-grpc-self-services-*.jar` in `./self-services/target` directory which is sample implementation of gRPC service for self service.

### Deploy gRPC server and sample gRPC service

Put `midpoint-grpc-server-*.jar` into `$MIDPOINT_HOME/lib` directory.
Also, put `midpoint-grpc-self-services-*.jar` into `$MIDPOINT_HOME/lib` directory simply
if you want to deploy the sample gRPC service.

### Start gRPC server and services

Start your midPoint server. You can see some logging about starting gRPC server and services:

```
2019-12-06 19:52:02,701 [] [main] INFO (com.evolveum.midpoint.web.boot.MidPointSpringApplication): Started MidPointSpringApplication in 55.149 seconds (JVM running for 58.019)
2019-12-06 19:52:02,726 [] [main] INFO (org.lognet.springboot.grpc.GRpcServerRunner): Starting gRPC Server ...
2019-12-06 19:52:02,834 [] [main] INFO (org.lognet.springboot.grpc.GRpcServerRunner): 'jp.openstandia.midpoint.grpc.SelfServiceResource' service has been registered.
2019-12-06 19:52:03,931 [] [main] INFO (org.lognet.springboot.grpc.GRpcServerRunner): gRPC Server started, listening on port 6565.
```

## How to write own custom gRPC service

Please see [the sample implementation of gRPC service](https://github.com/openstandia/midpoint-grpc/tree/master/self-services).

After building your services, you can deploy it by putting it into `$MIDPOINT_HOME/lib` directory simply.

## License

Licensed under the [Apache License 2.0](/LICENSE).
