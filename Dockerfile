# 两阶段构建 先编译项目，再生成运行镜像

# 以 maven:3.9.6-eclipse-temurin-8 作为第一阶段基础镜像
FROM maven:3-amazoncorretto-8 AS builder

# 设置容器内当前工作目录为 /app，后续命令默认都在 /app 下执行
WORKDIR /app

# 复制 pom.xml 文件到容器当前目录
COPY pom.xml .
# 把 src 目录复制到容器 /app/src
COPY src ./src
# 执行 Maven 打包命令，生成：/app/target/bilibili-audio-service-0.0.1-SNAPSHOT.jar
RUN mvn clean package -DskipTests

# 开启第二阶段构建
FROM openjdk:8-jre

WORKDIR /app
# 安装运行依赖。
RUN apt-get update && apt-get install -y \
    ffmpeg \
    python3 \
    python3-pip \
    curl \
    && pip3 install --no-cache-dir yt-dlp \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*
# 从第一阶段 builder 里，把打好的 jar 复制到第二阶段镜像里，放到 /app/app.jar
COPY --from=builder /app/target/bilibili-audio-service-0.0.1-SNAPSHOT.jar /app/app.jar
# 在镜像里创建工作目录
RUN mkdir -p /app/work/tasks
# 声明容器会使用 8082 端口
EXPOSE 8082
# 定义容器启动时默认执行的命令
ENTRYPOINT ["java", "-jar", "/app/app.jar", \
"--app.media.ytDlpPath=/usr/local/bin/yt-dlp", \
"--app.media.ffmpegPath=/usr/bin/ffmpeg", \
"--app.media.workDir=/app/work/tasks"]

#第一阶段
#用 Maven + JDK8 编译项目
#生成 jar

#第二阶段
#用轻量 JRE 镜像运行
#安装 ffmpeg 和 yt-dlp
#复制 jar
#启动服务
