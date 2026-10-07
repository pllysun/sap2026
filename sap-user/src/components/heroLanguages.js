// Display-only examples. Never execute these snippets or interpolate user data.
export const heroLanguages = [
  { id: 'c', label: 'C', file: 'hello.c', code: `#include <stdio.h>

int main(void) {
    const char *us = "软件协会";

    printf("Hello, %s!\\n", us);

    return 0;
}` },
  { id: 'cpp', label: 'C++', file: 'hello.cpp', code: `#include <iostream>
#include <string>

int main() {
    std::string us = "软件协会";

    std::cout << "Hello, " << us << "!\\n";
    return 0;
}` },
  { id: 'java', label: 'Java', file: 'Hello.java', code: `public class Hello {
    public static void main(String[] args) {
        String us = "软件协会";

        System.out.println("Hello, " + us + "!");
    }
}` },
  { id: 'python', label: 'Python', file: 'hello.py', code: `def greet(name: str) -> str:
    return f"Hello, {name}!"


if __name__ == "__main__":
    us = "软件协会"
    message = greet(us)

    print(message)` },
  { id: 'js', label: 'JavaScript', file: 'hello.js', code: `function greet(name) {
    return "Hello, " + name + "!";
}

const us = "软件协会";
const message = greet(us);

console.log(message);` },
  { id: 'ts', label: 'TypeScript', file: 'hello.ts', code: `type Community = { name: string };

const greet = (us: Community): string => {
    return "Hello, " + us.name + "!";
};

const us: Community = { name: "软件协会" };

console.log(greet(us));` },
  { id: 'rust', label: 'Rust', file: 'hello.rs', code: `fn greet(name: &str) -> String {
    format!("Hello, {}!", name)
}

fn main() {
    let us = "软件协会";
    println!("{}", greet(us));
}` },
  { id: 'kotlin', label: 'Kotlin', file: 'hello.kt', code: `fun greet(name: String): String {
    return "Hello, $name!"
}

fun main() {
    val us = "软件协会"
    println(greet(us))
}` },
  { id: 'shell', label: 'Shell', file: 'hello.sh', code: `#!/bin/sh

greet() {
    printf 'Hello, %s!\\n' "$1"
}

us="软件协会"
greet "$us"` },
  { id: 'lua', label: 'Lua', file: 'hello.lua', code: `local function greet(name)
    return "Hello, " .. name .. "!"
end

local us = "软件协会"
local message = greet(us)

print(message)` },
  { id: 'csharp', label: 'C#', file: 'Hello.cs', code: `using System;

class Hello {
    static void Main() {
        string us = "软件协会";

        Console.WriteLine("Hello, " + us + "!");
    }
}` },
]

const keywords = new Set('include int void const char return public class static string String def if type fn let fun val local function end using true false main Main'.split(' '))
export function codeTokens(line) {
  return (line.match(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\/.*$|--.*$|#.*$|[A-Za-z_][A-Za-z_0-9]*|\d+|[^A-Za-z_0-9"']+/g) || []).map(text => ({
    text,
    kind: /^\/\/|^--|^#(?!include)/.test(text) ? 'comment' : /^#include/.test(text) ? 'directive' : /^["']/.test(text) ? 'string' : keywords.has(text) ? 'keyword' : /^\d+$/.test(text) ? 'number' : 'plain',
  }))
}
