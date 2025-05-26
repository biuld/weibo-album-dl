# 第一阶段：构建阶段
FROM debian:bookworm-slim AS builder

# 安装必要的依赖（curl和一些构建依赖）
RUN apt-get update && \
    apt-get install -y curl &&\
    apt-get clean

# 检测 CPU 架构并下载对应的 Coursier 二进制文件
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then \
        curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz" | gzip -d > cs; \
    elif [ "$ARCH" = "aarch64" ]; then \
        curl -fL "https://github.com/VirtusLab/coursier-m1/releases/latest/download/cs-aarch64-pc-linux.gz" | gzip -d > cs; \
    else \
        echo "Unsupported architecture: $ARCH"; exit 1; \
    fi && \
    chmod +x cs

RUN ./cs install cs

# 设置环境变量
ENV PATH="/root/.local/share/coursier/bin:${PATH}"
    
RUN cs install scala-cli

# 设置工作目录
WORKDIR /app

# 复制项目文件到工作目录中
COPY src src/
COPY .scalafmt.conf ./

# 使用 scala-cli 构建项目，生成 JAR 文件
RUN scala-cli package --power . -o weibo-album-dl.jar --assembly --preamble=false --jvm 21

# 第二阶段：运行阶段
FROM openjdk:21

# 设置工作目录
WORKDIR /app

# 创建日志目录
RUN mkdir -p /app/logs

# 从构建阶段复制生成的 JAR 文件
COPY --from=builder /app/weibo-album-dl.jar /app/weibo-album-dl.jar

# 设置容器启动命令，使用 OpenJDK 21 运行 JAR 文件
CMD ["java", "-jar", "/app/weibo-album-dl.jar"]