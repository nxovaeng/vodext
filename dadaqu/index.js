const scraper = require('./scraper');
const config = require('../../config');

// --- Manifest ---
const allCatalogs = [];
const allIdPrefixes = [];

for (const site of Object.values(scraper.SITES)) {
    for (const cat of site.catalogs) {
        allCatalogs.push({
            type: cat.type,
            id: cat.id,
            name: cat.name
        });
    }
    allIdPrefixes.push(`${site.id}:`);
}

const manifest = {
    catalogs: allCatalogs,
    idPrefix: allIdPrefixes
};

// --- Logic Functions ---

async function getCatalog({ type, id, extra }) {
    let metas = [];
    const skip = extra && extra.skip ? parseInt(extra.skip, 10) : 0;

    if (extra && extra.search) {
        console.log('[Dadaqu] Search request:', extra.search, 'for catalog:', id);
        
        // 找到当前 catalog 对应的站点
        let targetSite = null;
        for (const site of Object.values(scraper.SITES)) {
            if (site.catalogs.find(c => c.id === id)) {
                targetSite = site;
                break;
            }
        }
        
        if (!targetSite) {
            console.log('[Dadaqu] No site found for catalog:', id);
            return { 
                metas: [], 
                cacheMaxAge: config.search.cacheMaxAge 
            };
        }
        
        console.log(`[Dadaqu] Searching ${targetSite.name}...`);
        
        try {
            const results = await scraper.searchDadaqu(targetSite, extra.search);
            // 使用搜索结果中的类型（从页面提取）
            metas = results.map(r => ({
                id: r.id,
                type: r.type || 'series',  // 使用搜索结果中的类型
                name: r.title,
                poster: r.poster,
                posterShape: 'regular'
            }));
        } catch (err) {
            console.error(`[Dadaqu] Search error for ${targetSite.name}:`, err.message);
        }
        
        return { 
            metas, 
            cacheMaxAge: config.search.cacheMaxAge 
        };

    } else {
        // 分类浏览：找到对应的站点和 catalog
        for (const site of Object.values(scraper.SITES)) {
            const catalog = site.catalogs.find(c => c.id === id);
            if (catalog) {
                try {
                    const recent = await scraper.getDadaquRecent(site, catalog.typeId, skip);
                    metas = recent.map(r => ({
                        id: r.id,
                        type: catalog.type,
                        name: r.title,
                        poster: r.poster,
                        posterShape: 'regular'
                    }));
                } catch (e) {
                    console.error(`[Dadaqu] Failed to get recent items for ${site.name}:`, e.message);
                }
                break;
            }
        }

        // Return hasMore based on whether we got a full page of results
        const hasMore = metas.length >= 30;
        return { 
            metas, 
            hasMore, 
            cacheMaxAge: config.catalog.cacheMaxAge 
        };
    }
}

async function getMeta({ id }) {
    const { siteId, dadaquId } = scraper.parseId(id);
    const siteConfig = scraper.getSiteConfig(siteId);
    
    if (siteConfig) {
        const meta = await scraper.getDadaquMeta(siteConfig, dadaquId);
        if (meta) {
            return { 
                meta, 
                cacheMaxAge: config.meta.cacheMaxAge 
            };
        }
    }
    return { meta: null };
}

async function getStreams({ type, id }) {
    const { siteId, dadaquId, episode } = scraper.parseId(id);
    const siteConfig = scraper.getSiteConfig(siteId);
    
    if (siteConfig) {
        try {
            const streams = await scraper.getDadaquStreams(siteConfig, dadaquId, episode);
            
            // 调试日志：输出解析出的视频地址
            console.log(`\n[Dadaqu] 🎬 Streams for ${siteConfig.name} - ID: ${dadaquId}, Episode: ${episode}`);
            streams.forEach((stream, index) => {
                console.log(`  [${index + 1}] ${stream.name || stream.description}`);
                console.log(`      URL: ${stream.url}`);
                if (stream.behaviorHints?.proxyHeaders) {
                    console.log(`      Proxy: YES (headers will be added)`);
                } else {
                    console.log(`      Proxy: NO (direct URL)`);
                }
            });
            console.log('');
            
            return { 
                streams, 
                cacheMaxAge: config.stream.cacheMaxAge 
            };
        } catch(e) {
            console.error(`[Dadaqu] Failed to get streams for ${siteConfig.name}:`, e.message);
            return { streams: [] };
        }
    }

    return { streams: [] };
}

module.exports = { ...manifest, getCatalog, getMeta, getStreams };
