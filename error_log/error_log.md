E:\AI Application\Agentic_RAG> mvn spring-boot:run -pl rag-boot -Dspring-boot.run.profiles=local
[INFO] Scanning for projects...
[INFO] 
[INFO] --------------------------< com.rag:rag-boot >--------------------------
[INFO] Building RAG Boot 0.1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] >>> spring-boot-maven-plugin:3.4.5:run (default-cli) > test-compile @ rag-boot >>>
[INFO] 
[INFO] --- maven-resources-plugin:3.3.1:resources (default-resources) @ rag-boot ---
[INFO] Copying 2 resources from src\main\resources to target\classes
[INFO] Copying 2 resources from src\main\resources to target\classes
[INFO] 
[INFO] --- maven-compiler-plugin:3.13.0:compile (default-compile) @ rag-boot ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- maven-resources-plugin:3.3.1:testResources (default-testResources) @ rag-boot ---
[INFO] skip non existing resourceDirectory E:\AI Application\Agentic_RAG\rag-boot\src\test\resources
[INFO] 
[INFO] --- maven-compiler-plugin:3.13.0:testCompile (default-testCompile) @ rag-boot ---
[INFO] No sources to compile
[INFO] 
[INFO] <<< spring-boot-maven-plugin:3.4.5:run (default-cli) < test-compile @ rag-boot <<<
[INFO]
[INFO]
[INFO] --- spring-boot-maven-plugin:3.4.5:run (default-cli) @ rag-boot ---
[INFO] Attaching agents: []
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

 :: Spring Boot ::                (v3.4.5)

2026-04-01T18:18:20.620+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] com.rag.RagApplication                   : Starting RagApplication using Java 21.0.10 with PID 62248 (E:\AI Application\Agentic_RAG\rag-boot\target\classes started by zhong kai in E:\AI Application\Agentic_RAG\rag-boot)
2026-04-01T18:18:20.622+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] com.rag.RagApplication                   : The following 1 profile is active: "local"
2026-04-01T18:18:21.231+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Multiple Spring Data modules found, entering strict repository configuration mode
2026-04-01T18:18:21.231+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-04-01T18:18:21.339+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 100 ms. Found 10 JPA repository interfaces.
2026-04-01T18:18:21.350+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Multiple Spring Data modules found, entering strict repository configuration mode
2026-04-01T18:18:21.350+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data Redis repositories in DEFAULT mode.
2026-04-01T18:18:21.361+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.AccessRuleJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.361+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.ChatSessionJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.361+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.CitationJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.361+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.DocumentJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.362+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.DocumentTagJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.362+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.DocumentVersionJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.363+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.KnowledgeSpaceJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.363+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.MessageJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.363+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.SpacePermissionJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.363+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .RepositoryConfigurationExtensionSupport : Spring Data Redis - Could not safely identify store assignment for repository candidate interface com.rag.adapter.outbound.persistence.repository.UserJpaRepository; If you want this repository to be a Redis repository, consider annotating your entities with one of these annotations: org.springframework.data.redis.core.RedisHash (preferred), or consider extending one of the following types with your repository: org.springframework.data.keyvalue.repository.KeyValueRepository
2026-04-01T18:18:21.363+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 6 ms. Found 0 Redis repository interfaces.
2026-04-01T18:18:21.754+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port 8080 (http)
2026-04-01T18:18:21.764+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-04-01T18:18:21.765+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.40]
2026-04-01T18:18:21.814+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2026-04-01T18:18:21.815+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 1164 ms
Standard Commons Logging discovery in action with spring-jcl: please remove commons-logging.jar from classpath in order to avoid potential conflicts
2026-04-01T18:18:21.969+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-04-01T18:18:22.080+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@6233c6c2
2026-04-01T18:18:22.082+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-04-01T18:18:22.105+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:postgresql://localhost:5432/rag_db (PostgreSQL 16.13)
2026-04-01T18:18:22.147+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.f.core.internal.command.DbValidate     : Successfully validated 1 migration (execution time 00:00.022s)
2026-04-01T18:18:22.179+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "public": 1
2026-04-01T18:18:22.181+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.f.core.internal.command.DbMigrate      : Schema "public" is up to date. No migration necessary.
2026-04-01T18:18:22.234+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.hibernate.jpa.internal.util.LogHelper  : HHH000204: Processing PersistenceUnitInfo [name: default]
2026-04-01T18:18:22.264+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.6.13.Final
2026-04-01T18:18:22.286+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.h.c.internal.RegionFactoryInitiator    : HHH000026: Second-level cache disabled
2026-04-01T18:18:22.471+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-04-01T18:18:22.505+08:00  WARN 62248 --- [agentic-rag-knowledge-base] [           main] org.hibernate.orm.deprecation            : HHH90000025: PostgreSQLDialect does not need to be specified explicitly using 'hibernate.dialect' (remove the property setting and it will be selected by default)
2026-04-01T18:18:22.516+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
        Database JDBC URL [Connecting through datasource 'HikariDataSource (HikariPool-1)']
        Database driver: undefined/unknown
        Database version: 16.13
        Autocommit mode: undefined/unknown
        Isolation level: undefined/unknown
        Minimum pool size: undefined/unknown
        Maximum pool size: undefined/unknown
2026-04-01T18:18:23.133+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.h.e.t.j.p.i.JtaPlatformInitiator       : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-04-01T18:18:23.170+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-04-01T18:18:23.395+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.d.j.r.query.QueryEnhancerFactory     : Hibernate is in classpath; If applicable, HQL parser will be used.
2026-04-01T18:18:25.509+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.m.s.b.SimpleBrokerMessageHandler     : Starting...
2026-04-01T18:18:25.510+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.m.s.b.SimpleBrokerMessageHandler     : BrokerAvailabilityEvent[available=true, SimpleBrokerMessageHandler [org.springframework.messaging.simp.broker.DefaultSubscriptionRegistry@7f38646d]]
2026-04-01T18:18:25.510+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.m.s.b.SimpleBrokerMessageHandler     : Started.
2026-04-01T18:18:25.564+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port 8080 (http) with context path '/'
2026-04-01T18:18:25.571+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [           main] com.rag.RagApplication                   : Started RagApplication in 5.262 seconds (process running for 5.567)
2026-04-01T18:18:34.079+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [nio-8080-exec-2] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-04-01T18:18:34.080+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [nio-8080-exec-2] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-04-01T18:18:34.082+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [nio-8080-exec-2] o.s.web.servlet.DispatcherServlet        : Completed initialization in 2 ms
2026-04-01T18:19:25.520+08:00  INFO 62248 --- [agentic-rag-knowledge-base] [MessageBroker-1] o.s.w.s.c.WebSocketMessageBrokerStats    : WebSocketSession[0 current WS(0)-HttpStream(0)-HttpPoll(0), 0 total, 0 closed abnormally (0 connect failure, 0 send limit, 0 transport error)], stompSubProtocol[processed CONNECT(0)-CONNECTED(0)-DISCONNECT(0)], stompBrokerRelay[null], inboundChannel[pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0], outboundChannel[pool size = 0, active threads = 0, queued tasks = 0, completed tasks = 0], sockJsScheduler[pool size = 1, active threads = 1, queued tasks = 0, completed tasks = 0]
2026-04-01T18:19:50.830+08:00 ERROR 62248 --- [agentic-rag-knowledge-base] [nio-8080-exec-6] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: java.lang.NullPointerException: Cannot invoke "com.rag.domain.document.model.DocumentVersion.versionId()" because "v" is null] with root cause

java.lang.NullPointerException: Cannot invoke "com.rag.domain.document.model.DocumentVersion.versionId()" because "v" is null
        at com.rag.adapter.outbound.persistence.mapper.DocumentMapper.toVersionEntity(DocumentMapper.java:81) ~[rag-adapter-outbound-0.1.0-SNAPSHOT.jar:0.1.0-SNAPSHOT]
        at com.rag.adapter.outbound.persistence.adapter.DocumentRepositoryAdapter.saveVersion(DocumentRepositoryAdapter.java:88) ~[rag-adapter-outbound-0.1.0-SNAPSHOT.jar:0.1.0-SNAPSHOT]
        at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
        at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
        at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359) ~[spring-aop-6.2.6.jar:6.2.6]
        at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724) ~[spring-aop-6.2.6.jar:6.2.6]
        at com.rag.adapter.outbound.persistence.adapter.DocumentRepositoryAdapter$$SpringCGLIB$$0.saveVersion(<generated>) ~[rag-adapter-outbound-0.1.0-SNAPSHOT.jar:0.1.0-SNAPSHOT]
        at com.rag.application.document.DocumentApplicationService.uploadDocument(DocumentApplicationService.java:45) ~[rag-application-0.1.0-SNAPSHOT.jar:0.1.0-SNAPSHOT]
        at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
        at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
        at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359) ~[spring-aop-6.2.6.jar:6.2.6]
        at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196) ~[spring-aop-6.2.6.jar:6.2.6]
        at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163) ~[spring-aop-6.2.6.jar:6.2.6]
        at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:380) ~[spring-tx-6.2.6.jar:6.2.6]
        at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:119) ~[spring-tx-6.2.6.jar:6.2.6]
        at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:184) ~[spring-aop-6.2.6.jar:6.2.6]
        at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:728) ~[spring-aop-6.2.6.jar:6.2.6]
        at com.rag.application.document.DocumentApplicationService$$SpringCGLIB$$0.uploadDocument(<generated>) ~[rag-application-0.1.0-SNAPSHOT.jar:0.1.0-SNAPSHOT]
        at com.rag.adapter.inbound.rest.DocumentController.upload(DocumentController.java:35) ~[rag-adapter-inbound-0.1.0-SNAPSHOT.jar:0.1.0-SNAPSHOT]
        at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
        at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
        at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258) ~[spring-web-6.2.6.jar:6.2.6]
        at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:191) ~[spring-web-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:986) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:891) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590) ~[tomcat-embed-core-10.1.40.jar:6.0]
        at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885) ~[spring-webmvc-6.2.6.jar:6.2.6]
        at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658) ~[tomcat-embed-core-10.1.40.jar:6.0]
        at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:195) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51) ~[tomcat-embed-websocket-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-6.2.6.jar:6.2.6]
        at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.6.jar:6.2.6]
        at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-6.2.6.jar:6.2.6]
        at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.6.jar:6.2.6]
        at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) ~[spring-web-6.2.6.jar:6.2.6]
        at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.6.jar:6.2.6]
        at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:167) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:483) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:116) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:344) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:398) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:903) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1740) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1189) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:658) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) ~[tomcat-embed-core-10.1.40.jar:10.1.40]
        at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]