import { cloudflareTest } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest({
      wrangler: { configPath: "./wrangler.jsonc" },
      // 테스트용 가짜 키. 실제 키가 아니다.
      miniflare: { bindings: { KAKAO_REST_API_KEY: "test-rest-key-not-real" } },
    }),
  ],
});
