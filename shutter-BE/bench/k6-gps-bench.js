import http from 'k6/http';
import { WebSocket } from 'k6/websockets';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_HTTP = __ENV.BASE_HTTP || 'http://shutter-bench-app:8080';
const BASE_WS = __ENV.BASE_WS || 'ws://shutter-bench-app:8080/api/v1/ws/gps';
const MODE = __ENV.MODE || 'redis'; // 'redis' | 'db'
const VUS = parseInt(__ENV.VUS || '100', 10);
const INTERVAL_MS = parseInt(__ENV.INTERVAL_MS || '3000', 10);
const DURATION_SEC = parseInt(__ENV.DURATION_SEC || '60', 10);
const WARMUP_MS = parseInt(__ENV.WARMUP_MS || '5000', 10);

const ENDPOINT = MODE === 'db' ? '/api/v1/bench/gps-db' : '/api/v1/gps';

const httpLatency = new Trend('gps_http_duration', true);
const wsE2eLatency = new Trend('gps_ws_e2e_duration', true);
const httpErrors = new Counter('gps_http_errors');
const wsMessages = new Counter('gps_ws_messages');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    gps_load: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: `${DURATION_SEC + 30}s`,
    },
  },
  setupTimeout: '60s',
};

export function setup() {
  const wardTokens = [];
  const guardianTokens = [];

  // Batch fetch JWT tokens for seeded wards (1..VUS) and guardians (501..500+VUS)
  const batchSize = 50;
  for (let start = 1; start <= VUS; start += batchSize) {
    const end = Math.min(start + batchSize - 1, VUS);
    const reqs = [];
    for (let i = start; i <= end; i++) {
      reqs.push(['GET', `${BASE_HTTP}/api/v1/test/token/${i}`]);
      reqs.push(['GET', `${BASE_HTTP}/api/v1/test/token/${500 + i}`]);
    }
    const resList = http.batch(reqs);
    for (let idx = 0; idx < resList.length; idx += 2) {
      const wBody = JSON.parse(resList[idx].body);
      const gBody = JSON.parse(resList[idx + 1].body);
      wardTokens.push(wBody.data.access_token);
      guardianTokens.push(gBody.data.access_token);
    }
  }

  return { wardTokens, guardianTokens };
}

function buildStompConnect(token) {
  return `CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer ${token}\n\n\0`;
}

function buildStompSubscribe(wardId) {
  return `SUBSCRIBE\nid:sub-${wardId}\ndestination:/topic/location/${wardId}\n\n\0`;
}

export default function (data) {
  const vuIdx = __VU - 1;
  const wardId = __VU;
  const wardToken = data.wardTokens[vuIdx];
  const guardianToken = data.guardianTokens[vuIdx];

  const ws = new WebSocket(BASE_WS);
  const sentMap = new Map();
  let seq = 0;
  let intervalId = null;
  let startedAt = 0;

  const stopTest = () => {
    if (intervalId !== null) {
      clearInterval(intervalId);
      intervalId = null;
    }
    try {
      ws.close();
    } catch (_) {}
  };

  ws.onopen = () => {
    ws.send(buildStompConnect(guardianToken));
  };

  ws.onmessage = (event) => {
    const raw = typeof event.data === 'string' ? event.data : '';
    const frames = raw.split('\0');
    for (const frame of frames) {
      if (!frame || frame === '\n') continue;

      if (frame.startsWith('CONNECTED')) {
        ws.send(buildStompSubscribe(wardId));
        startedAt = Date.now();

        const sendOne = () => {
          seq += 1;
          const latVal = Number((37.0 + (wardId % 100) * 0.001 + seq * 0.0000001).toFixed(7));
          const key = latVal.toFixed(7);
          const payload = JSON.stringify({ lat: latVal, lng: 127.027619 });
          const params = {
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${wardToken}`,
            },
            tags: { mode: MODE },
          };

          const dispatchedAt = Date.now();
          const inMeasureWindow = dispatchedAt - startedAt >= WARMUP_MS;
          if (inMeasureWindow) {
            sentMap.set(key, dispatchedAt);
          }

          http.asyncRequest('POST', `${BASE_HTTP}${ENDPOINT}`, payload, params).then((res) => {
            const ok = check(res, { 'status is 200': (r) => r.status === 200 });
            if (!ok) {
              httpErrors.add(1);
              sentMap.delete(key);
            } else if (inMeasureWindow) {
              httpLatency.add(res.timings.duration);
            }
          });
        };

        // Jitter start across VUs so all VUs don't burst at exact same millisecond
        const jitter = Math.floor(Math.random() * INTERVAL_MS);
        setTimeout(() => {
          sendOne();
          intervalId = setInterval(sendOne, INTERVAL_MS);
        }, jitter);

        setTimeout(stopTest, DURATION_SEC * 1000);
      } else if (frame.startsWith('MESSAGE')) {
        const crlfIdx = frame.indexOf('\r\n\r\n');
        const lfIdx = frame.indexOf('\n\n');
        const bodyIdx = crlfIdx !== -1 ? crlfIdx + 4 : (lfIdx !== -1 ? lfIdx + 2 : -1);
        if (bodyIdx !== -1) {
          const bodyStr = frame.slice(bodyIdx).trim();
          if (bodyStr) {
            try {
              const msg = JSON.parse(bodyStr);
              if (msg.lat !== undefined && msg.lat !== null) {
                const key = Number(msg.lat).toFixed(7);
                const sentAt = sentMap.get(key);
                if (sentAt !== undefined) {
                  wsE2eLatency.add(Date.now() - sentAt);
                  wsMessages.add(1);
                  sentMap.delete(key);
                }
              }
            } catch (_) {}
          }
        }
      }
    }
  };

  ws.onerror = () => {
    stopTest();
  };
}
