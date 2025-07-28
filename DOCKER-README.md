# Docker 环境配置指南

## 测试容器问题解决方案

在运行集成测试时，您可能会遇到以下错误：

```
java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
```

这是因为TestContainers需要一个可用的Docker环境来创建和管理测试容器。

## 解决方案

### 1. 安装Docker

首先，您需要安装Docker：

- Windows: 安装 [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop)
- Mac: 安装 [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop)
- Linux: 根据您的发行版安装 [Docker Engine](https://docs.docker.com/engine/install/)

### 2. 确保Docker正在运行

安装后，确保Docker服务已启动：

- Windows/Mac: 启动Docker Desktop应用
- Linux: 运行 `sudo systemctl start docker`

### 3. 验证Docker安装

打开命令行终端，运行以下命令验证Docker是否正确安装和运行：

```bash
docker --version
docker info
```

### 4. TestContainers配置

如果Docker已正确安装但TestContainers仍无法连接，可以尝试以下配置：

1. 创建或编辑 `src/test/resources/testcontainers.properties` 文件：

```properties
# Docker主机配置
docker.host=tcp://localhost:2375
# 如果使用Docker Desktop，可能需要以下配置
# docker.host=npipe:////./pipe/docker_engine
```

2. 确保Docker API已启用：
   - Docker Desktop: 在设置中启用"Expose daemon on tcp://localhost:2375 without TLS"选项

### 5. 替代方案

如果无法配置Docker环境，可以使用以下替代方案：

1. 使用模拟测试：参考 `RagDataLoaderMockTest.java`
2. 禁用依赖Docker的测试：使用 `@Disabled` 注解

## 更多资源

- [TestContainers文档](https://www.testcontainers.org/)
- [Docker文档](https://docs.docker.com/)