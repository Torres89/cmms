// Development placeholder. In the container, docker-entrypoint.sh overwrites
// this file at start with the deployment's API_URL and AGENT_URL. Empty values
// fall back to the ones baked in at build time (app.config.ts).
window.__ATLAS_ENV__ = { API_URL: '', AGENT_URL: '' };
