import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// Custom metrics so each scenario's timing shows up separately in the summary,
// instead of one blended average across very different endpoint types
const searchVariedTrend = new Trend('search_varied_duration');
const searchCachedTrend = new Trend('search_cached_duration');
const recommendationTrend = new Trend('recommendation_duration');

const BASE_URL = 'http://localhost:8082';

// A handful of real terms known to exist in the seeded/generated data
const SEARCH_TERMS = ['wireless', 'bluetooth', 'laptop', 'smartphone', 'headphones', 'monitor'];

// A real user ID with actual interaction history — adjust to a known-good ID
// from your data (e.g. one of the synthetic users, like 58) before running
const KNOWN_USER_ID = 58;

export const options = {
  scenarios: {
    // Scenario 1: varied search queries — simulates real, diverse traffic.
    // Every request is a cache MISS on first hit since queries differ.
    search_varied: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      exec: 'searchVaried',
    },
    // Scenario 2: same exact query repeated — after the first hit, every
    // subsequent request should be served from Redis. This is the scenario
    // that should show a dramatically lower p95 than scenario 1.
    search_cached: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      exec: 'searchCached',
      startTime: '35s', // run after scenario 1 finishes, to keep results easy to read separately
    },
    // Scenario 3: recommendation engine — the heaviest compute path in the app
    recommendations: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      exec: 'getRecommendations',
      startTime: '70s',
    },
  },
  thresholds: {
    // These are intentionally generous starting points — tighten them once
    // you see your actual baseline numbers from the first run
    'search_varied_duration': ['p(95)<1000'],
    'search_cached_duration': ['p(95)<100'],
    'recommendation_duration': ['p(95)<2000'],
  },
};

export function searchVaried() {
  const term = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];
  const res = http.get(`${BASE_URL}/api/search?q=${term}&page=0&size=20`);
  searchVariedTrend.add(res.timings.duration);
  check(res, { 'search_varied status is 200': (r) => r.status === 200 });
  sleep(0.2);
}

export function searchCached() {
  // Same exact query every time — this is what should hit Redis after the first call
  const res = http.get(`${BASE_URL}/api/search?q=wireless&page=0&size=20`);
  searchCachedTrend.add(res.timings.duration);
  check(res, { 'search_cached status is 200': (r) => r.status === 200 });
  sleep(0.2);
}

export function getRecommendations() {
  const res = http.get(`${BASE_URL}/api/recommendations/${KNOWN_USER_ID}?limit=10`);
  recommendationTrend.add(res.timings.duration);
  check(res, { 'recommendations status is 200': (r) => r.status === 200 });
  sleep(0.3);
}