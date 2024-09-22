# Docker 镜像构建
#
#
FROM maven:3.5-jdk-8-alpine as builder

## Copy local code to the container image.
#WORKDIR /app
#COPY pom.xml .
#COPY src ./srcyu
#
## Build a release artifact.
#RUN mvn package -DskipTests

WORKDIR /app
COPY ./target/yupao-backend-duplicate-0.0.1-SNAPSHOT.jar .

# Run the web service on container startup.
#CMD ["java","-jar","/app/target/yupao-backend-duplicate-0.0.1-SNAPSHOT.jar","--spring.profiles.active=prod"]
CMD ["java","-jar","/app/yupao-backend-duplicate-0.0.1-SNAPSHOT.jar","--spring.profiles.active=prod"]

# [编程学习交流圈](https://www.code-nav.cn/) 快速入门编程不走弯路！30+ 原创学习路线和专栏、500+ 编程学习指南、1000+ 编程精华文章、20T+ 编程资源汇总