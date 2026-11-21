// 테스트에서 cloudflare:workers의 env·exports 타입을 이 Worker에 맞춘다(wrangler.jsonc와 같게).
declare namespace Cloudflare {
  interface GlobalProps {
    mainModule: typeof import("../src/index");
  }
  interface Env {
    KAKAO_REST_API_KEY?: string;
    KAKAO_RATE_LIMITER: RateLimit;
  }
}

declare module "*?raw" {
  const content: string;
  export default content;
}
