import { matchesWildcardDomain } from "../functions/src/index";
import * as assert from "assert";

describe("Domain Wildcard Matching Suite", () => {
  const rule = "*.youtube.com";

  it("blocks exact root domain youtube.com", () => {
    assert.strictEqual(matchesWildcardDomain("youtube.com", rule), true);
  });

  it("blocks subdomains m.youtube.com and www.youtube.com", () => {
    assert.strictEqual(matchesWildcardDomain("m.youtube.com", rule), true);
    assert.strictEqual(matchesWildcardDomain("www.youtube.com", rule), true);
    assert.strictEqual(matchesWildcardDomain("music.youtube.com", rule), true);
  });

  it("does NOT block notyoutube.com (CRITICAL SECURITY TEST)", () => {
    assert.strictEqual(matchesWildcardDomain("notyoutube.com", rule), false);
  });

  it("does NOT block other unrelated domains", () => {
    assert.strictEqual(matchesWildcardDomain("ilmkidunya.com", rule), false);
    assert.strictEqual(matchesWildcardDomain("google.com", rule), false);
    assert.strictEqual(matchesWildcardDomain("fakewwwyoutube.com", rule), false);
  });
});
