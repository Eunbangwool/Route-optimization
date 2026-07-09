// 최단 경로 설계 — PWA 서비스워커
// 전략: 네트워크 우선(최신 배포 즉시 반영) + 오프라인 시 캐시 폴백.
// 같은 origin(GitHub Pages) 자원만 캐시. 외부 API(VWorld/OSRM/타일)는 통과.
const CACHE = 'routeopt-cache-v1';

self.addEventListener('install', function () {
    self.skipWaiting();
});

self.addEventListener('activate', function (e) {
    e.waitUntil((async function () {
        const keys = await caches.keys();
        await Promise.all(keys.filter(function (k) { return k !== CACHE; }).map(function (k) { return caches.delete(k); }));
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', function (e) {
    const req = e.request;
    if (req.method !== 'GET') return;
    let url;
    try { url = new URL(req.url); } catch (err) { return; }
    if (url.origin !== self.location.origin) return; // 외부 API/타일은 캐시하지 않음

    e.respondWith((async function () {
        try {
            const res = await fetch(req);
            if (res && res.status === 200 && res.type === 'basic') {
                const c = await caches.open(CACHE);
                c.put(req, res.clone());
            }
            return res;
        } catch (err) {
            const cached = await caches.match(req);
            if (cached) return cached;
            if (req.mode === 'navigate') {
                const idx = (await caches.match('./')) || (await caches.match('index.html'));
                if (idx) return idx;
            }
            throw err;
        }
    })());
});
