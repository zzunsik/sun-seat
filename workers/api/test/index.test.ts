import { env, exports } from "cloudflare:workers";
import { afterEach, describe, expect, it, vi } from "vitest";
import { type Env, GROUPS, RETRY_AFTER_SECONDS, handleRequest, resolve } from "../src/index";
import wranglerSource from "../wrangler.jsonc?raw";

/** wrangler.jsonc(주석 포함 JSON)를 읽는다. 주석은 한 줄 전체로만 쓴다는 가정. */
type RateLimitConfig = { name: string; namespace_id: string; simple: { limit: number; period: number } };
type WorkerConfig = { name: string; secrets?: { required?: string[] }; ratelimits: RateLimitConfig[] };
const wrangler = JSON.parse(wranglerSource.replace(/^\s*\/\/.*$/gm, "")) as WorkerConfig & {
  env: Record<string, Partial<WorkerConfig>>;
};
const kakaoLimit = wrangler.ratelimits.find((r) => r.name === "KAKAO_RATE_LIMITER")!.simple;

/** 테스트용 가짜 키. 실제 키가 아니다(vitest.config.ts와 같은 값). */
const KEY = "test-rest-key-not-real";
const BASE = "https://api.example";

/** 앱이 실제로 보내는 형태의 요청(공개 POI 서울역·강남역 근처 좌표)과 전달될 카카오 주소. */
const ALLOWED: Record<string, { request: string; upstream: string }> = {
  "local/search/keyword": {
    request: "/kakao/local/search/keyword?query=%EC%84%9C%EC%9A%B8%EC%97%AD&x=126.97&y=37.55&size=15",
    upstream: "https://dapi.kakao.com/v2/local/search/keyword.json",
  },
  "local/geo/coord2address": {
    request: "/kakao/local/geo/coord2address?x=126.97&y=37.55",
    upstream: "https://dapi.kakao.com/v2/local/geo/coord2address.json",
  },
  "routing/publictraffic": {
    request: "/kakao/routing/publictraffic?start_x=126.97&start_y=37.55&end_x=127.02&end_y=37.49",
    upstream: "https://dapi.kakao.com/v2/routing/publictraffic",
  },
  "navi/directions": {
    request: "/kakao/navi/directions?origin=126.97%2C37.55&destination=127.02%2C37.49",
    upstream: "https://apis-navi.kakaomobility.com/v1/directions",
  },
};
const req = (route: string) => ALLOWED[route]!.request;

interface Recorded {
  url: string;
  headers: Headers;
  method: string;
}

function fakeUpstream(respond: (url: string) => Response = () => Response.json({ documents: [] })) {
  const calls: Recorded[] = [];
  const fetcher = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = input.toString();
    calls.push({ url, headers: new Headers(init?.headers), method: init?.method ?? "GET" });
    return respond(url);
  }) as typeof fetch;
  return { calls, fetcher };
}

function testEnv(allow = true, key: string | undefined | null = KEY): { env: Env; keys: string[] } {
  const keys: string[] = [];
  const limiter = {
    limit: async ({ key: k }: { key: string }) => {
      keys.push(k);
      return { success: allow };
    },
  } as unknown as RateLimit;
  return { env: { KAKAO_REST_API_KEY: key ?? undefined, KAKAO_RATE_LIMITER: limiter }, keys };
}

function get(path: string, init: RequestInit = {}): Request {
  return new Request(BASE + path, { headers: { "cf-connecting-ip": "203.0.113.7" }, ...init });
}

describe("허용 목록", () => {
  it("경로 묶음은 kakao 하나, 앱이 쓰는 경로는 이 4개뿐이다", () => {
    expect(Object.keys(GROUPS)).toEqual(["kakao"]);
    expect(Object.keys(GROUPS.kakao!.routes).sort()).toEqual(Object.keys(ALLOWED).sort());
  });

  for (const [route, { request, upstream }] of Object.entries(ALLOWED)) {
    it(`/kakao/${route}는 ${upstream}로 전달하고 키를 서버에서 붙인다`, async () => {
      const { env } = testEnv();
      const up = fakeUpstream();
      const res = await handleRequest(get(request), env, up.fetcher);
      expect(res.status).toBe(200);
      expect(up.calls).toHaveLength(1);
      const call = up.calls[0]!;
      const target = new URL(call.url);
      expect(target.origin + target.pathname).toBe(upstream);
      expect(target.search).toBe(new URL(BASE + request).search);
      expect(call.method).toBe("GET");
      expect(call.headers.get("authorization")).toBe(`KakaoAK ${KEY}`);
    });
  }

  it("앱이 보낸 헤더는 카카오로 넘기지 않는다", async () => {
    const { env } = testEnv();
    const up = fakeUpstream();
    await handleRequest(
      get(req("navi/directions"), { headers: { authorization: "KakaoAK client-key", cookie: "a=b", "cf-connecting-ip": "203.0.113.7" } }),
      env,
      up.fetcher,
    );
    expect(up.calls[0]!.headers.get("authorization")).toBe(`KakaoAK ${KEY}`);
    expect(up.calls[0]!.headers.get("cookie")).toBeNull();
  });

  const notFound = [
    "/",
    "/kakao",
    "/kakao/",
    "/v2/local/search/keyword.json?query=a",
    "/v1/directions?origin=1,2&destination=3,4",
    "/naver/local/search/keyword?query=a",
    "/Kakao/navi/directions?origin=1,2&destination=3,4",
    "/__proto__/x",
    "/constructor/x",
  ];
  for (const path of notFound) {
    it(`모르는 묶음 ${path}는 404이고 외부 API·호출 제한을 건드리지 않는다`, async () => {
      const { env, keys } = testEnv();
      const up = fakeUpstream();
      const res = await handleRequest(get(path), env, up.fetcher);
      expect(res.status).toBe(404);
      expect(up.calls).toHaveLength(0);
      expect(keys).toHaveLength(0);
    });
  }

  const forbidden = [
    "/kakao/local/search/address?query=a",
    "/kakao/local/search/category?category_group_code=SW8",
    "/kakao/v2/local/search/keyword.json?query=a",
    "/kakao/navi/directions/?origin=1,2&destination=3,4",
    "/kakao/navi/waypoints?origin=1,2&destination=3,4",
    "/kakao/local/search/keyword?query=a&radius=100",
    "/kakao/local/search/keyword?query=a&query=b",
    "/kakao/local/search/keyword",
    "/kakao/local/geo/coord2address?x=1",
    "/kakao/navi/directions?origin=1,2&destination=3,4&waypoints=5,6",
    "/kakao/routing/publictraffic?start_x=1&start_y=2&end_x=3",
    `/kakao/local/search/keyword?query=${"a".repeat(201)}`,
    "/kakao/local%2Fsearch%2Fkeyword?query=a",
    "/kakao/toString?x=1",
    "/kakao/__proto__?x=1",
  ];
  for (const path of forbidden) {
    it(`허용 밖 요청 ${path.slice(0, 60)}는 403이고 외부 API·호출 제한을 건드리지 않는다`, async () => {
      const { env, keys } = testEnv();
      const up = fakeUpstream();
      const res = await handleRequest(get(path), env, up.fetcher);
      expect(res.status).toBe(403);
      expect(up.calls).toHaveLength(0);
      expect(keys).toHaveLength(0);
    });
  }

  it("앱이 보낸 쿼리 인코딩을 그대로 전달한다(공백 %20을 +로 바꾸지 않는다)", async () => {
    const { env } = testEnv();
    const up = fakeUpstream();
    await handleRequest(get("/kakao/local/search/keyword?query=%EC%84%9C%EC%9A%B8%EC%97%AD%20%EA%B4%91%EC%9E%A5&size=15"), env, up.fetcher);
    expect(new URL(up.calls[0]!.url).search).toBe("?query=%EC%84%9C%EC%9A%B8%EC%97%AD%20%EA%B4%91%EC%9E%A5&size=15");
  });

  it("resolve는 허용 경로의 외부 호스트만 고른다", () => {
    const r = resolve(new URL(BASE + req("navi/directions")));
    expect(r.kind === "ok" && r.target.host).toBe("apis-navi.kakaomobility.com");
  });
});

describe("메서드", () => {
  for (const method of ["POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"]) {
    it(`${method}는 405이고 외부 API를 부르지 않는다`, async () => {
      const { env } = testEnv();
      const up = fakeUpstream();
      const res = await handleRequest(get(req("navi/directions"), { method }), env, up.fetcher);
      expect(res.status).toBe(405);
      expect(res.headers.get("allow")).toBe("GET");
      expect(up.calls).toHaveLength(0);
    });
  }
});

describe("호출 제한", () => {
  it("상한을 넘으면 카카오를 부르지 않고 429", async () => {
    const { env, keys } = testEnv(false);
    const up = fakeUpstream();
    const res = await handleRequest(get(req("local/search/keyword")), env, up.fetcher);
    expect(res.status).toBe(429);
    expect(res.headers.get("retry-after")).toBe(String(kakaoLimit.period));
    expect(up.calls).toHaveLength(0);
    expect(keys).toEqual(["203.0.113.7"]);
  });

  it("CF-Connecting-IP를 키로 센다(X-Forwarded-For는 믿지 않는다)", async () => {
    const { env, keys } = testEnv();
    const up = fakeUpstream();
    await handleRequest(get(req("navi/directions"), { headers: { "cf-connecting-ip": "198.51.100.1", "x-forwarded-for": "192.0.2.9" } }), env, up.fetcher);
    expect(keys).toEqual(["198.51.100.1"]);
  });
});

describe("응답", () => {
  for (const status of [200, 400, 401, 403, 429, 500, 503]) {
    it(`카카오 상태 ${status}와 본문을 그대로 돌려준다`, async () => {
      const { env } = testEnv();
      const body = JSON.stringify({ code: -10, msg: `status ${status}` });
      const up = fakeUpstream(() => new Response(body, { status, headers: { "content-type": "application/json;charset=UTF-8", "set-cookie": "s=1" } }));
      const res = await handleRequest(get(req("routing/publictraffic")), env, up.fetcher);
      expect(res.status).toBe(status);
      expect(await res.text()).toBe(body);
      expect(res.headers.get("content-type")).toBe("application/json;charset=UTF-8");
      expect(res.headers.get("set-cookie")).toBeNull();
    });
  }

  it("키와 인증 헤더는 응답에 나오지 않는다(카카오가 되돌려 보내도 가린다)", async () => {
    const { env } = testEnv();
    const up = fakeUpstream(() => new Response(`{"echo":"KakaoAK ${KEY}"}`, { status: 401, headers: { authorization: `KakaoAK ${KEY}`, "x-echo": KEY } }));
    const res = await handleRequest(get(req("navi/directions")), env, up.fetcher);
    const text = await res.text();
    expect(text).not.toContain(KEY);
    for (const [, value] of res.headers) expect(value).not.toContain(KEY);
    expect(res.headers.get("authorization")).toBeNull();
  });

  it("Retry-After는 wrangler.jsonc의 호출 제한 기간과 같다", () => {
    expect(RETRY_AFTER_SECONDS).toBe(kakaoLimit.period);
  });

  it("리디렉션을 따르지 않고(키가 다른 호스트로 가지 않게) 시간 제한을 건다", async () => {
    const { env } = testEnv();
    const inits: (RequestInit | undefined)[] = [];
    const res = await handleRequest(get(req("navi/directions")), env, (async (_: RequestInfo | URL, init?: RequestInit) => {
      inits.push(init);
      return new Response(null, { status: 302, headers: { location: "https://elsewhere.example/" } });
    }) as typeof fetch);
    expect(inits[0]?.redirect).toBe("manual");
    expect(inits[0]?.signal).toBeInstanceOf(AbortSignal);
    expect(res.status).toBe(502);
    expect(res.headers.get("location")).toBeNull();
  });

  it("본문이 없는 상태(204)도 그대로 돌려준다", async () => {
    const { env } = testEnv();
    const res = await handleRequest(get(req("navi/directions")), env, fakeUpstream(() => new Response(null, { status: 204 })).fetcher);
    expect(res.status).toBe(204);
  });

  it("카카오에 닿지 못하면 502이고 오류 내용을 넘기지 않는다", async () => {
    const { env } = testEnv();
    const res = await handleRequest(get(req("navi/directions")), env, (async () => {
      throw new TypeError(`network ${KEY}`);
    }) as typeof fetch);
    expect(res.status).toBe(502);
    expect(await res.text()).not.toContain(KEY);
  });

  it("시크릿이 없으면 카카오를 부르지 않고 503", async () => {
    for (const key of [null, "", "  "]) {
      const { env } = testEnv(true, key);
      const up = fakeUpstream();
      const res = await handleRequest(get(req("navi/directions")), env, up.fetcher);
      expect(res.status).toBe(503);
      expect(up.calls).toHaveLength(0);
    }
  });

  it("로그에 키를 남기지 않는다", async () => {
    const spies = (["log", "info", "warn", "error", "debug"] as const).map((m) => vi.spyOn(console, m));
    try {
      const { env } = testEnv();
      await handleRequest(get(req("navi/directions")), env, fakeUpstream(() => new Response("x", { status: 500 })).fetcher);
      await handleRequest(get(req("navi/directions")), env, (async () => { throw new Error("down"); }) as typeof fetch);
      for (const spy of spies) for (const args of spy.mock.calls) expect(JSON.stringify(args)).not.toContain(KEY);
    } finally {
      spies.forEach((s) => s.mockRestore());
    }
  });
});

/** wrangler.jsonc의 실제 바인딩(호출 제한·시크릿)으로 Worker 전체를 돌린다. */
describe("배포 설정으로 실행", () => {
  afterEach(() => vi.restoreAllMocks());

  it("wrangler.jsonc에 kakao 호출 제한 바인딩이 있다", () => {
    expect(typeof env.KAKAO_RATE_LIMITER?.limit).toBe("function");
  });

  it("허용 경로는 키를 붙여 전달하고, 모르는 묶음은 404·허용 밖은 403·POST는 405", async () => {
    const upstream = vi.spyOn(globalThis, "fetch").mockImplementation(async () => Response.json({ documents: [] }));
    const ok = await exports.default.fetch(get(req("local/geo/coord2address")));
    expect(ok.status).toBe(200);
    expect(upstream).toHaveBeenCalledTimes(1);
    const [url, init] = upstream.mock.calls[0]!;
    expect(String(url)).toBe("https://dapi.kakao.com/v2/local/geo/coord2address.json?x=126.97&y=37.55");
    expect(new Headers(init?.headers).get("authorization")).toBe(`KakaoAK ${KEY}`);
    expect(await ok.text()).not.toContain(KEY);

    expect((await exports.default.fetch(get("/v2/local/search/address.json?query=a"))).status).toBe(404);
    expect((await exports.default.fetch(get("/kakao/local/search/address?query=a"))).status).toBe(403);
    expect((await exports.default.fetch(get(req("navi/directions"), { method: "POST" }))).status).toBe(405);
    expect(upstream).toHaveBeenCalledTimes(1);
  });

  it("한 IP가 상한(wrangler.jsonc 값)을 넘으면 429이고 그 요청은 카카오로 가지 않는다", async () => {
    const upstream = vi.spyOn(globalThis, "fetch").mockImplementation(async () => Response.json({ documents: [] }));
    const request = () => get(req("local/search/keyword"), { headers: { "cf-connecting-ip": "198.51.100.200" } });
    const statuses: number[] = [];
    // 로컬 카운터는 시계에 맞춘 창이라 창 경계를 넘을 수 있다. 상한의 두 배 남짓까지 보내 429가 나오는지 본다.
    for (let i = 0; i < kakaoLimit.limit * 2 + 10 && !statuses.includes(429); i++) {
      statuses.push((await exports.default.fetch(request())).status);
    }
    expect(statuses).toContain(429);
    expect(statuses.filter((s) => s === 200).length).toBeGreaterThanOrEqual(kakaoLimit.limit);
    expect(statuses.every((s) => s === 200 || s === 429)).toBe(true);
    // 429는 카카오를 부르지 않는다: 카카오 호출 수 = 200 응답 수.
    expect(upstream).toHaveBeenCalledTimes(statuses.filter((s) => s === 200).length);
    // 다른 IP는 영향을 받지 않는다.
    const other = await exports.default.fetch(get(req("local/search/keyword"), { headers: { "cf-connecting-ip": "198.51.100.201" } }));
    expect(other.status).toBe(200);
  });
});

/** 운영(최상위)과 개발(env.development) Worker는 같은 허용 목록·호출 제한을 쓴다(#191). */
describe("배포 환경", () => {
  const dev = wrangler.env.development!;

  it("운영은 api, 개발은 api-dev로 배포된다", () => {
    expect(wrangler.name).toBe("api");
    expect(dev.name).toBe("api-dev");
    expect(Object.keys(wrangler.env)).toEqual(["development"]);
  });

  it("개발 환경은 운영과 같은 호출 제한 바인딩·상한·기간을 쓰고 카운터(namespace_id)만 따로 쓴다", () => {
    // ratelimits는 환경으로 이어지지 않는다. 빠지면 개발 Worker에 호출 제한이 없게 된다.
    expect(dev.ratelimits?.map((r) => [r.name, r.simple])).toEqual(wrangler.ratelimits.map((r) => [r.name, r.simple]));
    const prodIds = wrangler.ratelimits.map((r) => r.namespace_id);
    for (const r of dev.ratelimits!) expect(prodIds).not.toContain(r.namespace_id);
  });

  it("개발 환경도 카카오 키 시크릿을 필수로 둔다", () => {
    expect(wrangler.secrets?.required).toEqual(["KAKAO_REST_API_KEY"]);
    expect(dev.secrets?.required).toEqual(wrangler.secrets?.required);
  });
});
