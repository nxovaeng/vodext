package nxovaeng

/**
 * CCTV 官方直播源提供者（现已合并至 ChinaIPTVProvider）
 * 保留此类以防外部依赖或历史收藏。
 */
open class CCTVProvider : ChinaIPTVProvider() {
    override var name = "CCTV Live"
}
