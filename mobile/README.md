# Atlas CMMS Mobile (PWA)

The technician app: work orders, requests, assets, meters and the on-machine
assistant, sized for a phone. It is an installable web app (PWA), not a store
app. Technicians open the URL once and add it to their home screen, and it then
launches full screen like any other app.

It is still written in React Native and rendered with react-native-web
(`expo export -p web`), so screens use React Native primitives.

## Installing on a phone

The app must be served over **HTTPS**, or phones will not install it and the
camera and NFC will not work.

- **Android (Chrome):** open the URL, then tap **Install** in the banner at
  the top (or menu → *Install app*).
- **iPhone (Safari):** open the URL, tap **Share**, then **Add to Home
  Screen**. Safari has no install API, so the banner only shows these steps.

## Run / build

```shell
npm install
npm start          # dev server in the browser
npm run build      # static site in dist/
npm test
```

## Configuration

| Name        | Where                 | Description                               |
|-------------|-----------------------|-------------------------------------------|
| `API_URL`   | container env / build | Public API URL                            |
| `AGENT_URL` | container env / build | Public agent URL (on-machine assistant)   |

In Docker the image is built once and `docker-entrypoint.sh` writes these into
`/env.js` at container start, so one image serves any deployment. For `npm
start`, set them in `.env` and `app.config.ts` bakes them in.

The API and agent only accept browser requests from allowed origins, so the
deployment must also set **`PUBLIC_MOBILE_URL`** (see the root `.env.example`)
to this app's public URL.

## What changed from the native app

| Native feature           | In the PWA                                                                            |
|--------------------------|---------------------------------------------------------------------------------------|
| NFC tag scanning         | Web NFC: **Chrome on Android only**. iOS has no NFC API for web apps, so the NFC options are hidden there; use barcodes. Tag ids are normalised to the native app's format, so existing tags still resolve. |
| Barcode / QR scanning    | Camera + ZXing (bundled), QR and 1D codes, both platforms                            |
| Push notifications       | Not yet. Notifications arrive live over the websocket while the app is open. Web Push (iOS 16.4+ once installed, Android) needs API support for VAPID keys. |
| Photos, files, audio     | Browser pickers and MediaRecorder                                                     |
| Date pickers             | The phone's native picker via `<input type="datetime-local">`                         |
| Signature                | HTML canvas                                                                           |
| Offline                  | The app shell opens without signal (service worker); data still needs the network   |
| OTA updates (EAS)        | A deploy is picked up on the next launch                                              |

## Getting involved

You can contribute in different ways. Sending feedback on features, fixing certain bugs, implementing new features, etc.
Instructions on _how_ to contribute can be found in [CONTRIBUTING](CONTRIBUTING.md).
