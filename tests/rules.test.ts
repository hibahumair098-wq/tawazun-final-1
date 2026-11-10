name: Tawazun CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test-and-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js 20
        uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install dependencies
        run: |
          npm ci || npm install
          cd functions && (npm ci || npm install)

      - name: Typecheck Cloud Functions
        run: |
          cd functions && npm run build

      - name: Run Wildcard Domain Tests
        run: |
          npx jest tests/domain.test.ts

      - name: Verify No Phone/SMS OTP Collection
        run: |
          echo "Scanning for illegal SMS/phone number collection..."
          ! grep -rEi "(\\bphone\\b|\\bsms\\b|otp.*phone)" app/src/main/

      - name: Setup Java 17
        uses: actions/setup-java@v4
        with:
          distribution: "temurin"
          java-version: "17"

      - name: Run Android Unit Tests
        run: |
          chmod +x gradlew || true
          gradle :app:testDebugUnitTest
