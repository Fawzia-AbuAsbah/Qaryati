import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import encoding from 'k6/encoding';
import { Counter, Rate, Trend } from 'k6/metrics';

// Client attestation is not proof of the deployed application's database identity.
// Independently verify the API instance, database and exclusive synthetic ownership first.
const PREFIX = 'QARYATI_PERF_';
const APPROVAL = 'verified-exclusive-synthetic-target';
const SETUP_SECONDS = 120;
const GRACE_SECONDS = 5;
const TOKEN_MARGIN_SECONDS = 30;
const MAX_BODY_CHARACTERS = 1000000;
const STATUSES = ['DRAFT', 'UNDER_REVIEW', 'VERIFIED', 'REJECTED'];
const PAGE_FIELDS = ['content', 'number', 'size', 'totalElements', 'totalPages',
  'numberOfElements', 'first', 'last', 'hasNext', 'hasPrevious', 'empty'];
const MIX = ['list', 'search', 'list', 'detail', 'list',
  'search', 'list', 'detail', 'list', 'search'];

function invalid(name) {
  // Never interpolate supplied configuration, tokens, claims, bodies or driver errors.
  throw new Error(`Missing or invalid ${PREFIX}${name}; see performance/k6/README.md`);
}

function required(name) {
  const value = __ENV[PREFIX + name];
  if (typeof value !== 'string' || value.length === 0) invalid(name);
  return value;
}

function integer(name, fallback, minimum, maximum) {
  const value = __ENV[PREFIX + name];
  if (value === undefined) return fallback;
  if (!/^(0|[1-9][0-9]*)$/.test(value)) invalid(name);
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number < minimum || number > maximum) invalid(name);
  return number;
}

function positiveId(value, name) {
  // String-only input avoids JSON numeric rounding before validation.
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) invalid(name);
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) invalid(name);
  return number;
}

function array(name, maximum) {
  let values;
  try { values = JSON.parse(required(name)); } catch (_) { invalid(name); }
  if (!Array.isArray(values) || values.length === 0 || values.length > maximum) invalid(name);
  return values;
}

function origin(value, name) {
  // Deliberately narrow origin parser: no userinfo, path, escaping, query or fragment.
  // No global URL/polyfill or network import is needed. IPv6 is limited to literal ::1.
  if (/[\s\\%?#@]/.test(value)) invalid(name);
  const match = /^(https?):\/\/(\[::1\]|[a-z0-9.-]+)(?::([1-9][0-9]{0,4}))?$/.exec(value);
  if (!match) invalid(name);
  const host = match[2];
  if (match[3] && Number(match[3]) > 65535) invalid(name);
  if (host !== '[::1]') {
    if (host.length > 253 || host.endsWith('.')) invalid(name);
    if (/^[0-9.]+$/.test(host)) {
      const parts = host.split('.');
      if (parts.length !== 4 || parts.some(part => !/^(0|[1-9][0-9]{0,2})$/.test(part)
          || Number(part) > 255)) invalid(name);
    } else if (host !== 'localhost' && (host.indexOf('.') < 0
        || host.split('.').some(part => !/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(part)))) {
      invalid(name);
    }
  }
  if (match[1] === 'http' && (!['localhost', '127.0.0.1', '[::1]'].includes(host)
      || __ENV[PREFIX + 'ALLOW_HTTP_LOOPBACK'] !== 'true')) invalid(name);
  return value;
}

function configuration() {
  if (required('APPROVAL') !== APPROVAL) invalid('APPROVAL');
  const base = origin(required('BASE_URL'), 'BASE_URL');
  if (origin(required('EXPECTED_BASE_URL'), 'EXPECTED_BASE_URL') !== base) invalid('EXPECTED_BASE_URL');
  const database = required('DATABASE_NAME');
  if (!/^[a-z][a-z0-9_]{0,62}$/.test(database)
      || ['qaryati_db', 'qaryati_governorate_dev'].includes(database)) invalid('DATABASE_NAME');
  const dataset = required('DATASET_ID');
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(dataset)) invalid('DATASET_ID');

  const ids = array('VILLAGE_IDS', 10).map(value => positiveId(value, 'VILLAGE_IDS'));
  if (new Set(ids).size !== ids.length) invalid('VILLAGE_IDS');
  const terms = array('SEARCH_TERMS', 5);
  terms.forEach(term => {
    if (typeof term !== 'string' || term.length === 0 || term.length > 180
        || term !== term.trim() || /[\u0000-\u001f\u007f]/.test(term)) invalid('SEARCH_TERMS');
    try { encodeURIComponent(term); } catch (_) { invalid('SEARCH_TERMS'); }
  });
  if (new Set(terms).size !== terms.length) invalid('SEARCH_TERMS');

  const token = required('TOKEN');
  if (token.length > 8192 || !/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token)) invalid('TOKEN');
  let claims;
  try { claims = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's')); }
  catch (_) { invalid('TOKEN'); }
  if (!claims || !Number.isSafeInteger(claims.exp) || claims.exp <= 0
      || (claims.nbf !== undefined && (!Number.isSafeInteger(claims.nbf)
        || claims.nbf > Math.floor(Date.now() / 1000)))) invalid('TOKEN');

  const config = {
    base, database, dataset, ids, terms, token, expiresAt: claims.exp,
    vus: integer('VUS', 1, 1, 20),
    seconds: integer('DURATION_SECONDS', 30, 10, 300),
    paceMs: integer('PACE_MS', 1000, 250, 5000),
    timeoutSeconds: integer('TIMEOUT_SECONDS', 5, 1, 10),
    page: integer('PAGE', 0, 0, 1000),
    size: integer('PAGE_SIZE', 20, 1, 100),
    governorateId: null, localAuthorityId: null, status: null,
  };
  ['GOVERNORATE_ID', 'LOCAL_AUTHORITY_ID'].forEach(name => {
    if (__ENV[PREFIX + name] !== undefined) {
      const field = name === 'GOVERNORATE_ID' ? 'governorateId' : 'localAuthorityId';
      config[field] = positiveId(__ENV[PREFIX + name], name);
    }
  });
  if (__ENV[PREFIX + 'STATUS'] !== undefined) {
    config.status = __ENV[PREFIX + 'STATUS'];
    if (!STATUSES.includes(config.status)) invalid('STATUS');
  }
  const budget = SETUP_SECONDS + config.seconds + GRACE_SECONDS
    + config.timeoutSeconds + TOKEN_MARGIN_SECONDS;
  if (config.expiresAt <= Math.ceil(Date.now() / 1000) + budget) invalid('TOKEN');
  if (__ENV.K6_HTTP_DEBUG && !['false', '0'].includes(__ENV.K6_HTTP_DEBUG)) invalid('TOKEN');
  return config;
}

const config = configuration();
const requests = new Counter('village_read_requests');
const errors = new Rate('village_read_errors');
const duration = new Trend('village_read_duration', true);

export const options = {
  scenarios: {
    villages_read: {
      executor: 'constant-vus', exec: 'default', vus: config.vus,
      duration: `${config.seconds}s`, gracefulStop: `${GRACE_SECONDS}s`,
    },
  },
  setupTimeout: `${SETUP_SECONDS}s`,
  maxRedirects: 0,
  insecureSkipTLSVerify: false,
  discardResponseBodies: false,
  throw: false,
  systemTags: ['method', 'name', 'status', 'scenario', 'expected_response'],
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)'],
  thresholds: {
    'http_req_failed{phase:load}': ['rate<0.01'],
    'checks{phase:load}': ['rate>0.99'],
    village_read_errors: ['rate<0.01'],
    village_read_requests: ['count>0'],
  },
};

function stop(message) {
  exec.test.abort(message);
  throw new Error(message); // Fail closed even if a future runtime returns from abort().
}

function milliseconds(value) {
  if (typeof value !== 'string' || value.length > 32) return NaN;
  const parts = value.match(/[0-9]+(?:\.[0-9]+)?(?:ms|s|m|h)/g);
  if (!parts || parts.join('') !== value) return NaN;
  return parts.reduce((total, part) => {
    const unit = part.match(/ms|s|m|h/)[0];
    return total + Number(part.slice(0, -unit.length)) * { ms: 1, s: 1000, m: 60000, h: 3600000 }[unit];
  }, 0);
}

function runtimePolicy() {
  // Inspect consolidated options, not only custom environment variables: CLI/config can override k6 options.
  const resolved = exec.test.options;
  if (!resolved || !resolved.scenarios || Object.keys(resolved.scenarios).length !== 1
      || !resolved.scenarios.villages_read) stop('Unsupported execution options; no traffic authorized.');
  const scenario = resolved.scenarios.villages_read;
  if (scenario.executor !== 'constant-vus' || scenario.vus !== config.vus
      || milliseconds(scenario.duration) !== config.seconds * 1000
      || milliseconds(scenario.gracefulStop) !== GRACE_SECONDS * 1000
      || (scenario.startTime !== undefined && milliseconds(scenario.startTime) !== 0)
      || (scenario.exec !== undefined && scenario.exec !== 'default')
      || (scenario.env && Object.keys(scenario.env).length !== 0)
      || resolved.insecureSkipTLSVerify === true || resolved.throw === true
      || resolved.discardResponseBodies === true || resolved.httpDebug) {
    stop('Execution overrides rejected; use only the documented bounded profile.');
  }
}

function safeInteger(value, minimum) {
  return Number.isSafeInteger(value) && value >= minimum;
}

function timestamp(value) {
  return typeof value === 'string'
    && /^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z$/.test(value);
}

function village(row) {
  if (!row || typeof row !== 'object' || Array.isArray(row)
      || !safeInteger(row.id, 1) || !safeInteger(row.governorateId, 1)
      || typeof row.nameAr !== 'string' || row.nameAr.trim().length === 0
      || typeof row.locationDescription !== 'string' || row.locationDescription.trim().length === 0
      || !STATUSES.includes(row.status) || !timestamp(row.createdAt) || !timestamp(row.updatedAt)) return false;
  if (row.localAuthorityId != null && !safeInteger(row.localAuthorityId, 1)) return false;
  if (['nameEn', 'nameOrigin'].some(key => row[key] != null && typeof row[key] !== 'string')) return false;
  if (['latitude', 'longitude', 'elevationM', 'areaKm2'].some(key => row[key] != null
      && (typeof row[key] !== 'number' || !Number.isFinite(row[key])))) return false;
  if ((row.latitude == null) !== (row.longitude == null)) return false;
  if (row.latitude != null && (Math.abs(row.latitude) > 90 || Math.abs(row.longitude) > 180)) return false;
  if (row.areaKm2 != null && row.areaKm2 <= 0) return false;
  return row.archivedAt == null || timestamp(row.archivedAt);
}

function page(body, requestedPage) {
  if (!body || typeof body !== 'object' || Array.isArray(body)
      || Object.keys(body).length !== PAGE_FIELDS.length
      || !PAGE_FIELDS.every(key => Object.prototype.hasOwnProperty.call(body, key))
      || !Array.isArray(body.content) || body.number !== requestedPage || body.size !== config.size
      || !safeInteger(body.totalElements, 0) || !safeInteger(body.totalPages, 0)
      || body.totalPages !== Math.ceil(body.totalElements / body.size)
      || body.numberOfElements !== body.content.length || body.content.length > body.size
      || body.totalElements < body.content.length
      || body.first !== (body.number === 0) || body.hasPrevious !== (body.number > 0)
      || body.hasNext !== (body.number + 1 < body.totalPages) || body.last !== !body.hasNext
      || body.empty !== (body.content.length === 0)) return false;
  return body.content.every((row, index) => village(row) && row.archivedAt == null
    && (index === 0 || body.content[index - 1].id < row.id)
    && (config.governorateId === null || row.governorateId === config.governorateId)
    && (config.localAuthorityId === null || row.localAuthorityId === config.localAuthorityId)
    && (config.status === null || row.status === config.status));
}

function query(term, requestedPage) {
  const values = [`page=${requestedPage}`, `size=${config.size}`, 'sortBy=id', 'sortDir=asc'];
  if (term !== null) values.push(`search=${encodeURIComponent(term)}`);
  if (config.governorateId !== null) values.push(`governorateId=${config.governorateId}`);
  if (config.localAuthorityId !== null) values.push(`localAuthorityId=${config.localAuthorityId}`);
  if (config.status !== null) values.push(`status=${config.status}`);
  return `/api/v1/villages?${values.join('&')}`;
}

function read(endpoint, value, requestedPage, phase) {
  if (config.expiresAt <= Math.ceil(Date.now() / 1000) + config.timeoutSeconds + TOKEN_MARGIN_SECONDS) {
    stop('Token lifetime budget exhausted; no further requests will be sent.');
  }
  let path;
  if (endpoint === 'detail' && config.ids.includes(value)) path = `/api/v1/villages/${value}`;
  else if (endpoint === 'list' && value === null) path = query(null, requestedPage);
  else if (endpoint === 'search' && config.terms.includes(value)) path = query(value, requestedPage);
  else stop('Unsupported Village request rejected.');
  const url = config.base + path;
  const tags = { endpoint, phase, name: endpoint === 'detail'
    ? 'GET /api/v1/villages/:id' : `GET /api/v1/villages [${endpoint}]` };
  let response;
  if (phase === 'load') requests.add(1, tags);
  try {
    response = http.get(url, {
      headers: { Authorization: `Bearer ${config.token}`, Accept: 'application/json' },
      redirects: 0, timeout: `${config.timeoutSeconds}s`, responseType: 'text',
      responseCallback: http.expectedStatuses(200), tags,
    });
  } catch (_) {
    if (phase === 'load') errors.add(true, tags);
    check(null, { 'GET completed without client exception': () => false }, tags);
    stop('Village GET could not complete; inspect the approved target locally.');
  }
  const headers = response.headers || {};
  const typeKey = Object.keys(headers).find(key => key.toLowerCase() === 'content-type');
  const jsonType = typeKey !== undefined && /^application\/json(?:\s*;|$)/i.test(headers[typeKey]);
  const timed = response.timings && Number.isFinite(response.timings.duration) && response.timings.duration >= 0;
  let body = null;
  if (response.status === 200 && jsonType && typeof response.body === 'string'
      && response.body.length <= MAX_BODY_CHARACTERS) {
    try { body = JSON.parse(response.body); } catch (_) { /* Recorded as a contract failure below. */ }
  }
  const contract = endpoint === 'detail' ? village(body) && body.id === value : page(body, requestedPage);
  const valid = check(response, {
    'HTTP status is 200': result => result.status === 200,
    'request stayed on the configured URL': result => result.url === url,
    'response is bounded JSON': () => jsonType && body !== null,
    'Village response contract is valid': () => contract,
    'HTTP timing metadata is available': () => Boolean(timed),
  }, tags);
  if (phase === 'load') {
    errors.add(!valid, tags);
    if (timed) duration.add(response.timings.duration, tags);
  }
  if (!valid) {
    if (response.status === 401 || response.status === 403) stop('Authentication or read-role authorization failed.');
    if (response.status === 404) stop('Known Village fixture or configured API route is missing.');
    stop('Unexpected Village status or response contract; workload aborted.');
  }
  return body;
}

export function setup() {
  runtimePolicy();
  // Only Village GETs: this also checks supplied-token authentication without calling login.
  const listing = read('list', null, 0, 'setup');
  if (listing.totalElements === 0 || listing.content.length === 0) stop('Synthetic listing fixtures are missing.');
  config.terms.forEach(term => {
    const matches = read('search', term, 0, 'setup');
    if (matches.totalElements === 0 || matches.content.length === 0) stop('Synthetic search fixtures are missing.');
  });
  config.ids.forEach(id => read('detail', id, 0, 'setup'));
  return { preflightComplete: true };
}

export default function (data) {
  runtimePolicy();
  if (!data || data.preflightComplete !== true) stop('Required Village preflight was not completed.');
  const iteration = exec.scenario.iterationInTest;
  if (!safeInteger(iteration, 0)) stop('Unsupported k6 execution metadata.');
  const endpoint = MIX[iteration % MIX.length];
  const rotation = Math.floor(iteration / MIX.length);
  const value = endpoint === 'list' ? null : endpoint === 'search'
    ? config.terms[rotation % config.terms.length] : config.ids[rotation % config.ids.length];
  read(endpoint, value, config.page, 'load');
  sleep(config.paceMs / 1000);
}
