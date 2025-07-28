# 测试指南

## 测试类型

本项目包含两种类型的测试：

1. **模拟测试**：不依赖Docker环境，使用模拟对象进行测试
2. **集成测试**：需要Docker环境，使用TestContainers启动真实的Redis容器进行测试

## 如何运行测试

### 运行所有测试

```bash
mvn test
```

### 仅运行模拟测试

```bash
mvn test -Dtest=*MockTest
```

### 仅运行集成测试（需要Docker环境）

```bash
mvn test -Dtest=*IntegrationTest
```

## 测试配置

测试配置位于 `src/test/resources/application-test.yml` 文件中。

## 常见问题

### 集成测试失败，提示"Could not find a valid Docker environment"

这表明TestContainers无法找到有效的Docker环境。请参考 `DOCKER-README.md` 文件了解如何解决此问题。

### 如何在没有Docker的环境中运行测试？

如果您无法配置Docker环境，可以：

1. 仅运行模拟测试：`mvn test -Dtest=*MockTest`
2. 或者使用提供的批处理脚本：`run-tests.bat`