/**
 * sun-seat 앱 API 게이트웨이(#189). Worker 이름 `api`.
 *
 * 앱은 외부 API 키 없이 이 서버를 부르고, 서버가 시크릿으로 외부 API를 부른다.
 * 경로 첫 마디가 경로 묶음(지금은 `kakao` 하나)을 고른다. 새 외부 API는 묶음을 더해 붙이고 앱의 기준 주소는 그대로 둔다.
 * - GET만 받는다(그 밖 405).
 * - 모르는 묶음은 404, 묶음 안에서 허용 목록에 없는 경로·파라미터는 403.
 * - 묶음마다 IP(`CF-Connecting-IP`)별 호출 제한을 두고, 넘으면 외부 API를 부르지 않고 429.
 * - 외부 API의 상태·본문을 그대로 돌려주되 키·인증 헤더는 응답·로그에 남기지 않는다.
 */

export interface Env {
  /** 카카오 REST API 키. `wrangler secret put KAKAO_REST_API_KEY`로만 넣는다. */
  KAKAO_REST_API_KEY?: string;
  /** `kakao` 묶음의 IP별 호출 제한(wrangler.jsonc `ratelimits`). */
  KAKAO_RATE_LIMITER: RateLimit;
}

export interface Route {
  /** 외부 API의 전체 주소(쿼리 제외). */
  readonly upstream: string;
  /** 허용하는 쿼리 파라미터. 이 밖의 파라미터가 있으면 403. */
  readonly params: readonly string[];
  /** 반드시 있어야 하는 파라미터. */
  readonly required: readonly string[];
}

export interface RouteGroup {
  /** 묶음 이름 아래의 경로(`/kakao/local/search/keyword`면 `local/search/keyword`) → 외부 API. */
  readonly routes: Readonly<Record<string, Route>>;
  readonly limiter: (env: Env) => RateLimit;
  /** 외부 API에 붙일 인증 헤더. 시크릿이 없으면 null(503). */
  readonly auth: (env: Env) => Record<string, string> | null;
  /** 응답 본문에서 가릴 비밀 값. */
  readonly secrets: (env: Env) => string[];
}

const DAPI = "https://dapi.kakao.com";
const NAVI = "https://apis-navi.kakaomobility.com";

const kakaoKey = (env: Env) => env.KAKAO_REST_API_KEY?.trim() ?? "";

/**
 * 경로 묶음. `kakao`의 경로·파라미터는 앱(`app/src/main/java/com/sunseat/app/data/place|route/Kakao*.kt`)이
 * 부르는 것뿐이다. 앱과 함께 바꾼다.
 */
export const GROUPS: Readonly<Record<string, RouteGroup>> = {
  kakao: {
    routes: {
      "local/search/keyword": { upstream: `${DAPI}/v2/local/search/keyword.json`, params: ["query", "x", "y", "size", "page"], required: ["query"] },
      "local/geo/coord2address": { upstream: `${DAPI}/v2/local/geo/coord2address.json`, params: ["x", "y"], required: ["x", "y"] },
      "routing/publictraffic": { upstream: `${DAPI}/v2/routing/publictraffic`, params: ["start_x", "start_y", "end_x", "end_y"], required: ["start_x", "start_y", "end_x", "end_y"] },
      "navi/directions": { upstream: `${NAVI}/v1/directions`, params: ["origin", "destination"], required: ["origin", "destination"] },
    },
    limiter: (env) => env.KAKAO_RATE_LIMITER,
    auth: (env) => (kakaoKey(env) ? { authorization: `KakaoAK ${kakaoKey(env)}` } : null),
    secrets: (env) => [kakaoKey(env)].filter((s) => s.length > 0),
  },
};

/** 파라미터 값 하나의 최대 길이. 앱이 보내는 값(검색어·좌표)보다 넉넉하다. */
const MAX_PARAM_LENGTH = 200;

/** 외부 API 응답을 기다리는 최대 시간(ms). 앱의 읽기 시간 제한(20초)보다 짧다. */
const UPSTREAM_TIMEOUT_MS = 10_000;

/** 429 응답의 Retry-After(초). wrangler.jsonc ratelimits의 period와 같게 둔다(테스트가 대조한다). */
export const RETRY_AFTER_SECONDS = 60;

/** 외부 API 응답에서 앱으로 넘기는 헤더. 그 밖(쿠키 등)은 버린다. */
const PASS_HEADERS = ["content-type"] as const;

const own = <T>(record: Readonly<Record<string, T>>, key: string): T | undefined =>
  Object.prototype.hasOwnProperty.call(record, key) ? record[key] : undefined;

function errorResponse(status: number, code: string, extra: HeadersInit = {}): Response {
  return new Response(JSON.stringify({ error: code }), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...extra },
  });
}

export type Resolved =
  | { kind: "not_found" }
  | { kind: "forbidden" }
  | { kind: "ok"; group: RouteGroup; target: URL };

/** 요청 주소를 경로 묶음·허용 목록에 맞춰 외부 API 주소로 바꾼다. */
export function resolve(url: URL): Resolved {
  const match = /^\/([a-z0-9-]+)\/(.+)$/.exec(url.pathname);
  const group = match ? own(GROUPS, match[1]!) : undefined;
  if (!match || !group) return { kind: "not_found" };
  const route = own(group.routes, match[2]!);
  if (!route) return { kind: "forbidden" };
  const seen = new Set<string>();
  for (const [name, value] of url.searchParams) {
    if (!route.params.includes(name) || seen.has(name) || value.length > MAX_PARAM_LENGTH) return { kind: "forbidden" };
    seen.add(name);
  }
  if (!route.required.every((name) => seen.has(name))) return { kind: "forbidden" };
  const target = new URL(route.upstream);
  // 검사한 쿼리를 앱이 보낸 인코딩 그대로 붙인다(다시 인코딩하면 공백 %20이 +로 바뀐다).
  target.search = url.search;
  return { kind: "ok", group, target };
}

export async function handleRequest(
  request: Request,
  env: Env,
  upstreamFetch: typeof fetch = fetch,
): Promise<Response> {
  if (request.method !== "GET") return errorResponse(405, "method_not_allowed", { allow: "GET" });

  const resolved = resolve(new URL(request.url));
  if (resolved.kind === "not_found") return errorResponse(404, "not_found");
  if (resolved.kind === "forbidden") return errorResponse(403, "forbidden");
  const { group, target } = resolved;

  // 허용된 요청만 센다. 403·404·405는 외부 API 쿼터를 쓰지 않으므로 세지 않는다.
  const ip = request.headers.get("cf-connecting-ip") ?? "unknown";
  const { success } = await group.limiter(env).limit({ key: ip });
  if (!success) return errorResponse(429, "rate_limited", { "retry-after": String(RETRY_AFTER_SECONDS) });

  const auth = group.auth(env);
  if (!auth) return errorResponse(503, "not_configured");

  let upstream: Response;
  try {
    // 앱이 보낸 헤더는 하나도 넘기지 않는다.
    upstream = await upstreamFetch(target.toString(), {
      method: "GET",
      headers: { ...auth, accept: "application/json" },
      // 인증 헤더가 다른 호스트로 따라가지 않게 리디렉션은 따르지 않는다.
      redirect: "manual",
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
  } catch {
    return errorResponse(502, "upstream_unavailable");
  }
  if (upstream.status >= 300 && upstream.status < 400) return errorResponse(502, "upstream_redirect");

  // 본문은 작은 JSON이다. 만일 외부 API가 키를 되돌려 보내도 앱에 넘기지 않도록 가린다.
  let body = await upstream.text();
  for (const secret of group.secrets(env)) {
    if (body.includes(secret)) body = body.split(secret).join("[redacted]");
  }
  const headers = new Headers({ "cache-control": "no-store" });
  for (const name of PASS_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) headers.set(name, value);
  }
  // 204·304 등 본문이 없어야 하는 상태는 본문 없이 돌려준다.
  const bodyless = upstream.status === 204 || upstream.status === 205 || upstream.status === 304;
  return new Response(bodyless ? null : body, { status: upstream.status, headers });
}

export default {
  fetch: (request, env) => handleRequest(request, env),
} satisfies ExportedHandler<Env>;
