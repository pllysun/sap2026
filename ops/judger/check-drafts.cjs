const fs = require("node:fs"),
  assert = require("node:assert/strict"),
  {
    chromium,
  } = require("/Users/pllysun/Library/Caches/ms-playwright-go/1.57.0/package");
const s = JSON.parse(
    fs.readFileSync("/Users/pllysun/Library/Caches/sap-oj-test-session.json"),
  ),
  base = process.env.SAP_BASE_URL || "http://127.0.0.1:18081";
(async () => {
  const b = await chromium.launch({
    executablePath:
      "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    headless: true,
  });
  try {
    const p = await b.newPage({ viewport: { width: 1440, height: 1000 } }),
      api = async (r) =>
        (
          await (
            await fetch(s.base + r, { headers: { "sap-token": s.token } })
          ).json()
        ).data,
      user = (await api("/api/auth/info")).user,
      rows = (await api("/api/oj/problems")).records,
      lc = rows.find((x) => x.slug === "leetcode-1"),
      d = await api("/api/oj/problems/" + lc.id);
    await p.addInitScript(
      ({ token, user, id, raw }) => {
        localStorage.setItem("sap_token", token);
        localStorage.setItem("sap-token", token);
        localStorage.setItem("sap-user", JSON.stringify(user));
        localStorage.setItem(`sap-oj-draft:${user.id}:${id}:cpp:FUNCTION`, raw);
      },
      { token: s.token, user, id: lc.id, raw: d.templates.cpp.FUNCTION },
    );
    await p.goto(base + "/oj/" + lc.id);
    await p.locator(".cm-content").waitFor();
    await p.waitForFunction(
      () => !document.querySelector(".oj-template-loading"),
    );
    assert((await p.locator(".cm-content").innerText()).split("\n").length > 3);
    await p.locator(".cm-content").click();
    await p.keyboard.press("Meta+A");
    const custom = "// 我的草稿\nclass Solution { public: int keep = 42; };";
    await p.keyboard.insertText(custom);
    await p.waitForTimeout(700);
    await p
      .getByRole("group", { name: "做题模式", exact: true })
      .getByRole("button", { name: "完整程序", exact: true })
      .click();
    await p.waitForFunction(
      () => !document.querySelector(".oj-template-loading"),
    );
    await p
      .getByRole("group", { name: "做题模式", exact: true })
      .getByRole("button", { name: "核心函数", exact: true })
      .click();
    await p.waitForFunction(
      () => !document.querySelector(".oj-template-loading"),
    );
    assert.equal((await p.locator(".cm-content").innerText()).trim(), custom);
    p.once("dialog", (d) => d.accept());
    await p.getByRole("button", { name: "恢复模板", exact: true }).click();
    await p.waitForFunction(
      () => !document.querySelector(".oj-template-loading"),
    );
    await p.waitForTimeout(700);
    const saved = await p.evaluate(
      ({ uid, id }) =>
        localStorage.getItem(`sap-oj-draft:${uid}:${id}:cpp:FUNCTION`),
      { uid: user.id, id: lc.id },
    );
    assert(saved.includes("\n"));
    assert(!saved.includes("keep"));
    await p.goto(base + "/admin/oj");
    await p.locator(".oj-table-title").first().waitFor();
    for (const width of [390, 768, 1024]) {
      await p.setViewportSize({ width, height: 1000 });
      assert(
        await p.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
        "Admin overflow " + width,
      );
    }
    const report = {
        passed: true,
        untouchedOldTemplatesFormatted: true,
        editedDraftPreserved: true,
        restoredTemplatePersisted: true,
        adminWidths: [390, 768, 1024],
      };
    fs.writeFileSync(require("node:path").join(__dirname, "draft-ui-"+(process.env.SAP_OJ_PRODUCTION==="true"?"production":"candidate")+"-validation.json"), JSON.stringify(report,null,2)+"\n");
    console.log(JSON.stringify(report));
  } finally {
    await b.close();
  }
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
