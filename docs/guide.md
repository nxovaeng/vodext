
# 开发指南

## 添加新视频源

1. MacCMS类网站

   ```kotlin
   class NewSourceExtractor : BaseMacCmsProvider() {
       override val name = "视频源名称"
       override val mainUrl = "https://example.com"
   }
   ```

2. Api 采集类网站

   ```kotlin
   class NewSourceExtractor : BaseVodProvider() {
       override val name = "采集源名称"
       override val mainUrl = "https://api.example.com"
   }
   ```

## 构建说明

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 17 或更高版本
- Kotlin 2.2.0 或更高版本
- Android SDK 33 或更高版本

### 构建步骤

1. 克隆项目

   ```bash
   git clone https://github.com/nxovaeng/vodext.git
   cd vodext
   ```

2. 本地打包测试

   windows环境配置：当前项目下新建 local.properties 配置 android sdk

   sdk.dir=C:\\Users\\UserName\\AppData\\Local\\Android\\sdk

   ```bash
   ./gradlew make makePluginsJson
   ```

### 自动发布流程

本项目可使用 GitHub Actions 自动构建和发布。

手动触发 GitHub Actions 将自动：

- 构建项目
- 将生成的文件发布到builds分支
