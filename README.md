# CloudStream 影视扩展

这是一个 CloudStream 的资源扩展集合，为 CloudStream 添加影视资源支持。

该扩展为学习编程及自用目的, 有新需求可创建分支自行修改。

## ⚖️ DMCA 免责声明

我们特此发布此通知，以澄清这些扩展程序的功能类似于标准网络浏览器，通过从公共互联网获取影视内容，此存储库或 Cloudstream 3 应用程序不托管任何内容。
访问的任何内容均由第三方网站托管，用户对其使用负全部责任，并且必须遵守当地法律。

如果您认为内容违反了版权法，请联系实际的文件托管商，而不是此存储库或 Cloudstream 3 应用程序的开发人员。

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
4. 输入仓库地址：`https://raw.githubusercontent.com/nxovaeng/vodext/builds/repo.json`
5. 安装插件

## [了解更多](docs/index.md)
  
[详细介绍](https://vod.zrocf.qzz.io)

## 许可证

[![GNU GPLv3 Image](https://www.gnu.org/graphics/gplv3-127x51.png)](http://www.gnu.org/licenses/gpl-3.0.en.html)

这些扩展是免费软件：您可以根据需要使用、学习、共享和修改它们。

它们根据自由软件基金会发布的 [GNU General Public License](https://www.gnu.org/licenses/gpl.html) 第 3 版或更高版本的条款分发。

## 致谢

- [CloudStream](https://github.com/recloudstream/cloudstream) - 提供优秀的视频播放框架
- [Kotlin](https://kotlinlang.org/) - 现代化的编程语言
- [Jsoup](https://jsoup.org/) - HTML 解析库
- [phisher](https://github.com/phisher98/cloudstream-extensions-phisher) - CloudStream插件扩展库
- [SaurabhKaperwan](https://github.com/SaurabhKaperwan/CSX) - NetflixMirrorProvider
