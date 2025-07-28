# RagDataLoader 单元测试套件

## 概述
为 `RagDataLoader` 类创建的完整单元测试套件，包含单元测试、集成测试和测试覆盖率配置。

## 测试结构

### 单元测试
- **文件位置**: `src/test/java/com/redis/demo/spring/ai/RagDataLoaderTest.java`
- **测试框架**: JUnit 5 + Mockito
- **覆盖场景**:
  - ✅ 数据存在性检查逻辑
  - ✅ GZIP 文件处理功能
  - ✅ 向量创建和存储流程
  - ✅ 异常处理场景
  - ✅ 边界条件测试

### 集成测试
- **文件位置**: `src/test/java/com/redis/demo/spring/ai/RagDataLoaderIntegrationTest.java`
- **测试框架**: Spring Boot Test + TestContainers
- **测试内容**: Redis 容器集成测试

## 运行测试

### 方法一：使用 Maven 命令
```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=RagDataLoaderTest

# 运行测试并生成覆盖率报告
mvn clean test jacoco:report
```

### 方法二：使用批处理脚本
```bash
# Windows 环境
run-tests.bat
```

## 测试报告

### 单元测试报告
- **位置**: `target/surefire-reports/`
- **格式**: HTML 和 XML 格式的测试结果

### 覆盖率报告
- **位置**: `target/site/jacoco/index.html`
- **内容**: 代码覆盖率详细报告，包括行覆盖率、分支覆盖率等

## 测试配置

### Maven 插件配置
- **JaCoCo**: 测试覆盖率插件
- **Surefire**: 单元测试插件
- **Failsafe**: 集成测试插件

### 测试依赖
- `spring-boot-starter-test`: Spring Boot 测试启动器
- `testcontainers`: 容器化集成测试
- `mockito`: Mock 框架

## 测试用例详情

### 核心测试用例
1. **shouldSkipLoadingWhenDataAlreadyExists**: 测试数据已存在时跳过加载
2. **shouldLoadDataWhenNoExistingData**: 测试正常数据加载流程
3. **shouldHandleGzipFile**: 测试 GZIP 文件处理
4. **shouldThrowExceptionWhenFileReadFails**: 测试文件读取异常
5. **shouldThrowExceptionWhenVectorStoreOperationFails**: 测试向量存储异常

### 边界条件测试
6. **shouldHandleEmptyIndexInfo**: 测试空索引信息处理
7. **shouldHandleNumDocsAsString**: 测试字符串类型数据处理
8. **shouldHandleBoundaryValue**: 测试边界值处理

## 最佳实践

### Mock 策略
- 使用 `@Mock` 注解创建 Mock 对象
- 使用 `ReflectionTestUtils` 注入私有字段
- 使用 `ArgumentCaptor` 验证方法参数

### 测试数据
- 使用真实的 JSON 测试数据
- 创建 GZIP 压缩测试数据
- 模拟各种 Redis 索引状态

### 断言验证
- 使用 AssertJ 进行流畅断言
- 验证方法调用次数和参数
- 检查异常抛出情况

## 持续集成
测试套件已配置为支持 CI/CD 流水线，可以集成到 Jenkins、GitHub Actions 等持续集成工具中。

## 故障排除

### 常见问题
1. **Redis 连接失败**: 确保 Redis 服务正在运行
2. **依赖下载失败**: 检查网络连接和 Maven 仓库配置
3. **测试超时**: 调整测试超时配置

### 调试技巧
- 使用 `@Disabled` 注解临时禁用测试
- 添加日志输出查看测试执行过程
- 使用 IDE 调试功能逐步执行测试