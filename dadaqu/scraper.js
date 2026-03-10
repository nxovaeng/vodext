const axios = require('axios');
const cheerio = require('cheerio');
const crypto = require('crypto');
const fs = require('fs');
const config = require('../../config');

// ============================================================================
// Site Configurations
// ============================================================================

const SITES = {
    dadaqu: {
        id: 'dadaqu',
        name: 'Dadaqu',
        mainUrl: 'https://www.dadaqu.pro',
        lang: 'zh',
        catalogs: [
            { typeId: 1, id: 'dadaqu_movies', name: 'Dadaqu 电影', type: 'movie' },
            { typeId: 2, id: 'dadaqu_series', name: 'Dadaqu 电视剧', type: 'series' },
            { typeId: 4, id: 'dadaqu_anime', name: 'Dadaqu 动漫', type: 'series' }
        ]
    }
};

let globalCookie = '';

const defaultHeaders = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.9',
    'Connection': 'keep-alive',
};

// Encryption logic from Dadaqu
const JS_KEY = 'jZ#8C*d!2$';

function encrypt(txt, key) {
    let chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    let nh = Math.floor(Math.random() * 64);
    let ch = chars.charAt(nh);
    let mdKey = crypto.createHash('md5').update(key + ch).digest('hex');
    mdKey = mdKey.substring(nh % 8, nh % 8 + (nh % 8 > 7 ? nh % 8 : nh % 8 + 17));
    txt = base64encode(txt);
    let tmp = '';
    let k = 0;
    for (let i = 0; i < txt.length; i++) {
        k = k == mdKey.length ? 0 : k;
        let charCode = txt.charCodeAt(i) ^ mdKey.charCodeAt(k++);
        tmp += String.fromCharCode(charCode);
    }
    return encodeURIComponent(ch + base64encode(tmp));
}

function base64encode(str) {
    return Buffer.from(str, 'utf8').toString('base64');
}

// Bot bypass fetch
async function fetchWithBypass(url) {
    try {
        const headers = { ...defaultHeaders };
        if (globalCookie) headers['Cookie'] = globalCookie;

        let res = await axios.get(url, { headers, validateStatus: () => true });
        
        if (res.headers['set-cookie']) {
            globalCookie = res.headers['set-cookie'].map(c => c.split(';')[0]).join('; ');
        }

        if (res.status === 200 && res.data.includes('robot.php')) {
            // Challenge 1
            const staticMatch1 = res.data.match(/var\s+staticchars\s*=\s*'([^']+)'/);
            const tokenMatch1 = res.data.match(/var\s+token\s*=\s*'([^']+)'/);
            
            // Challenge 2
            const tokenMatch2 = res.data.match(/var\s+token\s*=\s*encrypt\("([^"]+)"\);/);
            
            if (staticMatch1 && tokenMatch1 && res.data.includes('math.random')) {
                const staticchars = staticMatch1[1];
                const token = tokenMatch1[1];
                const p = encrypt(staticchars, token);
                const verificationUrl = `https://www.dadaqu.pro/static/js/robot.php?p=${p}&${token}=`;
                
                let verifyRes = await axios.get(verificationUrl, {
                    headers: { ...defaultHeaders, 'Cookie': globalCookie, 'Referer': url },
                    validateStatus: () => true
                });

                if (verifyRes.headers['set-cookie']) {
                    globalCookie = verifyRes.headers['set-cookie'].map(c => c.split(';')[0]).join('; ');
                }
                
                res = await axios.get(url, {
                    headers: { ...defaultHeaders, 'Cookie': globalCookie },
                    validateStatus: () => true
                });
            } else if (tokenMatch2) {
                const tokenRaw = tokenMatch2[1];
                const encrypt2 = (_str) => {
                    const staticchars = "PXhw7UT1B0a9kQDKZsjIASmOezxYG4CHo5Jyfg2b8FLpEvRr3WtVnlqMidu6cN";
                    let encodechars = "";
                    for (let i = 0; i < _str.length; i++) {
                        let num0 = staticchars.indexOf(_str[i]);
                        let code = num0 === -1 ? _str[i] : staticchars[(num0 + 3) % 62];
                        let num1 = Math.floor(Math.random() * 62);
                        let num2 = Math.floor(Math.random() * 62);
                        encodechars += staticchars[num1] + code + staticchars[num2];
                    }
                    return Buffer.from(encodechars).toString('base64');
                };
                
                const value = encrypt2(url);
                const token = encrypt2(tokenRaw);
                const postData = `value=${encodeURIComponent(value)}&token=${encodeURIComponent(token)}`;
                
                let verifyRes = await axios.post('https://www.dadaqu.pro/robot.php', postData, {
                    headers: { 
                        ...defaultHeaders, 
                        'Cookie': globalCookie, 
                        'Referer': url,
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    validateStatus: () => true
                });

                if (verifyRes.headers['set-cookie']) {
                    globalCookie = verifyRes.headers['set-cookie'].map(c => c.split(';')[0]).join('; ');
                }
                
                res = await axios.get(url, {
                    headers: { ...defaultHeaders, 'Cookie': globalCookie },
                    validateStatus: () => true
                });
            }
        }
        return res.data;
    } catch (e) {
        console.error('Fetch error:', e.message);
        return null;
    }
}

// URL Decryption logic
function md5(str) {
    return crypto.createHash('md5').update(str).digest('hex');
}

function decode1(cipherStr) {
    const key = md5('test');
    const decoded1 = Buffer.from(cipherStr, 'base64').toString('binary');
    let code = '';
    for (let i = 0; i < decoded1.length; i++) {
        const k = i % key.length;
        code += String.fromCharCode(decoded1.charCodeAt(i) ^ key.charCodeAt(k));
    }
    return Buffer.from(code, 'base64').toString('utf8');
}

function decodeFinalStream(input) {
    const out = decode1(input);
    const parts = out.split('/');
    if (parts.length < 3) return null;
    try {
        const arr1 = JSON.parse(Buffer.from(parts[0], 'base64').toString('utf8'));
        const arr2 = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf8'));
        const cipherUrl = Buffer.from(parts[2], 'base64').toString('utf8');
        
        let realUrl = '';
        for (let c of cipherUrl) {
            if (/^[a-zA-Z]$/.test(c)) {
                const idx = arr2.indexOf(c);
                if (idx !== -1) {
                    realUrl += arr1[idx];
                } else {
                    realUrl += c;
                }
            } else {
                realUrl += c;
            }
        }
        return realUrl;
    } catch(e) {
        return null;
    }
}

function decodeFinalStream2(input) {
    try {
        const decoded = Buffer.from(input, 'base64').toString('binary');
        const chars = 'PXhw7UT1B0a9kQDKZsjIASmOezxYG4CHo5Jyfg2b8FLpEvRr3WtVnlqMidu6cN';
        let res = '';
        for (let i = 1; i < decoded.length; i += 3) {
            const idx = chars.indexOf(decoded[i]);
            if (idx === -1) {
                res += decoded[i];
            } else {
                res += chars[(idx + 59) % 62];
            }
        }
        return res;
    } catch(e) {
        return null;
    }
}

/**
 * 构造代理 URL
 * @param {string} streamUrl - 原始视频 URL
 * @param {object} headers - 需要添加的请求头
 * @returns {string} 代理 URL
 */
function buildProxyUrl(streamUrl, headers = {}) {
    // 获取服务器地址
    let serverUrl = config.server.serverUrl;
    if (!serverUrl) {
        // 如果没有配置，尝试从 server.js 获取
        try {
            const server = require('../../server');
            serverUrl = server.serverUrl || 'http://127.0.0.1:11226';
        } catch (e) {
            serverUrl = 'http://127.0.0.1:11226';
        }
    }
    
    // 解析 URL
    const urlObj = new URL(streamUrl);
    const baseUrl = `${urlObj.protocol}//${urlObj.host}`;
    const pathAndQuery = streamUrl.substring(baseUrl.length);
    
    // 构造参数
    const params = new URLSearchParams();
    params.set('d', baseUrl);
    
    // 添加请求头
    Object.keys(headers).forEach(key => {
        params.append('h', `${key}:${encodeURIComponent(headers[key])}`);
    });
    
    // 构造完整的代理 URL
    return `${serverUrl}/proxy/${params.toString()}${pathAndQuery}`;
}

async function searchDadaqu(siteConfig, query) {
    if (!query) return [];
    console.log(`[${siteConfig.name}] Searching for query: "${query}"`);
    const searchUrl = `${siteConfig.mainUrl}/search/-------------.html?wd=${encodeURIComponent(query)}`;
    const html = await fetchWithBypass(searchUrl);
    if (!html) {
        console.error(`[${siteConfig.name}] fetchWithBypass returned null for search`);
        return [];
    }
    fs.writeFileSync('debug_search.html', html);
    
    const results = [];
    const $ = cheerio.load(html);
    $('.module-card-item').each((i, el) => {
        const titleEl = $(el).find('.module-card-item-title a');
        const title = titleEl.text().trim();
        const link = titleEl.attr('href');
        const img = $(el).find('img').attr('data-original');
        const idMatch = link ? link.match(/\/(vod)?detail\/(\d+)\.html/) : null;
        
        // 提取类型：从 .module-card-item-class 获取
        const typeClass = $(el).find('.module-card-item-class').text().trim();
        let type = 'series'; // 默认为 series
        if (typeClass === '电影') {
            type = 'movie';
        }
        // 剧集、动漫、综艺 都归类为 series
        
        if (idMatch) {
            results.push({
                id: `${siteConfig.id}:${idMatch[2]}`,
                siteId: siteConfig.id,
                dadaquId: idMatch[2],
                title,
                poster: img ? (img.startsWith('http') ? img : `${siteConfig.mainUrl}${img}`) : null,
                type  // 添加类型信息
            });
        }
    });
    console.log(`[${siteConfig.name}] Found ${results.length} search results`);
    return results;
}

async function getDadaquStreams(siteConfig, dadaquId, episode = 1) {
    const detailUrl = `${siteConfig.mainUrl}/detail/${dadaquId}.html`;
    const html = await fetchWithBypass(detailUrl);
    if (!html) return [];
    
    const $ = cheerio.load(html);
    const sourceNames = [];
    $('.module-tab-items-box .tab-item').each((i, el) => {
        sourceNames.push($(el).attr('data-dropdown-value') || $(el).text().replace(/\d+$/, '').trim());
    });

    const playLinks = [];
    $('.module-list').each((sourceIndex, listEl) => {
        const sourceName = sourceNames[sourceIndex] || `Source ${sourceIndex + 1}`;
        
        $(listEl).find('.module-play-list-link').each((i, el) => {
            const link = $(el).attr('href');
            const resolution = $(el).text().trim(); 
            const match = link ? link.match(/\/play\/(\d+)-(\d+)-(\d+)\.html/) : null;
            if (match && parseInt(match[3], 10) === parseInt(episode, 10)) {
                playLinks.push({ link, sourceName, resolution });
            }
        });
    });

    const streams = [];
    
    // 并行处理所有播放链接，每个设置超时
    const EXTRACT_TIMEOUT = 15000; // 15 秒超时
    
    const extractPromises = playLinks.map(async (item) => {
        try {
            const playUrl = `${siteConfig.mainUrl}${item.link}`;
            
            // 创建超时 Promise
            const timeoutPromise = new Promise((_, reject) => 
                setTimeout(() => reject(new Error('Extract timeout')), EXTRACT_TIMEOUT)
            );
            
            // 创建提取 Promise
            const extractPromise = (async () => {
                const playHtml = await fetchWithBypass(playUrl);
                if (!playHtml) return null;

                const playerMatch = playHtml.match(/var player_aaaa=({"flag".*?})<\/script>/);
                if (!playerMatch) return null;

                const playerData = JSON.parse(playerMatch[1]);
                if (!playerData.url) return null;

                const apiUrl = `${siteConfig.mainUrl}/ddplay/api.php`;
                const apiRes = await axios.post(apiUrl, `vid=${encodeURIComponent(playerData.url)}`, {
                    headers: {
                        ...defaultHeaders,
                        'Cookie': globalCookie,
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'Origin': siteConfig.mainUrl,
                        'Referer': `${siteConfig.mainUrl}/ddplay/index.php?vid=${playerData.url}`
                    },
                    validateStatus: () => true
                });

                if (apiRes.status === 200 && apiRes.data && apiRes.data.data) {
                    const streamData = apiRes.data.data;
                    let streamUrl = '';

                    if (streamData.urlmode === 1) {
                        streamUrl = decodeFinalStream(streamData.url);
                    } else if (streamData.urlmode === 2) {
                        streamUrl = decodeFinalStream2(streamData.url);
                    }

                    if (streamUrl && !streamUrl.includes('404.mp4')) {
                        // 检查是否为自营源 CDN
                        let isOwnSourceCDN = false;
                        let cdnType = '';
                        try {
                            const urlObject = new URL(streamUrl);
                            if (urlObject.hostname === 'mtv.exoz.cn') {
                                isOwnSourceCDN = true;
                                cdnType = 'mtv.exoz.cn (HLS/ArtPlayer)';
                                console.log(`[Dadaqu] 检测到 mtv.exoz.cn 自营源: ${item.sourceName}`);
                                
                                // 检查是否有 YYNB.m3u8 标识
                                if (streamUrl.includes('YYNB.m3u8')) {
                                    console.log(`[Dadaqu] 发现 YYNB.m3u8 标识`);
                                }
                            } else if (urlObject.hostname === 'yun.jiexicn.top') {
                                isOwnSourceCDN = true;
                                cdnType = 'yun.jiexicn.top (CDN)';
                                console.log(`[Dadaqu] 检测到 yun.jiexicn.top 自营源: ${item.sourceName}`);
                            }
                        } catch (e) {
                            // Ignore URL parsing errors
                        }
                        
                        // 判断是否为自营源
                        const isOwnSource = item.sourceName.includes('自营') || 
                                          item.sourceName.includes('高清无广告') || 
                                          isOwnSourceCDN;
                        const isCollectedSource = item.sourceName.includes('有广');
                        
                        // 准备请求头
                        const proxyHeaders = {
                            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                            'Referer': `${siteConfig.mainUrl}/ddplay/index.php?vid=${playerData.url}`,
                            'Origin': siteConfig.mainUrl,
                            'Accept': '*/*',
                            'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
                            'Accept-Encoding': 'gzip, deflate, br',
                            'Connection': 'keep-alive',
                            'Sec-Fetch-Dest': 'empty',
                            'Sec-Fetch-Mode': 'cors',
                            'Sec-Fetch-Site': 'cross-site'
                        };
                        
                        const streamObj = {
                            name: item.resolution || '标清',
                            description: `线路: ${item.sourceName}`
                        };
                        
                        // 为有时效性的 URL 添加说明
                        if (streamUrl.includes('token=') || streamUrl.includes('time=')) {
                            streamObj.description += ' ⏱️';
                        }
                        
                        // 根据配置决定使用代理 URL 还是 proxyHeaders
                        const proxyMode = config.proxy.mode;
                        const needsProxy = isOwnSource || isOwnSourceCDN || 
                                         (!isCollectedSource && proxyMode !== 'proxyHeaders');
                        
                        if (needsProxy && proxyMode === 'proxyUrl') {
                            // 使用代理 URL
                            streamObj.url = buildProxyUrl(streamUrl, proxyHeaders);
                            streamObj.behaviorHints = {
                                notWebReady: false  // 已经是代理 URL，不需要额外处理
                            };
                            
                            if (isOwnSourceCDN) {
                                streamObj.description += ` 🔒 (${cdnType} via proxy)`;
                            }
                        } else if (needsProxy && proxyMode === 'proxyHeaders') {
                            // 使用 proxyHeaders（依赖 Stremio 本地代理）
                            streamObj.url = streamUrl;
                            streamObj.behaviorHints = {
                                notWebReady: true,
                                proxyHeaders: {
                                    request: proxyHeaders
                                }
                            };
                            
                            if (isOwnSourceCDN) {
                                streamObj.description += ` 🔒 (${cdnType})`;
                            }
                        } else {
                            // 采集源或不需要代理
                            streamObj.url = streamUrl;
                            streamObj.behaviorHints = {
                                notWebReady: false
                            };
                        }
                        
                        return streamObj;
                    }
                }
                return null;
            })();
            
            // 竞速：提取 vs 超时
            const result = await Promise.race([extractPromise, timeoutPromise]);
            return result;
        } catch (e) {
            console.error('Failed to parse stream from link:', item.link, e.message);
            return null;
        }
    });
    
    // 等待所有提取完成（或超时）
    const results = await Promise.all(extractPromises);
    
    // 过滤掉 null 结果
    const validStreams = results.filter(s => s !== null);
    streams.push(...validStreams);
    
    return streams;
}

async function getDadaquMeta(siteConfig, dadaquId) {
    const detailUrl = `${siteConfig.mainUrl}/detail/${dadaquId}.html`;
    const html = await fetchWithBypass(detailUrl);
    if (!html) return null;

    const $ = cheerio.load(html);
    const title = $('h1').text().trim();
    let img = $('.module-item-pic img').attr('data-original');
    const poster = img ? (img.startsWith('http') ? img : `${siteConfig.mainUrl}${img}`) : null;
    const description = $('.module-info-introduction-content p').text().trim();
    
    // Determine type (if there are multiple episodes it's a series, otherwise movie)
    const episodes = [];
    const firstList = $('.module-list').first(); // take the first source list
    firstList.find('.module-play-list-link').each((i, el) => {
        const link = $(el).attr('href');
        const epTitle = $(el).text().trim();
        const match = link ? link.match(/\/play\/(\d+)-(\d+)-(\d+)\.html/) : null;
        if (match) {
            const epNum = parseInt(match[3], 10);
            if (!episodes.find(e => e.episode === epNum)) {
                episodes.push({
                    id: `${siteConfig.id}:${match[1]}:${epNum}`,
                    title: epTitle,
                    episode: epNum,
                    rawEpNum: epNum
                });
            }
        }
    });

    const isSeries = episodes.length > 1 || (episodes.length === 1 && episodes[0].title.includes('集'));

    // 智能分组：如果剧集超过 20 集，按季度分组（每季 20 集）
    // 季名使用范围格式：1-20, 21-40, 41-60 等
    // 集数保持原始编号不变
    if (isSeries && episodes.length > 20) {
        episodes.forEach(ep => {
            const seasonNum = Math.ceil(ep.rawEpNum / 20);
            const seasonStart = (seasonNum - 1) * 20 + 1;
            const seasonEnd = seasonNum * 20;
            ep.season = seasonNum;
            ep.episode = ep.rawEpNum; // 保持原始集数
            // 注意：Stremio 的 season 字段必须是数字，但我们可以在 title 中体现范围
            // 实际上 Stremio 会自动显示 "Season X"，我们无法直接改变这个显示
        });
    } else {
        // 少于等于 20 集，全部归为第 1 季
        episodes.forEach(ep => {
            ep.season = 1;
            ep.episode = ep.rawEpNum;
        });
    }

    // 移除临时字段
    episodes.forEach(ep => delete ep.rawEpNum);

    return {
        id: `${siteConfig.id}:${dadaquId}`,
        type: isSeries ? 'series' : 'movie',
        name: title,
        poster: poster,
        posterShape: 'regular',
        description: description,
        videos: isSeries ? episodes : undefined,
        background: poster
    };
}

async function getDadaquRecent(siteConfig, typeId, skip = 0) {
    // typeId 1 = movie, typeId 2 = series, 4 = anime
    // Use /show/ URL for full library sorted by popularity (hits)
    // Format: /show/typeId--hits------page---.html
    
    const page = Math.floor(skip / 30) + 1; // 每页约 30 项
    let listUrl;
    
    if (page === 1) {
        listUrl = `${siteConfig.mainUrl}/show/${typeId}--hits---------.html`;
    } else {
        listUrl = `${siteConfig.mainUrl}/show/${typeId}--hits------${page}---.html`;
    }
    
    console.log(`[${siteConfig.name} Scraper] Fetching recent (page ${page}): ${listUrl}`);
    
    const html = await fetchWithBypass(listUrl);
    if (!html) return [];
    
    const results = [];
    const $ = cheerio.load(html);
    
    // The /show/ page: .module-item is the <a> tag itself
    $('.module-item').each((i, el) => {
        const link = $(el).attr('href');
        const title = $(el).attr('title');
        let img = $(el).find('.module-item-pic img').attr('data-original');
        
        const idMatch = link ? link.match(/\/detail\/(\d+)\.html/) : null;
        const dadaquId = idMatch ? idMatch[1] : null;
        
        if (dadaquId && title && !results.find(r => r.dadaquId === dadaquId)) {
            results.push({
                id: `${siteConfig.id}:${dadaquId}`,
                siteId: siteConfig.id,
                dadaquId: dadaquId,
                title,
                poster: img ? (img.startsWith('http') ? img : `${siteConfig.mainUrl}${img}`) : null
            });
        }
    });
    
    console.log(`[${siteConfig.name} Scraper] Found ${results.length} items on page ${page}`);
    return results;
}

// Helper functions
function getSiteConfig(siteId) {
    return SITES[siteId] || null;
}

function parseId(stremioId) {
    // ID format: "siteid:dadaquId" or "siteid:dadaquId:episode"
    const parts = stremioId.split(':');
    const siteId = parts[0];
    const dadaquId = parts[1];
    const episode = parts[2] || 1;
    return { siteId, dadaquId, episode };
}

module.exports = {
    SITES,
    getSiteConfig,
    parseId,
    fetchWithBypass,
    searchDadaqu,
    getDadaquStreams,
    getDadaquMeta,
    getDadaquRecent
};
