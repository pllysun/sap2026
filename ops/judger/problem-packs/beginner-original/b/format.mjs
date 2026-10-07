import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { formatCode } from "../../../format-problem-packs.mjs";

const root = path.dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(await fs.readFile(path.join(root, "manifest.json"), "utf8"));
let fields = 0;
for (const entry of manifest) {
    const file = path.join(root, entry.path);
    const pack = JSON.parse(await fs.readFile(file, "utf8"));
    for (const language of ["c", "cpp", "java", "python", "rust"]) {
        pack.profiles[language].starterStdio = await formatCode(pack.profiles[language].starterStdio, language);
        pack.references[language].STDIO = await formatCode(pack.references[language].STDIO, language);
        fields += 2;
    }
    await fs.writeFile(file, JSON.stringify(pack, null, 2) + "\n");
}
console.log(JSON.stringify({ packs: manifest.length, formattedFields: fields, scope: "beginner-original/b" }));
