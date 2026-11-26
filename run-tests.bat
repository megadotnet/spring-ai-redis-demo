@echo off
echo 开始运行单元测试和生成覆盖率报告...

echo.
echo 1. 运行单元测试
mvn clean test

echo.
echo 2. 生成 JaCoCo 覆盖率报告
mvn jacoco:report

echo.
echo 3. 测试报告位置:
echo    - 单元测试报告: target/surefire-reports/
echo    - 覆盖率报告: target/site/jacoco/index.html

echo.
echo 测试完成！
pause