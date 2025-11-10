# CloudStream 影视扩展

这是一个 CloudStream 的资源扩展集合，为 CloudStream 添加影视资源支持。

该扩展为学习编程及自用目的, 有新需求可创建分支自行修改。

## 支持的视频源

| 扩展名称 |  支持的网站              | 类型 | 状态 |
|---------|-------------------------|------|------|
| ApiCMS | bfzyapi.com, mdzyapi.com | 影视、动漫 | ✅ 正常 |
| MacCMS | zjkrmv.com, rebovod.com | 影视、动漫 |   尚未调通 |
| Animekhor | animekhor.org, donghuaworld.com | 动漫 | ✅ 正常-来自phisher的扩展库 |

## 安装方法

1. 打开 CloudStream 应用
2. 进入设置 > 扩展设置
3. 点击"添加仓库"
4. 输入仓库地址：`https://raw.githubusercontent.com/zronest/vodext/builds/repo.json`
5. 安装插件

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

## 致谢

- [CloudStream](https://github.com/recloudstream/cloudstream) - 提供优秀的视频播放框架
- [Kotlin](https://kotlinlang.org/) - 现代化的编程语言
- [Jsoup](https://jsoup.org/) - HTML 解析库
- [phisher](https://github.com/phisher98/cloudstream-extensions-phisher) - CloudStream插件扩展库
