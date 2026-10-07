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
    const p = await b.newPage({ viewport: { width: 1440, height: 1100 } }),
      errors = [];
    p.on("pageerror", (e) => errors.push(e.message));
    const api = async (route) =>
      (
        await (
          await fetch(s.base + route, { headers: { "sap-token": s.token } })
        ).json()
      ).data;
    const user = await api("/api/auth/info"),
      rows = (await api("/api/oj/problems")).records,
      lc = rows.find((x) => x.slug === "leetcode-1");
    await p.addInitScript(
      ({ token, user }) => {
        localStorage.setItem("sap_token", token);
        localStorage.setItem("sap-token", token);
        localStorage.setItem("sap-user", JSON.stringify(user));
      },
      { token: s.token, user },
    );
    await p.goto(base + "/oj/" + lc.id);
    await p.locator(".cm-content").waitFor();
    await p.waitForFunction(
      () => !document.querySelector(".oj-template-loading"),
    );
    const detail = await api("/api/oj/problems/" + lc.id),
      pack = JSON.parse(
        fs.readFileSync(
          require("node:path").join(
            __dirname,
            "problem-packs/leetcode-1/pack.json",
          ),
        ),
      );
    const checked = [];
    for (const l of detail.languages) {
      await p.getByRole("button", { name: "编程语言", exact: true }).click();
      await p
        .getByRole("option")
        .nth(detail.languages.findIndex((v) => v.languageKey === l.languageKey))
        .click();
      await p.waitForFunction(
        () => !document.querySelector(".oj-template-loading"),
      );
      for (const mode of detail.modes) {
        await p
          .getByRole("group", { name: "做题模式", exact: true })
          .getByRole("button", {
            name: mode === "FUNCTION" ? "核心函数" : "完整程序",
            exact: true,
          })
          .click();
        await p.waitForFunction(
          () => !document.querySelector(".oj-template-loading"),
        );
        const rendered = (await p.locator(".cm-content").innerText())
            .trim()
            .replace(/\n+/g, "\n"),
          expected = pack.profiles[l.languageKey][
            mode === "FUNCTION" ? "starterFunction" : "starterStdio"
          ]
            .trim()
            .replace(/\n+/g, "\n");
        assert(expected.startsWith(rendered), l.languageKey + "/" + mode);
        assert(rendered.split("\n").length > 1);
        checked.push(l.languageKey + "/" + mode);
      }
    }
    await p.getByRole("button", { name: "编程语言", exact: true }).focus();
    await p.keyboard.press("ArrowDown");
    assert(await p.getByRole("listbox").isVisible());
    await p.keyboard.press("End");
    await p.keyboard.press("Enter");
    assert(
      (
        await p
          .getByRole("button", { name: "编程语言", exact: true })
          .innerText()
      ).includes("Rust"),
    );
    await p.waitForFunction(
      () => !document.querySelector(".oj-template-loading"),
    );
    for (const width of [390, 768, 1024, 1440, 1920]) {
      await p.setViewportSize({ width, height: 1100 });
      if (width <= 768)
        await p
          .getByRole("button", { name: "代码与控制台", exact: true })
          .click();
      assert(
        await p.evaluate(
          () => document.documentElement.scrollWidth <= innerWidth + 1,
        ),
      );
      await p.getByRole("button", { name: "编程语言", exact: true }).click();
      const box = await p.getByRole("listbox").boundingBox();
      assert(box.x >= 0 && box.x + box.width <= width + 1);
      await p.keyboard.press("Escape");
      await p.screenshot({
        path:
          "/Users/pllysun/Library/Caches/sap-oj-ui/formatted-workspace-" +
          width +
          ".png",
        fullPage: true,
      });
    }
    await p.setViewportSize({ width: 1440, height: 1100 });
    await p.goto(base + "/admin/oj");
    await p.locator(".oj-table-title").first().waitFor();
    await p.screenshot({
      path: "/Users/pllysun/Library/Caches/sap-oj-ui/admin-redesign.png",
      fullPage: true,
    });
    await p.getByRole("tab", { name: "统一管理", exact: true }).click();
    await p.screenshot({
      path: "/Users/pllysun/Library/Caches/sap-oj-ui/settings-redesign.png",
      fullPage: true,
    });
    await p.getByRole("tab", { name: "题目管理", exact: true }).click();
    await p.locator(".oj-table-title button").filter({ hasText: lc.title }).click();
    await p.getByRole("tab", { name: "语言模板与参考解", exact: true }).click();
    await p.waitForFunction(
      () => !document.querySelector(".oj-format-loading"),
      {},
      { timeout: 90000 },
    );
    await p.getByRole("group", { name: "模板模式", exact: true })
      .getByRole("button", { name: "核心函数", exact: true }).click();
    for (const label of ["C", "C++", "Java", "Python", "Rust"]) {
      await p
        .getByRole("group", { name: "模板语言", exact: true })
        .getByRole("button")
        .nth(["C", "C++", "Java", "Python", "Rust"].indexOf(label))
        .click();
      assert(
        (await p.locator(".el-dialog .cm-content").innerText()).split("\n")
          .length > 1,
      );
      await p
        .locator(".oj-template-files")
        .getByRole("button", { name: "参考解", exact: true })
        .click();
      assert(await p.locator(".el-dialog .cm-content").isVisible());
      await p
        .locator(".oj-template-files")
        .getByRole("button", { name: "调用驱动", exact: true })
        .click();
      assert(
        (await p.locator(".el-dialog .cm-content").innerText()).includes(
          "__USER_CODE__",
        ),
      );
      await p
        .locator(".oj-template-files")
        .getByRole("button", { name: "初始模板", exact: true })
        .click();
    }
    await p.screenshot({
      path: "/Users/pllysun/Library/Caches/sap-oj-ui/templates-redesign.png",
      fullPage: true,
    });
    const footer = await p
      .getByRole("button", { name: "保存为草稿", exact: true })
      .boundingBox();
    assert(footer.y + footer.height <= 1100);
    await p.getByRole("button", { name: "取消", exact: true }).click();
    assert.deepEqual(errors, []);
    const report = {
        passed: true,
        templates: checked,
        dropdownKeyboard: true,
        widths: [390, 768, 1024, 1440, 1920],
        adminEditorFiveLanguages: true,
        driverMarkers: true,
        footerVisible: true,
        runtimeErrors: 0,
      };
    fs.writeFileSync(require("node:path").join(__dirname, "ui-format-"+(process.env.SAP_OJ_PRODUCTION==="true"?"production":"candidate")+"-validation.json"), JSON.stringify(report,null,2)+"\n");
    console.log(JSON.stringify(report));
  } finally {
    await b.close();
  }
})().catch((e) => {
  console.error(e.stack);
  process.exitCode = 1;
});
