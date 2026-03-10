/**
 * 豆瓣热搜 v2 - TVBox QuickJS Plugin
 *
 * @name        豆瓣热搜v2
 * @version     2.0.1
 * @author      takagen99
 * @update      2026-02-03
 * @description 展示豆瓣热门影视，提供评分和简介。长按可使用快搜功能搜索其他站点播放
 *
 * 功能说明：
 *   - 展示豆瓣热门、最新、经典、高分等分类影视
 *   - 点击进入详情页查看豆瓣评分、导演、演员等信息
 *   - 长按影片使用「快搜」功能跳转其他站点搜索播放
 *   - 本插件不提供播放源，仅作为影片发现工具
 *
 * 使用方式：
 *   1. 将此文件放入 TVBox 的 js 目录
 *   2. 在配置文件中添加此插件
 *   3. 浏览热门影视，长按使用快搜功能
 */

// 插件配置信息
var $cfg = {
    name: '豆瓣热搜v2',
    type: 3,  // 3=影视
    ext: '',
    searchable: 1,
    quickSearch: 1,
    filterable: 0
};

const DOUBAN_BASE = 'https://movie.douban.com';
const CATEGORY_API = `${DOUBAN_BASE}/j/search_subjects`;  // 分类标签搜索
const SUGGEST_API = `${DOUBAN_BASE}/j/subject_suggest`;   // 关键词搜索建议
const SUBJECT_API = `${DOUBAN_BASE}/j/subject_abstract`;  // 影片详情

const headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    'Referer': 'https://movie.douban.com/'
};

/**
 * 初始化插件
 * @param {Object} cfg - 配置信息
 */
async function init(cfg) {
    console.log('豆瓣热搜v2插件初始化');
}

/**
 * 首页分类列表
 * @param {boolean} filter - 是否返回筛选项
 * @returns {string} JSON格式的分类列表
 */
async function home(filter) {
    const classes = [
        { type_id: '热门', type_name: '🔥热门' },
        { type_id: '最新', type_name: '🆕最新' },
        { type_id: '经典', type_name: '🎬经典' },
        { type_id: '豆瓣高分', type_name: '⭐高分' },
        { type_id: '冷门佳片', type_name: '💎冷门' },
        { type_id: '华语', type_name: '🇨🇳华语' },
        { type_id: '欧美', type_name: '🇺🇸欧美' },
        { type_id: '韩国', type_name: '🇰🇷韩国' },
        { type_id: '日本', type_name: '🇯🇵日本' }
    ];

    return JSON.stringify({
        class: classes,
        filters: {}
    });
}

/**
 * 首页推荐内容
 * @returns {string} JSON格式的影片列表
 */
async function homeVod() {
    return category('热门', 1, false, {});
}

/**
 * 分类列表
 * @param {string} tid - 分类ID（标签名称）
 * @param {number} pg - 页码
 * @param {boolean} filter - 是否应用筛选
 * @param {Object} extend - 扩展筛选参数
 * @returns {string} JSON格式的影片列表
 */
async function category(tid, pg, filter, extend) {
    try {
        const page_start = (parseInt(pg) - 1) * 20;
        const url = `${CATEGORY_API}?type=movie&tag=${encodeURIComponent(tid)}&page_limit=20&page_start=${page_start}`;

        const r = await req(url, { headers });
        const data = JSON.parse(r.content);

        let videos = [];
        const subjects = data.subjects || [];

        for (let i = 0; i < subjects.length; i++) {
            const item = subjects[i];
            let title = item.title || '';
            const rate = item.rate || '0';
            const cover = item.cover || '';
            const id = item.id || '';

            // 清理片名：去掉年份后缀 (2025) 等
            // title = title.replace(/\s*\(\d{4}\)\s*$/, '').trim();

            let remarks = rate && rate !== '0' ? `⭐${rate}` : '';

            if (title) {
                videos.push({
                    vod_id: id,  // 使用豆瓣ID
                    vod_name: title,
                    vod_pic: cover,
                    vod_remarks: remarks
                });
            }
        }

        return JSON.stringify({
            page: parseInt(pg),
            pagecount: 10,
            limit: 20,
            total: videos.length,
            list: videos
        });

    } catch (e) {
        console.log('category出错: ' + e);
        return JSON.stringify({ page: 1, pagecount: 1, list: [] });
    }
}

/**
 * 详情页 - 展示豆瓣评分和简介
 * @param {string} id - 豆瓣影片ID
 * @returns {string} JSON格式的影片详情
 */
async function detail(id) {
    try {
        // 获取豆瓣影片详情
        const url = `${SUBJECT_API}?subject_id=${id}`;
        console.log('获取详情: ' + url);

        let title = '';
        let cover = '';
        let rate = '';
        let intro = '';
        let year = '';
        let actors = '';
        let directors = '';
        let duration = '';
        let region = '';
        let types = '';

        try {
            const r = await req(url, { headers });
            const data = JSON.parse(r.content);
            const subject = data.subject || {};

            // 解析标题：可能带年份后缀如 "年会不能停!(2023)"
            title = subject.title || `影片${id}`;
            // 清理标题中的年份后缀
            title = title.replace(/\s*\(\d{4}\)\s*$/, '').trim();
            
            cover = subject.cover || '';
            rate = subject.rate || '';
            
            // 优先使用 release_year 字段
            year = subject.release_year || '';
            
            // 解析简介：优先使用 short_comment 中的内容
            if (subject.short_comment && subject.short_comment.content) {
                intro = subject.short_comment.content;
            } else if (subject.short_info) {
                intro = subject.short_info;
            }
            
            // 导演和演员列表
            directors = subject.directors ? subject.directors.join(' / ') : '';
            actors = subject.actors ? subject.actors.join(' / ') : '';
            
            // 时长、地区、类型
            duration = subject.duration || '';
            region = subject.region || '';
            types = subject.types ? subject.types.join(' / ') : '';

        } catch (e) {
            console.log('获取详情失败: ' + e);
            title = `影片${id}`;
        }

        // 构建简介内容
        let content = '';
        if (rate) content += `⭐ 豆瓣评分: ${rate}\n\n`;
        if (year) content += `📅 年份: ${year}\n\n`;
        if (types) content += `🎭 类型: ${types}\n\n`;
        if (region) content += `🌍 地区: ${region}\n\n`;
        if (duration) content += `⏱️ 时长: ${duration}\n\n`;
        if (directors) content += `🎬 导演: ${directors}\n\n`;
        if (actors) content += `👥 演员: ${actors}\n\n`;
        if (intro) content += `📖 短评: ${intro}\n\n`;
        
        content += `\n💡 提示: 长按使用『快搜』搜索播放`;

        const vod = {
            vod_id: id,
            vod_name: title,
            vod_pic: cover,
            vod_remarks: rate ? `⭐${rate}` : '',
            vod_year: year,
            vod_area: region,
            vod_type: types,
            vod_actor: actors,
            vod_director: directors,
            vod_content: content,
            vod_play_from: '',
            vod_play_url: ''
        };

        return JSON.stringify({ list: [vod] });

    } catch (e) {
        console.log('detail出错: ' + e);
        return JSON.stringify({ list: [] });
    }
}

/**
 * 搜索功能 - 使用豆瓣搜索建议 API
 * @param {string} wd - 搜索关键词
 * @param {boolean} quick - 是否快速搜索
 * @returns {string} JSON格式的搜索结果
 */
async function search(wd, quick) {
    try {
        // 使用 subject_suggest API 进行关键词搜索
        const url = `${SUGGEST_API}?q=${encodeURIComponent(wd)}`;
        console.log('搜索URL: ' + url);
        
        const r = await req(url, { headers });
        const data = JSON.parse(r.content);

        let videos = [];
        
        // subject_suggest 返回数组格式
        // [{id, title, img, year, type, sub_title, episode, url}]
        for (const item of data) {
            // 只处理电影和电视剧
            if (item.type !== 'movie' && item.type !== 'tv') {
                continue;
            }
            
            let title = item.title || item.sub_title || '';
            const cover = item.img || '';
            const id = item.id || '';
            const year = item.year || '';
            const episode = item.episode || '';
            
            // 清理标题中的空格
            title = title.trim();
            
            // 构建备注：年份 + 集数
            let remarks = '';
            if (year) remarks = year;
            if (episode) remarks += (remarks ? ' ' : '') + episode;

            if (title && id) {
                videos.push({
                    vod_id: id,
                    vod_name: title,
                    vod_pic: cover,
                    vod_remarks: remarks
                });
            }
        }

        console.log(`搜索到 ${videos.length} 个结果`);
        return JSON.stringify({ page: 1, pagecount: 1, list: videos });

    } catch (e) {
        console.log('search出错: ' + e);
        return JSON.stringify({ page: 1, pagecount: 1, list: [] });
    }
}

/**
 * 播放解析
 * @param {string} flag - 播放源标识
 * @param {string} id - 播放ID
 * @param {Array} flags - 所有播放源标识列表
 * @returns {string} JSON格式的播放信息
 */
async function play(flag, id, flags) {
    // 豆瓣热搜不提供播放源，请使用快搜功能
    return JSON.stringify({ parse: 0, url: '' });
}

// 导出插件接口
export default {
    $cfg,
    init,
    home,
    homeVod,
    category,
    detail,
    search,
    play
};
