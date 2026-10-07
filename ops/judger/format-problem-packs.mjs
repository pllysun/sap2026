// Format local packs with the same genuine engines used in the OJ UI.
// Does not upload or change a published problem's revision.
import fs from "node:fs";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const require = createRequire(path.join(root, "sap-user/package.json"));
const clang = await import(require.resolve("@wasm-fmt/clang-format"));
const ruff = await import(require.resolve("@wasm-fmt/ruff_fmt"));
const rust = await import(require.resolve("@scalar/rust-fmt"));
const config = JSON.stringify({
  BasedOnStyle: "Google",
  IndentWidth: 4,
  ColumnLimit: 100,
  SortIncludes: "Never",
  AllowShortFunctionsOnASingleLine: "None",
  AllowShortBlocksOnASingleLine: "Never",
});
export async function formatCode(code, language) {
  if (!code?.trim()) return code || "";
  const marker = "__USER_CODE__",
    comment =
      language === "python"
        ? "# SAP_OJ_USER_CODE_PLACEHOLDER"
        : "// SAP_OJ_USER_CODE_PLACEHOLDER",
    driver = code.includes(marker),
    input = driver ? code.replace(marker, comment) : code;
  let output =
    language === "rust"
      ? await rust.format(input, {
          edition: "2024",
          styleEdition: "2024",
          maxWidth: 100,
        })
      : language === "python"
        ? ruff.format(input, "main.py", { indent_width: 4, line_width: 100 })
        : clang.format(
            input,
            language === "java"
              ? "Main.java"
              : language === "c"
                ? "main.c"
                : "main.cpp",
            config,
          );
  if (driver) {
    output = output.replace(comment, marker);
    if ((output.match(/__USER_CODE__/g) || []).length !== 1)
      throw Error("Invalid driver marker");
  }
  return output;
}
function files(dir) {
  return fs
    .readdirSync(dir, { withFileTypes: true })
    .flatMap((e) =>
      e.isDirectory()
        ? files(path.join(dir, e.name))
        : e.name.endsWith(".json")
          ? [path.join(dir, e.name)]
          : [],
    );
}
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const report = {
    packs: 0,
    fields: 0,
    languages: ["c", "cpp", "java", "python", "rust"],
    failures: [],
  };
  for (const file of files(path.join(root, "ops/judger/problem-packs"))) {
    const p = JSON.parse(fs.readFileSync(file));
    if (!p.profiles || !p.references) continue;
    for (const [lang, profile] of Object.entries(p.profiles))
      for (const [obj, fields] of [
        [profile, ["starterStdio", "starterFunction", "functionDriver"]],
        [p.references[lang], ["STDIO", "FUNCTION"]],
      ])
        for (const field of fields)
          if (obj[field]) {
            try {
              obj[field] = await formatCode(obj[field], lang);
              report.fields++;
            } catch (e) {
              report.failures.push({
                slug: p.slug,
                language: lang,
                field,
                message: e.message,
              });
            }
          }
    if (!report.failures.length)
      fs.writeFileSync(file, JSON.stringify(p, null, 2) + "\n");
    report.packs++;
  }
  fs.writeFileSync(
    path.join(root, "ops/judger/template-format-validation.json"),
    JSON.stringify(report, null, 2) + "\n",
  );
  console.log(JSON.stringify(report));
  if (report.failures.length) process.exitCode = 1;
}
