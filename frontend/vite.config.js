import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend (`./mvnw spring-boot:run`) listens on 8080; `npm run dev` proxies to it.
const backend = 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  build: {
    // Spring Boot serves target/classes/static. The directory sits outside Vite's root and is shared
    // with favicon.svg and the logos that Maven copies in at process-resources, so it must not be emptied.
    outDir: '../target/classes/static',
    emptyOutDir: false,
  },
  server: {
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/plex': { target: backend, changeOrigin: true },
      '/favicon.svg': { target: backend, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    css: false,
    globals: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/test/**', 'src/**/*.test.{js,jsx}', 'src/main.jsx'],
      // lcov feeds the CI changed-lines check (diff-cover).
      reporter: ['text-summary', 'json-summary', 'lcov'],
      reportOnFailure: true,
      thresholds: { lines: 90, branches: 85 },
    },
  },
});
