import { defineConfig } from '@playwright/test';

/**
 * E2E-Tests laufen gegen eine laufende Instanz (lokal: ./mvnw quarkus:dev auf :8080).
 * Basis-URL via E2E_BASE_URL übersteuerbar.
 */
export default defineConfig({
  testDir: './e2e',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:8080',
  },
});
