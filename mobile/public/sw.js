/*
 * Atlas CMMS service worker.
 *
 * Its job is to make the app installable and to open instantly (and at all
 * with no signal, e.g. in a plant basement). It does not cache API data: the
 * API is on another origin and is never intercepted, so nothing a technician
 * sees can be stale beyond the app shell itself.
 *
 *  - page and env.js: network first, cached copy when offline. A deploy is
 *    picked up on the next launch; env.js carries the API URL.
 *  - /_expo/static and /assets: content-hashed file names, so cache first.
 */
const CACHE = 'atlas-shell-v1';
const PRECACHE = [
  '/',
  '/env.js',
  '/manifest.webmanifest',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/apple-touch-icon.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll(PRECACHE))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)))
      )
      .then(() => self.clients.claim())
  );
});

const networkFirst = async (request, fallbackUrl) => {
  const cache = await caches.open(CACHE);
  try {
    const response = await fetch(request);
    if (response.ok) cache.put(fallbackUrl || request, response.clone());
    return response;
  } catch (error) {
    const cached = await cache.match(fallbackUrl || request);
    if (cached) return cached;
    throw error;
  }
};

const cacheFirst = async (request) => {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok) cache.put(request, response.clone());
  return response;
};

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  // API, agent, storage and anything else off-origin: straight to the network
  if (url.origin !== self.location.origin) return;

  if (request.mode === 'navigate') {
    // Single-page app: every path is the same shell
    event.respondWith(networkFirst(request, '/'));
  } else if (url.pathname === '/env.js') {
    event.respondWith(networkFirst(request));
  } else if (
    url.pathname.startsWith('/_expo/static/') ||
    url.pathname.startsWith('/assets/') ||
    url.pathname.startsWith('/icons/')
  ) {
    event.respondWith(cacheFirst(request));
  }
});
